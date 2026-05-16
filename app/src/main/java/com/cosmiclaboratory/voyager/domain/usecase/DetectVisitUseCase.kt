package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.enums.VisitSource
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitWriteGuard
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEvidenceEntity
import javax.inject.Inject

class DetectVisitUseCase @Inject constructor(
    private val stateStore: TimelineStateStore,
    private val visitDao: VisitDao,
    private val visitWriteGuard: VisitWriteGuard,
    private val visitEvidenceDao: VisitEvidenceDao,
    private val placeDao: PlaceDao
) {
    companion object {
        private const val MIN_SAMPLES_FOR_CANDIDATE = 3
        private const val MIN_DWELL_MS = 180_000L // 3 minutes (unknown locations)
        private const val MIN_DWELL_KNOWN_PLACE_MS = 120_000L // 2 minutes (matched/known places)
        private const val EXIT_HYSTERESIS_THRESHOLD = 3
        /** Max time window to treat a return as continuation of the previous visit.
         *  30 minutes covers typical 15-30 min PROCESS_DEAD gaps while RETURN_RADIUS_M
         *  (80m) provides spatial gating to prevent false joins at nearby places. */
        private const val RETURN_WINDOW_MS = 1_800_000L // 30 minutes
        /** Max distance from previous visit centroid to treat as a return */
        private const val RETURN_RADIUS_M = 80.0
        /** Radius expansion factor after visit confirmation (prevents GPS jitter departures) */
        private const val CONFIRMED_RADIUS_MULTIPLIER = 1.2
        private const val MAX_CONFIRMED_RADIUS = 80.0
        /** Buffer added to place radius for place-anchored departure resistance */
        private const val PLACE_ANCHOR_BUFFER_M = 30.0
        /** Grace period before Accumulating suppresses motion state in the pipeline.
         *  During this window, CandidateStarted is returned so transit samples
         *  preserve their fused activity (enables WALK/DRIVE segments between visits). */
        private const val ACCUMULATION_GRACE_MS = 90_000L // 90 seconds
    }

    var consecutiveOutsideSamples = 0
        private set

    suspend fun clearDepartureMemory() {
        stateStore.update {
            it.copy(
                lastDepartedCentroidLat = null,
                lastDepartedCentroidLng = null,
                lastDepartureTime = null,
                lastDepartedVisitId = null
            )
        }
    }

    /** Adaptive radius based on GPS accuracy — tighter in good conditions, wider in poor. */
    private fun effectiveRadius(accuracyM: Float?): Double = when {
        accuracyM != null && accuracyM <= 10f -> 40.0
        accuracyM != null && accuracyM <= 25f -> 60.0
        else -> 80.0
    }

    suspend fun processSample(sample: RawSample, dayKey: String): VisitDetectionResult {
        val state = stateStore.getState()
        val candidate = state.pendingVisitCandidate
        val baseRadius = effectiveRadius(sample.accuracyM)
        // Expand radius after visit confirmation to resist GPS jitter departures
        val radius = if (state.lastConfirmedVisitId != null) {
            (baseRadius * CONFIRMED_RADIUS_MULTIPLIER).coerceAtMost(MAX_CONFIRMED_RADIUS)
        } else {
            baseRadius
        }

        if (candidate != null) {
            val distance = LocationUtils.calculateDistance(
                candidate.centroidLat, candidate.centroidLng,
                sample.lat, sample.lng
            )

            if (distance <= radius) {
                // Still within candidate area — reset exit counter, update centroid
                consecutiveOutsideSamples = 0
                val n = candidate.sampleCount + 1
                val isConfirmed = state.lastConfirmedVisitId != null
                val updated = if (isConfirmed) {
                    // Post-confirmation: freeze centroid to keep place matching stable.
                    // Only track spread for radius estimation.
                    candidate.copy(
                        sampleCount = n,
                        maxDistanceFromCentroidM = maxOf(candidate.maxDistanceFromCentroidM, distance)
                    )
                } else {
                    // Pre-confirmation: keep updating centroid for GPS convergence accuracy
                    candidate.copy(
                        centroidLat = candidate.centroidLat + (sample.lat - candidate.centroidLat) / n,
                        centroidLng = candidate.centroidLng + (sample.lng - candidate.centroidLng) / n,
                        sampleCount = n,
                        maxDistanceFromCentroidM = maxOf(candidate.maxDistanceFromCentroidM, distance)
                    )
                }
                stateStore.setPendingVisitCandidate(updated)

                val dwellMs = sample.capturedAt - candidate.accumulationStartAt
                val effectiveDwell = if (updated.matchedPlaceId != null) MIN_DWELL_KNOWN_PLACE_MS else MIN_DWELL_MS
                if (dwellMs >= effectiveDwell && updated.sampleCount >= MIN_SAMPLES_FOR_CANDIDATE) {
                    // Already confirmed this visit? Keep dwelling without re-inserting.
                    if (state.lastConfirmedVisitId != null) {
                        return VisitDetectionResult.Accumulating(updated)
                    }
                    return confirmVisit(updated, sample.capturedAt, dayKey)
                }
                // Grace period: during early accumulation, return CandidateStarted
                // so PipelineConsumer preserves the fused motion state. This enables
                // WALK/DRIVE segments during transit between visits. After the grace
                // period, return Accumulating to suppress motion for true dwell.
                return if (dwellMs < ACCUMULATION_GRACE_MS) {
                    VisitDetectionResult.CandidateStarted(updated)
                } else {
                    VisitDetectionResult.Accumulating(updated)
                }
            } else {
                // Outside candidate area — require EXIT_HYSTERESIS_THRESHOLD consecutive
                // outside samples before confirming departure (prevents GPS jitter exits)
                consecutiveOutsideSamples++
                if (consecutiveOutsideSamples < EXIT_HYSTERESIS_THRESHOLD) {
                    // Not enough outside samples yet — keep candidate alive
                    return VisitDetectionResult.Accumulating(candidate)
                }

                // Place-anchored departure resistance: if the sample is still within
                // the matched place's radius, don't depart (prevents GPS jitter exits
                // from a known place even if outside the candidate's tighter radius)
                val lastConfirmedVisitId = state.lastConfirmedVisitId
                if (lastConfirmedVisitId != null) {
                    val visit = visitDao.getById(lastConfirmedVisitId)
                    if (visit != null && visit.placeId != 0L) {
                        val place = placeDao.getById(visit.placeId)
                        if (place != null) {
                            val distToPlace = LocationUtils.calculateDistance(
                                sample.lat, sample.lng,
                                place.centroidLat, place.centroidLng
                            )
                            if (distToPlace <= place.radiusM + PLACE_ANCHOR_BUFFER_M) {
                                // Still within place boundary — don't depart
                                consecutiveOutsideSamples = 0
                                return VisitDetectionResult.Accumulating(candidate)
                            }
                        }
                    }
                }

                // Confirmed departure — persist context for return continuation
                // (survives process kill so quick-return detection works after restart)
                consecutiveOutsideSamples = 0
                stateStore.update {
                    it.copy(
                        lastDepartedCentroidLat = candidate.centroidLat,
                        lastDepartedCentroidLng = candidate.centroidLng,
                        lastDepartureTime = sample.capturedAt,
                        lastDepartedVisitId = lastConfirmedVisitId
                    )
                }

                if (lastConfirmedVisitId != null) {
                    val visit = visitDao.getById(lastConfirmedVisitId)
                    if (visit != null && visit.departureAt == null) {
                        visitDao.update(visit.copy(
                            departureAt = sample.capturedAt,
                            dwellMs = sample.capturedAt - visit.arrivalAt
                        ))
                    }
                    stateStore.setLastConfirmedVisitId(null)
                }
                stateStore.setPendingVisitCandidate(null)
                return VisitDetectionResult.Departed
            }
        } else {
            // No active candidate — check for quick return to previous visit.
            // Departure context is persisted in StateStore so it survives process kill.
            val depLat = state.lastDepartedCentroidLat
            val depLng = state.lastDepartedCentroidLng
            val depTime = state.lastDepartureTime
            val depVisitId = state.lastDepartedVisitId
            if (depLat != null && depLng != null && depTime != null) {
                val timeSinceDeparture = sample.capturedAt - depTime
                if (timeSinceDeparture > RETURN_WINDOW_MS) {
                    // Window expired — clear memory, proceed with new candidate
                    clearDepartureMemory()
                } else {
                    val distFromPrevious = LocationUtils.calculateDistance(
                        depLat, depLng, sample.lat, sample.lng
                    )
                    if (distFromPrevious <= RETURN_RADIUS_M && depVisitId != null) {
                        // Quick return — reopen previous visit as continuation
                        val previousVisit = visitDao.getById(depVisitId)
                        if (previousVisit != null) {
                            visitDao.update(previousVisit.copy(departureAt = null, dwellMs = null))
                            stateStore.setLastConfirmedVisitId(depVisitId)
                            val restoredCandidate = PendingVisitCandidate(
                                centroidLat = depLat,
                                centroidLng = depLng,
                                accumulationStartAt = previousVisit.arrivalAt,
                                sampleCount = 1,
                                maxDistanceFromCentroidM = distFromPrevious,
                                matchedPlaceId = previousVisit.placeId.takeIf { it != 0L }
                            )
                            stateStore.setPendingVisitCandidate(restoredCandidate)
                            clearDepartureMemory()
                            return VisitDetectionResult.Accumulating(restoredCandidate)
                        }
                    }
                }
            }

            // Start new candidate
            val newCandidate = PendingVisitCandidate(
                centroidLat = sample.lat,
                centroidLng = sample.lng,
                accumulationStartAt = sample.capturedAt,
                sampleCount = 1,
                maxDistanceFromCentroidM = 0.0,
                matchedPlaceId = null
            )
            stateStore.setPendingVisitCandidate(newCandidate)
            return VisitDetectionResult.CandidateStarted(newCandidate)
        }
    }

    /**
     * Force-close the current visit and candidate due to detected displacement.
     * Called by PipelineConsumer when displacement-based movement detection fires.
     */
    suspend fun forceDeparture(departureTime: Long): VisitDetectionResult {
        val state = stateStore.getState()
        consecutiveOutsideSamples = 0

        val lastConfirmedVisitId = state.lastConfirmedVisitId
        if (lastConfirmedVisitId != null) {
            val visit = visitDao.getById(lastConfirmedVisitId)
            if (visit != null && visit.departureAt == null) {
                visitDao.update(visit.copy(
                    departureAt = departureTime,
                    dwellMs = departureTime - visit.arrivalAt
                ))
            }
            stateStore.setLastConfirmedVisitId(null)
        }
        stateStore.setPendingVisitCandidate(null)
        return VisitDetectionResult.Departed
    }

    private suspend fun confirmVisit(
        candidate: PendingVisitCandidate,
        currentTime: Long,
        dayKey: String
    ): VisitDetectionResult {
        val visit = VisitEntity(
            placeId = candidate.matchedPlaceId ?: 0,
            arrivalAt = candidate.accumulationStartAt,
            departureAt = null, // ongoing
            dwellMs = currentTime - candidate.accumulationStartAt,
            source = VisitSource.LIVE_DETECTION.name,
            confidence = 0.7f,
            dayKey = dayKey,
            centroidLat = candidate.centroidLat,
            centroidLng = candidate.centroidLng
        )

        val visitId = visitWriteGuard.insertIfNotOverlapping(visit)
        if (visitId == -1L) {
            stateStore.setPendingVisitCandidate(null)
            return VisitDetectionResult.OverlapRejected
        }

        // Track the confirmed visit for departure handling
        stateStore.setLastConfirmedVisitId(visitId)

        visitEvidenceDao.upsert(
            VisitEvidenceEntity(
                visitId = visitId,
                insideCount = candidate.sampleCount,
                arrivalConfidence = 0.7f
            )
        )

        return VisitDetectionResult.Confirmed(visitId)
    }
}

sealed class VisitDetectionResult {
    data class CandidateStarted(val candidate: PendingVisitCandidate) : VisitDetectionResult()
    data class Accumulating(val candidate: PendingVisitCandidate) : VisitDetectionResult()
    data class Confirmed(val visitId: Long) : VisitDetectionResult()
    data object Departed : VisitDetectionResult()
    data object OverlapRejected : VisitDetectionResult()
}
