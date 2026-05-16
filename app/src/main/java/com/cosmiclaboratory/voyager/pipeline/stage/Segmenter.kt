package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.FusedMotionState
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.domain.util.DouglasPeuckerSimplifier
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.pipeline.PipelineConstants
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Segmenter @Inject constructor(
    private val database: VoyagerDatabase,
    private val movementSegmentDao: MovementSegmentDao,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val routeDao: RouteDao,
    private val stateStore: TimelineStateStore
) {
    // Mutex guards all mutable state below. PipelineConsumer calls processSample()
    // from its single coroutine, but TrackingRuntimeCoordinator calls closeCurrentSegment()
    // from arbitrary coroutines on stop/pause. The mutex prevents concurrent corruption.
    private val mutex = Mutex()

    private var currentSegmentType: SegmentType? = null
    private var currentSegmentDayKey: String? = null
    private var segmentStartSampleId: Long? = null
    private var segmentSamples = mutableListOf<RawSample>()
    private var activityVotes = mutableMapOf<String, Int>()
    private var lastSample: RawSample? = null

    // Debounce state — require TRANSITION_THRESHOLD consecutive samples of a new
    // activity type before closing the current segment. Prevents oscillation at
    // red lights, GPS boundary jitter, etc.
    private var pendingTransition: SegmentType? = null
    private var transitionCount = 0

    companion object {
        private const val TRANSITION_THRESHOLD = 5
        /** Flush in-progress segment every 5 minutes so the timeline stays up to date */
        private const val MAX_SEGMENT_DURATION_MS = 5 * 60 * 1000L
        /** Time-flush trigger for VISIT/DWELL: trim buffer every 15 min even if under sample cap */
        private const val VISIT_TRIM_INTERVAL_MS = 15 * 60 * 1000L
        /** Hard cap on in-memory samples to prevent OOM if flush fails repeatedly */
        private const val MAX_SEGMENT_SAMPLES = 500
        /** Implied speed above this is treated as FLIGHT — faster than any commercial flight */
        private const val FLIGHT_SPEED_THRESHOLD_MPS = 200.0
        private const val DISPLACEMENT_TRANSIT_THRESHOLD_M = PipelineConstants.DISPLACEMENT_TRANSIT_THRESHOLD_M
        private const val DISPLACEMENT_SPEED_THRESHOLD_MPS = PipelineConstants.DISPLACEMENT_SPEED_THRESHOLD_MPS
        private const val DISPLACEMENT_MAX_ACCURACY_M = PipelineConstants.DISPLACEMENT_MAX_ACCURACY_M
    }

    suspend fun processSample(
        sample: RawSample,
        motionState: FusedMotionState,
        dayKey: String
    ) = mutex.withLock {
        // Displacement-based override: detect movement when AR misses a transition.
        // Only trust displacement when both samples have reasonable GPS accuracy.
        val prev = lastSample
        lastSample = sample
        val displacementOverride: SegmentType? = if (prev != null) {
            val prevAcc = prev.accuracyM ?: DISPLACEMENT_MAX_ACCURACY_M
            val currAcc = sample.accuracyM ?: DISPLACEMENT_MAX_ACCURACY_M
            if (prevAcc <= DISPLACEMENT_MAX_ACCURACY_M && currAcc <= DISPLACEMENT_MAX_ACCURACY_M) {
                val displacement = LocationUtils.calculateDistance(
                    prev.lat, prev.lng, sample.lat, sample.lng
                )
                val timeDeltaMs = sample.capturedAt - prev.capturedAt
                if (displacement > DISPLACEMENT_TRANSIT_THRESHOLD_M && timeDeltaMs > 0) {
                    val impliedSpeed = displacement / (timeDeltaMs / 1000.0)
                    if (impliedSpeed > DISPLACEMENT_SPEED_THRESHOLD_MPS) {
                        when {
                            impliedSpeed >= FLIGHT_SPEED_THRESHOLD_MPS -> SegmentType.FLIGHT
                            impliedSpeed >= 7.5 -> SegmentType.DRIVE
                            impliedSpeed >= 3.7 -> SegmentType.CYCLE
                            else -> SegmentType.WALK
                        }
                    } else null
                } else null
            } else null
        } else null

        val newSegmentType = displacementOverride ?: mapToSegmentType(motionState.activityType)

        // Periodic flush: close and reopen segment if it's been running too long
        // or accumulated too many samples (OOM safety).
        // VISIT segments are NOT time-flushed — they stay open until departure to
        // prevent fragmentation. Only the OOM guard applies to visits.
        if (currentSegmentType != null && segmentSamples.isNotEmpty()) {
            val segmentAge = sample.capturedAt - segmentSamples.first().capturedAt
            val isVisit = currentSegmentType == SegmentType.VISIT || currentSegmentType == SegmentType.DWELL
            // VISIT/DWELL: trim buffer when EITHER sample-count OR time threshold reached.
            // Without the time trigger, an overnight 8h stay at 1 sample / 30s can keep
            // ~960 samples in memory; trim early to bound at ~50.
            val visitNeedsTrim = isVisit && (segmentSamples.size >= MAX_SEGMENT_SAMPLES || segmentAge >= VISIT_TRIM_INTERVAL_MS)
            if (visitNeedsTrim) {
                val first = segmentSamples.first()
                val recent = segmentSamples.takeLast(50)
                segmentSamples.clear()
                segmentSamples.add(first)
                segmentSamples.addAll(recent)
            } else if (!isVisit && segmentAge >= MAX_SEGMENT_DURATION_MS && segmentSamples.size >= 2) {
                closeInternal(currentSegmentDayKey ?: dayKey, bridgeToMs = sample.capturedAt, isPeriodicFlush = true)
            } else if (!isVisit && segmentSamples.size >= MAX_SEGMENT_SAMPLES) {
                closeInternal(currentSegmentDayKey ?: dayKey, bridgeToMs = sample.capturedAt, isPeriodicFlush = true)
            }
        }

        // Day boundary: if the sample's dayKey differs from the current segment's dayKey,
        // close the segment under its original day and let a new one open on the new day.
        if (currentSegmentType != null && currentSegmentDayKey != null && currentSegmentDayKey != dayKey) {
            closeInternal(currentSegmentDayKey!!, bridgeToMs = sample.capturedAt)
        }

        if (currentSegmentType != null && currentSegmentType != newSegmentType) {
            // Motion state changed — debounce before transitioning
            if (pendingTransition == newSegmentType) {
                transitionCount++
                if (transitionCount >= TRANSITION_THRESHOLD) {
                    closeInternal(dayKey, bridgeToMs = sample.capturedAt)
                    pendingTransition = null
                    transitionCount = 0
                } else {
                    // Still accumulating into current segment while debouncing
                    segmentSamples.add(sample)
                    activityVotes[motionState.activityType.name] =
                        (activityVotes[motionState.activityType.name] ?: 0) + 1
                    return@withLock
                }
            } else {
                pendingTransition = newSegmentType
                transitionCount = 1
                // Keep accumulating into current segment
                segmentSamples.add(sample)
                activityVotes[motionState.activityType.name] =
                    (activityVotes[motionState.activityType.name] ?: 0) + 1
                return@withLock
            }
        } else {
            // Same segment type — reset pending transition
            pendingTransition = null
            transitionCount = 0
        }

        if (currentSegmentType == null || currentSegmentType != newSegmentType) {
            // Open new segment
            currentSegmentType = newSegmentType
            currentSegmentDayKey = dayKey
            segmentStartSampleId = sample.sampleId
            segmentSamples.clear()
            activityVotes.clear()
        }

        segmentSamples.add(sample)
        activityVotes[motionState.activityType.name] =
            (activityVotes[motionState.activityType.name] ?: 0) + 1
    }

    /** Public entry point — acquires mutex. Called by TrackingRuntimeCoordinator on stop/pause. */
    suspend fun closeCurrentSegment(dayKey: String): Long? = mutex.withLock {
        closeInternal(dayKey)
    }

    /**
     * Internal — must be called while mutex is already held.
     * @param bridgeToMs When non-null, extends this segment's endAt to the next sample's
     *   capturedAt so consecutive segments are contiguous in the timeline. Null when
     *   called from closeCurrentSegment() (session stop/pause) where there's no next sample.
     */
    private suspend fun closeInternal(dayKey: String, bridgeToMs: Long? = null, isPeriodicFlush: Boolean = false): Long? {
        var type = currentSegmentType ?: return null
        if (segmentSamples.isEmpty()) return null

        // Dominant-mode voting: during periodic flushes (not activity transitions),
        // if one activity dominates (>65% of votes), use it as segment type.
        // This prevents a 20-minute bike ride from being labelled by brief oscillations.
        // Skip during transitions — those accumulate mixed votes from two different activities.
        if (isPeriodicFlush) {
            val totalVotes = activityVotes.values.sum()
            if (totalVotes >= 5) {
                val dominant = activityVotes.maxByOrNull { it.value }
                if (dominant != null && dominant.value > totalVotes * 0.65) {
                    val votedActivity = try {
                        ActivityType.valueOf(dominant.key)
                    } catch (_: Exception) { null }
                    if (votedActivity != null) {
                        type = mapToSegmentType(votedActivity)
                    }
                }
            }
        }

        val firstSample = segmentSamples.first()
        val lastSample = segmentSamples.last()

        // Calculate distance with GPS jitter filtering (movement segments only).
        // DWELL/VISIT segments are stationary — GPS jitter accumulates 4-12km of
        // fake distance over hours. Report 0 for stationary segments.
        val isStationary = type == SegmentType.VISIT || type == SegmentType.DWELL
        var totalDistance = 0.0
        if (!isStationary) {
            for (i in 1 until segmentSamples.size) {
                val prev = segmentSamples[i - 1]
                val curr = segmentSamples[i]
                val dist = LocationUtils.calculateDistance(
                    prev.lat, prev.lng, curr.lat, curr.lng
                )
                val noiseFloor = ((prev.accuracyM ?: 10f) + (curr.accuracyM ?: 10f)) / 2.0
                if (dist > noiseFloor) {
                    totalDistance += dist
                }
            }
        }

        // Bridge endAt to the next sample's timestamp for timeline continuity.
        // Route duration stays based on actual sample timestamps for accuracy.
        // maxOf guard prevents negative-duration segments from out-of-order FLP samples.
        val segmentEndAt = maxOf(bridgeToMs ?: lastSample.capturedAt, firstSample.capturedAt)

        val segment = MovementSegmentEntity(
            segmentType = type.name,
            startAt = firstSample.capturedAt,
            endAt = segmentEndAt,
            startSampleId = firstSample.sampleId,
            endSampleId = lastSample.sampleId,
            distanceM = totalDistance,
            confidence = 0.7f,
            dayKey = dayKey
        )

        val speeds = segmentSamples.mapNotNull { it.speedMps }
        val evidence = SegmentEvidenceEntity(
            segmentId = 0, // placeholder — replaced after insert
            avgSpeedMps = if (speeds.isNotEmpty()) speeds.average().toFloat() else null,
            maxSpeedMps = speeds.maxOrNull(),
            sampleCount = segmentSamples.size,
            activityVotesJson = kotlinx.serialization.json.Json.encodeToString(
                MapSerializer(String.serializer(), Int.serializer()),
                activityVotes
            )
        )

        // All DB ops in one atomic transaction
        val segmentId = database.withTransaction {
            val segId = movementSegmentDao.insert(segment)
            segmentEvidenceDao.upsert(evidence.copy(segmentId = segId))

            if (type != SegmentType.VISIT && type != SegmentType.DWELL && segmentSamples.size >= 2) {
                val points = segmentSamples.map { it.lat to it.lng }
                val encodedPolyline = PolylineEncoder.encode(points)
                val simplifiedPoints = DouglasPeuckerSimplifier.simplify(points, epsilonM = 5.0)
                val simplifiedPolyline = if (simplifiedPoints.size < points.size) {
                    PolylineEncoder.encode(simplifiedPoints)
                } else null
                val durationMs = lastSample.capturedAt - firstSample.capturedAt
                val route = RouteEntity(
                    segmentId = segId,
                    encodedPolyline = encodedPolyline,
                    simplifiedPolyline = simplifiedPolyline,
                    totalDistanceM = totalDistance,
                    totalDurationMs = durationMs,
                    avgSpeedMps = evidence.avgSpeedMps ?: 0f,
                    maxSpeedMps = evidence.maxSpeedMps ?: 0f,
                    transportMode = type.name,
                    sampleCount = segmentSamples.size
                )
                val routeId = routeDao.insert(route)
                movementSegmentDao.update(segment.copy(segmentId = segId, routeId = routeId))
            }

            segId
        }

        stateStore.setCurrentSegment(segmentId)

        // Reset
        currentSegmentType = null
        currentSegmentDayKey = null
        segmentSamples.clear()
        activityVotes.clear()
        // Don't reset lastSample — it's needed for displacement detection across segment boundaries

        return segmentId
    }

    /**
     * Returns a transient snapshot of the segment currently being accumulated in memory.
     * Used by the live timeline to show data before the 5-minute flush.
     * Not suspend — uses tryLock to avoid blocking the pipeline. Returns null if locked.
     */
    fun getInProgressSnapshot(): MovementSegmentEntity? {
        if (!mutex.tryLock()) return null
        try {
            val type = currentSegmentType ?: return null
            if (segmentSamples.isEmpty()) return null
            val first = segmentSamples.first()
            val last = segmentSamples.last()
            val isStationary = type == SegmentType.VISIT || type == SegmentType.DWELL
            var totalDistance = 0.0
            if (!isStationary) {
                for (i in 1 until segmentSamples.size) {
                    val prev = segmentSamples[i - 1]
                    val curr = segmentSamples[i]
                    val dist = LocationUtils.calculateDistance(
                        prev.lat, prev.lng, curr.lat, curr.lng
                    )
                    val noiseFloor =
                        ((prev.accuracyM ?: 10f) + (curr.accuracyM ?: 10f)) / 2.0
                    if (dist > noiseFloor) totalDistance += dist
                }
            }
            return MovementSegmentEntity(
                segmentId = -1,
                segmentType = type.name,
                startAt = first.capturedAt,
                endAt = last.capturedAt,
                startSampleId = first.sampleId,
                endSampleId = last.sampleId,
                distanceM = totalDistance,
                confidence = 0.7f,
                dayKey = ""
            )
        } finally {
            mutex.unlock()
        }
    }

    private fun mapToSegmentType(activity: ActivityType): SegmentType = when (activity) {
        // STILL maps to VISIT only when DetectVisitUseCase has confirmed a real visit
        // (PipelineConsumer overrides motionState to STILL for confirmed visits).
        // Unconfirmed STILL periods use VISIT too since the pipeline already filters
        // via effectiveMotionState — if we reach here with STILL, a visit is active.
        ActivityType.STILL -> SegmentType.DWELL
        ActivityType.WALKING -> SegmentType.WALK
        ActivityType.RUNNING -> SegmentType.RUN
        ActivityType.CYCLING, ActivityType.ON_BICYCLE -> SegmentType.CYCLE
        ActivityType.IN_VEHICLE -> SegmentType.DRIVE
        else -> SegmentType.UNKNOWN_MOTION
    }
}
