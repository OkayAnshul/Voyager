package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.capture.ActivityCapture
import com.cosmiclaboratory.voyager.capture.AdaptiveSamplingPolicy
import com.cosmiclaboratory.voyager.capture.DormantModeManager
import com.cosmiclaboratory.voyager.capture.GeofenceEventHandler
import com.cosmiclaboratory.voyager.capture.LocationCapture
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.usecase.DetectVisitUseCase
import com.cosmiclaboratory.voyager.domain.usecase.FuseActivityStateUseCase
import com.cosmiclaboratory.voyager.domain.usecase.MatchPlaceLiveUseCase
import com.cosmiclaboratory.voyager.domain.usecase.VisitDetectionResult
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.domain.model.InProgressSegmentSnapshot
import com.cosmiclaboratory.voyager.pipeline.stage.*
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consumes samples from PipelineSerializer.sampleChannel and processes them
 * through the pipeline stages: normalize -> quality -> dedup -> fuse -> visit -> segment -> commit.
 * Visit detection runs BEFORE segmenter so dwell periods aren't fragmented.
 *
 * INVARIANT: This consumer must run on a single coroutine. Pipeline stages
 * (DedupSuppressor, MatchPlaceLiveUseCase, Segmenter) hold mutable state without
 * synchronization, relying on sequential processing from the channel's for-loop.
 */
@Singleton
class PipelineConsumer @Inject constructor(
    private val pipelineSerializer: PipelineSerializer,
    private val sampleNormalizer: SampleNormalizer,
    private val kalmanFilter: LocationKalmanFilter,
    private val qualityScorer: QualityScorer,
    private val dedupSuppressor: DedupSuppressor,
    private val fuseActivityStateUseCase: FuseActivityStateUseCase,
    private val segmenter: Segmenter,
    private val stateCommitter: StateCommitter,
    private val pipelineGateway: PipelineGateway,
    private val detectVisitUseCase: DetectVisitUseCase,
    private val matchPlaceLiveUseCase: MatchPlaceLiveUseCase,
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val locationCapture: LocationCapture,
    private val activityCapture: ActivityCapture,
    private val dormantModeManager: DormantModeManager,
    private val geofenceEventHandler: GeofenceEventHandler,
    private val placeLinkingService: PlaceLinkingService,
    private val timelineStateStore: TimelineStateStore,
    private val settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository,
    private val dayBoundaryResolver: com.cosmiclaboratory.voyager.domain.util.DayBoundaryResolver,
    private val logger: ProductionLogger
) {
    private val started = AtomicBoolean(false)
    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private var gapWatchdogJob: Job? = null
    private var lastAcceptedSample: RawSample? = null
    private var lastDisplacementOverrideAt: Long = 0

    companion object {
        private const val AR_STALENESS_MS = 180_000L // 3 minutes
        private const val GAP_WATCHDOG_MULTIPLIER = 5
        private const val GAP_WATCHDOG_CHECK_MS = 60_000L
        private const val MIN_GAP_DURATION_MS = 600_000L
        private const val DISPLACEMENT_TRANSIT_THRESHOLD_M = PipelineConstants.DISPLACEMENT_TRANSIT_THRESHOLD_M
        private const val DISPLACEMENT_SPEED_THRESHOLD_MPS = PipelineConstants.DISPLACEMENT_SPEED_THRESHOLD_MPS
        private const val DISPLACEMENT_MAX_ACCURACY_M = PipelineConstants.DISPLACEMENT_MAX_ACCURACY_M
        private const val DISPLACEMENT_POST_DORMANT_MAX_ACCURACY_M = PipelineConstants.DISPLACEMENT_POST_DORMANT_MAX_ACCURACY_M
        private const val DISPLACEMENT_DORMANT_GRACE_MS = PipelineConstants.DISPLACEMENT_DORMANT_GRACE_MS
        private const val DISPLACEMENT_COOLDOWN_MS = PipelineConstants.DISPLACEMENT_COOLDOWN_MS
    }

    /**
     * Reset all mutable in-memory state held by pipeline stages.
     * Must be called on session stop to prevent stale state from leaking into the next session.
     */
    suspend fun resetSessionState() {
        dedupSuppressor.reset()
        matchPlaceLiveUseCase.resetHysteresis()
        detectVisitUseCase.clearDepartureMemory()
        fuseActivityStateUseCase.reset()
        adaptiveSamplingPolicy.resetHysteresis()
        dormantModeManager.reset()
        kalmanFilter.reset()
        lastAcceptedSample = null
        lastDisplacementOverrideAt = 0
    }

    /**
     * Start consuming from the pipeline channel.
     * Call this once from Application scope.
     */
    fun start(scope: CoroutineScope) {
        check(started.compareAndSet(false, true)) { "PipelineConsumer.start() called twice" }
        placeLinkingService.setGeocodeScope(scope)
        scope.launch {
            for (rawSample in pipelineSerializer.sampleChannel) {
                try {
                    processSample(rawSample)
                } catch (e: android.database.sqlite.SQLiteFullException) {
                    // F1: device storage exhausted — keep draining the channel so we
                    // don't deadlock the producer, but surface it loudly.
                    logger.e("PipelineConsumer", "Storage full — sample dropped", e)
                } catch (e: Exception) {
                    logger.e("PipelineConsumer", "Error processing sample", e)
                }
            }
        }
        startGapWatchdog(scope)
    }

    private fun startGapWatchdog(scope: CoroutineScope) {
        gapWatchdogJob = scope.launch {
            var lastGapCreatedAt: Long = 0
            while (true) {
                delay(GAP_WATCHDOG_CHECK_MS)
                val state = timelineStateStore.getState()
                if (state.activeSessionId == null) continue

                activityCapture.reRegisterIfStale()

                val lastAt = state.lastAcceptedAt ?: continue
                val silenceMs = System.currentTimeMillis() - lastAt
                val expectedInterval = adaptiveSamplingPolicy.getCurrentPolicy().intervalMs
                val now = System.currentTimeMillis()
                val inDormantGrace = dormantModeManager.dormantExitedAt > 0L &&
                    now - dormantModeManager.dormantExitedAt <= DormantModeManager.DORMANT_EXIT_GRACE_MS
                if (expectedInterval > 0 &&
                    !inDormantGrace &&
                    silenceMs > expectedInterval * GAP_WATCHDOG_MULTIPLIER &&
                    silenceMs >= MIN_GAP_DURATION_MS &&
                    lastAt != lastGapCreatedAt
                ) {
                    val gapReason = if (dormantModeManager.isDormant) "DORMANT" else "GPS_LOSS"
                    createGapSegment(lastAt, now, gapReason)
                    lastGapCreatedAt = lastAt
                    logger.w("PipelineConsumer", "Gap watchdog: no sample for ${silenceMs / 1000}s, reason=$gapReason")
                }
            }
        }
    }

    private suspend fun createGapSegment(startMs: Long, endMs: Long, reason: String) {
        val zone = ZoneId.of(java.util.TimeZone.getDefault().id)
        val dayKey = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().format(dayKeyFormatter)
        pipelineGateway.recordGapSegment(startAt = startMs, endAt = endMs, dayKey = dayKey, reason = reason)
    }

    private suspend fun bridgeGapIfNeeded(sampleTime: Long) {
        val latest = pipelineGateway.latestSegment() ?: return
        if (latest.segmentType == com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.GAP.name &&
            latest.endAt < sampleTime
        ) {
            pipelineGateway.updateSegmentEndAt(latest.segmentId, sampleTime)
        }
    }

    private suspend fun processSample(rawSample: RawSample) {
        val pipelineStartMs = System.currentTimeMillis()

        bridgeGapIfNeeded(rawSample.capturedAt)
        stateCommitter.commitTimestampEarly(rawSample.capturedAt)

        // 1. Persist raw sample
        val sampleId = pipelineGateway.recordSample(rawSample)
        val sample = rawSample.copy(sampleId = sampleId)

        // 2. Normalize
        val normalized = sampleNormalizer.normalize(sample)

        // 2a. Dedup on RAW data (before Kalman shift)
        if (dedupSuppressor.shouldSuppress(normalized)) return

        // 2b. Kalman filter
        val filtered = kalmanFilter.filter(normalized.lat, normalized.lng, normalized.accuracyM, normalized.capturedAt)
        val smoothed = normalized.copy(
            lat = filtered.lat, lng = filtered.lng,
            speedMps = if (filtered.estimatedSpeedMps > 0.1f) filtered.estimatedSpeedMps else normalized.speedMps,
            bearingDeg = if (filtered.estimatedSpeedMps > 0.3f) filtered.estimatedBearing else normalized.bearingDeg
        )

        // 3. Quality score
        val quality = qualityScorer.score(smoothed)
        if (quality.shouldDiscard) {
            logger.d("PipelineConsumer", "Discarded sample: ${quality.reason}")
            return
        }

        // 4. Fuse activity state
        val arSample = pipelineGateway.latestActivity()
        val arAge = smoothed.capturedAt - (arSample?.capturedAt ?: 0L)
        val isFresh = arAge < AR_STALENESS_MS
        val arActivity = if (isFresh) arSample?.let { parseActivityType(it.activityType) } else null
        val arConfidence = if (isFresh) (arSample?.confidence?.toFloat() ?: 0f) else 0f
        val stepRate = computeStepRate(smoothed.capturedAt)

        val motionState = fuseActivityStateUseCase.fuse(
            arActivity = arActivity, arConfidence = arConfidence,
            speedMps = smoothed.speedMps, stepRatePerMinute = stepRate,
            accuracyM = smoothed.accuracyM
        )

        // 4b. Update adaptive sampling with hysteresis
        val samplingMotion = mapToSamplingMotionState(motionState.activityType, smoothed)
        if (adaptiveSamplingPolicy.updateMotionStateWithHysteresis(samplingMotion)) {
            locationCapture.updateSamplingPolicy()
            timelineStateStore.update { it.copy(lastMotionState = samplingMotion.name) }
        }

        // 4b-bis. Battery-saver sampling — stretch intervals when battery is below
        // the user-configured threshold (and not charging).
        rawSample.batteryPct?.let { pct ->
            val threshold = settingsRepository.observeSettings().value.batterySaverThresholdPct
            val saverActive = pct < threshold && rawSample.isCharging != true
            adaptiveSamplingPolicy.setBatterySaverMultiplier(if (saverActive) 2.0f else 1.0f)
        }

        // 4c. Dormant mode management
        if (dormantModeManager.onActivityUpdate(motionState.activityType)) {
            timelineStateStore.update { it.copy(lastMotionState = dormantModeManager.currentMotionStateName) }
        }

        // Day-key assignment honours the user's day-boundary mode (home timezone
        // vs travel-aware) instead of always using the sample's capture timezone.
        val daySettings = settingsRepository.observeSettings().value
        val dayKey = dayBoundaryResolver.resolveDayKey(
            instantEpochMs = smoothed.capturedAt,
            mode = daySettings.dayBoundaryMode,
            homeTimeZone = daySettings.homeTimeZone,
            sampleTimeZone = smoothed.localTimeZone
        )

        // 5. Displacement-based movement detection
        val prevSample = lastAcceptedSample
        lastAcceptedSample = smoothed
        var displacementOverrideActivity: ActivityType? = null

        if (prevSample != null) {
            displacementOverrideActivity = checkDisplacement(prevSample, smoothed)
            if (displacementOverrideActivity != null) {
                detectVisitUseCase.forceDeparture(prevSample.capturedAt)
                adaptiveSamplingPolicy.forceMotionState(AdaptiveSamplingPolicy.MotionState.DRIVING)
                locationCapture.updateSamplingPolicy()
                timelineStateStore.update { it.copy(lastMotionState = AdaptiveSamplingPolicy.MotionState.DRIVING.name) }
            }
        }

        // 5d. Geofence hint
        val geofencePlaceId = geofenceEventHandler.lastEnteredPlaceId
        if (geofencePlaceId != null &&
            System.currentTimeMillis() - geofenceEventHandler.lastEnteredAt < 600_000L
        ) {
            val candidate = timelineStateStore.getState().pendingVisitCandidate
            if (candidate != null && candidate.matchedPlaceId == null) {
                timelineStateStore.setPendingVisitCandidate(candidate.copy(matchedPlaceId = geofencePlaceId))
            }
        }

        // 6. Visit detection (BEFORE segmenter so dwell isn't fragmented)
        val visitResult = if (displacementOverrideActivity != null) {
            VisitDetectionResult.Departed
        } else {
            detectVisitUseCase.processSample(smoothed, dayKey)
        }
        placeLinkingService.handleVisitResult(visitResult, smoothed, dayKey)

        // On departure, close DWELL segment and boost sampling
        if (visitResult is VisitDetectionResult.Departed) {
            segmenter.closeCurrentSegment(dayKey)
            if (displacementOverrideActivity == null) {
                adaptiveSamplingPolicy.forceMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
                locationCapture.updateSamplingPolicy()
            }
        }

        // Override motion for segmenter based on visit state
        val effectiveMotionState = when {
            displacementOverrideActivity != null ->
                motionState.copy(activityType = displacementOverrideActivity)
            visitResult is VisitDetectionResult.Accumulating ||
                visitResult is VisitDetectionResult.Confirmed ->
                motionState.copy(activityType = ActivityType.STILL)
            else -> motionState
        }

        // 7. Segment
        segmenter.processSample(smoothed, effectiveMotionState, dayKey)

        // 7b. Push in-progress segment snapshot for live UI
        val snapshot = segmenter.getInProgressSnapshot()
        timelineStateStore.setInProgressSegment(snapshot?.let {
            InProgressSegmentSnapshot(
                segmentType = it.segmentType, startAt = it.startAt,
                endAt = it.endAt, distanceM = it.distanceM, sampleCount = 0
            )
        })

        // 8. Commit state
        stateCommitter.commit(smoothed, pipelineStartMs)
    }

    private var displacementStrikeCount = 0

    private fun checkDisplacement(prev: RawSample, curr: RawSample): ActivityType? {
        // Skip displacement during GPS settle period after dormant wake
        if (System.currentTimeMillis() - dormantModeManager.dormantExitedAt < DISPLACEMENT_DORMANT_GRACE_MS) {
            displacementStrikeCount = 0
            return null
        }

        // Tighter accuracy requirement shortly after dormant exit (cold GPS)
        val isPostDormant = System.currentTimeMillis() - dormantModeManager.dormantExitedAt < 120_000L
        val maxAccuracy = if (isPostDormant) DISPLACEMENT_POST_DORMANT_MAX_ACCURACY_M else DISPLACEMENT_MAX_ACCURACY_M

        val prevAcc = prev.accuracyM ?: maxAccuracy
        val currAcc = curr.accuracyM ?: maxAccuracy
        if (prevAcc > maxAccuracy || currAcc > maxAccuracy) {
            displacementStrikeCount = 0
            return null
        }

        val displacement = LocationUtils.calculateDistance(prev.lat, prev.lng, curr.lat, curr.lng)
        val timeDeltaMs = curr.capturedAt - prev.capturedAt
        if (displacement <= DISPLACEMENT_TRANSIT_THRESHOLD_M || timeDeltaMs <= 0) {
            displacementStrikeCount = 0
            return null
        }

        val impliedSpeed = displacement / (timeDeltaMs / 1000.0)
        if (impliedSpeed <= DISPLACEMENT_SPEED_THRESHOLD_MPS) {
            displacementStrikeCount = 0
            return null
        }
        if (System.currentTimeMillis() - lastDisplacementOverrideAt <= DISPLACEMENT_COOLDOWN_MS) return null

        // Require 2 consecutive displacement detections to prevent single GPS jump false positives
        displacementStrikeCount++
        if (displacementStrikeCount < 2) return null

        displacementStrikeCount = 0
        lastDisplacementOverrideAt = System.currentTimeMillis()
        logger.d("PipelineConsumer", "Displacement override: ${displacement.toInt()}m, speed=${impliedSpeed.toInt()}m/s")

        return when {
            impliedSpeed >= 7.5 -> ActivityType.IN_VEHICLE
            impliedSpeed >= 3.7 -> ActivityType.CYCLING
            else -> ActivityType.WALKING
        }
    }

    private fun parseActivityType(name: String): ActivityType? =
        try { ActivityType.valueOf(name) } catch (_: IllegalArgumentException) { null }

    private suspend fun computeStepRate(currentTimeMs: Long): Float? {
        val windowMs = 60_000L
        val recentSteps = pipelineGateway.stepBuckets(currentTimeMs - windowMs, currentTimeMs)
        if (recentSteps.isEmpty()) return null
        val totalSteps = recentSteps.sumOf { it.stepCount }
        val spanMs = recentSteps.last().periodEnd - recentSteps.first().periodStart
        if (spanMs <= 0) return null
        return (totalSteps.toFloat() / spanMs) * 60_000f
    }

    private fun mapToSamplingMotionState(
        activity: ActivityType,
        sample: RawSample
    ): AdaptiveSamplingPolicy.MotionState = when {
        sample.isCharging == true -> AdaptiveSamplingPolicy.MotionState.CHARGING
        activity == ActivityType.UNKNOWN ->
            adaptiveSamplingPolicy.getLastConfirmedState() ?: AdaptiveSamplingPolicy.MotionState.STILL
        activity == ActivityType.STILL -> AdaptiveSamplingPolicy.MotionState.STILL
        activity == ActivityType.WALKING -> AdaptiveSamplingPolicy.MotionState.WALKING
        activity == ActivityType.RUNNING -> AdaptiveSamplingPolicy.MotionState.RUNNING
        activity == ActivityType.CYCLING || activity == ActivityType.ON_BICYCLE ->
            AdaptiveSamplingPolicy.MotionState.CYCLING
        activity == ActivityType.IN_VEHICLE -> AdaptiveSamplingPolicy.MotionState.DRIVING
        else -> AdaptiveSamplingPolicy.MotionState.STILL
    }
}
