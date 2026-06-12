package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.enums.VisitSource
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
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
    private val placeDao: PlaceDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        /** Radius expansion factor after visit confirmation (prevents GPS jitter departures) */
        private const val CONFIRMED_RADIUS_MULTIPLIER = 1.2
        /** Grace period before Accumulating suppresses motion state in the pipeline.
         *  During this window, CandidateStarted is returned so transit samples
         *  preserve their fused activity (enables WALK/DRIVE segments between visits). */
        private const val ACCUMULATION_GRACE_MS = 90_000L // 90 seconds
        /** Known/matched places confirm faster than unknown ones — fraction of the
         *  user-configured min dwell applied to matched-place candidates. */
        private const val KNOWN_PLACE_DWELL_FRACTION = 2.0 / 3.0
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

    /** Adaptive radius based on GPS accuracy — tighter in good conditions, wider in poor.
     *  Scales against the user-configured [placeRadiusM] (the wide/poor-accuracy bound). */
    private fun effectiveRadius(accuracyM: Float?, placeRadiusM: Double): Double = when {
        accuracyM != null && accuracyM <= 10f -> placeRadiusM * 0.5
        accuracyM != null && accuracyM <= 25f -> placeRadiusM * 0.75
        else -> placeRadiusM
    }

    suspend fun processSample(sample: RawSample, dayKey: String): VisitDetectionResult {
        val settings = settingsRepository.observeSettings().value
        val placeRadiusM = settings.placeRadiusM.toDouble()
        val minSamplesForCandidate = settings.entryHysteresisCount
        val exitHysteresisThreshold = settings.exitHysteresisCount
        val placeAnchorBufferM = settings.exitBufferM.toDouble()
        val unknownDwellMs = settings.minDwellMinutes * 60_000L
        val knownPlaceDwellMs = (unknownDwellMs * KNOWN_PLACE_DWELL_FRACTION).toLong()

        val state = stateStore.getState()
        val candidate = state.pendingVisitCandidate
        val baseRadius = effectiveRadius(sample.accuracyM, placeRadiusM)
        // Expand radius after visit confirmation to resist GPS jitter departures
        val radius = if (state.lastConfirmedVisitId != null) {
            (baseRadius * CONFIRMED_RADIUS_MULTIPLIER).coerceAtMost(placeRadiusM)
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
                // First-stable timestamp: latch the first sample that sits well inside
                // the candidate radius (half the radius), which means the centroid has
                // converged and the user is genuinely *there* — not still walking up to
                // the place. This becomes the reported arrival/dwell anchor.
                val firstStableSampleAt = candidate.firstStableSampleAt
                    ?: sample.capturedAt.takeIf { distance < radius * 0.5 }
                val updated = if (isConfirmed) {
                    // Post-confirmation: freeze centroid to keep place matching stable.
                    // Only track spread for radius estimation.
                    candidate.copy(
                        sampleCount = n,
                        maxDistanceFromCentroidM = maxOf(candidate.maxDistanceFromCentroidM, distance),
                        firstStableSampleAt = firstStableSampleAt,
                        lastInsideSampleAt = sample.capturedAt
                    )
                } else {
                    // Pre-confirmation: keep updating centroid for GPS convergence accuracy
                    candidate.copy(
                        centroidLat = candidate.centroidLat + (sample.lat - candidate.centroidLat) / n,
                        centroidLng = candidate.centroidLng + (sample.lng - candidate.centroidLng) / n,
                        sampleCount = n,
                        maxDistanceFromCentroidM = maxOf(candidate.maxDistanceFromCentroidM, distance),
                        firstStableSampleAt = firstStableSampleAt,
                        lastInsideSampleAt = sample.capturedAt
                    )
                }
                stateStore.setPendingVisitCandidate(updated)

                // Confirmation timing keys off accumulationStartAt (the candidate's
                // first-ever sample). firstStableSampleAt refines only the *persisted*
                // arrival time on confirmation — see confirmVisit() — so that the
                // confirmation threshold is stable across releases while the timeline
                // arrival is more accurate.
                val dwellMs = sample.capturedAt - candidate.accumulationStartAt
                val effectiveDwell = if (updated.matchedPlaceId != null) knownPlaceDwellMs else unknownDwellMs
                if (dwellMs >= effectiveDwell && updated.sampleCount >= minSamplesForCandidate) {
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
                // Outside candidate area — require exitHysteresisCount consecutive
                // outside samples before confirming departure (prevents GPS jitter exits)
                consecutiveOutsideSamples++
                if (consecutiveOutsideSamples < exitHysteresisThreshold) {
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
                            if (distToPlace <= place.radiusM + placeAnchorBufferM) {
                                // Still within place boundary — don't depart
                                consecutiveOutsideSamples = 0
                                return VisitDetectionResult.Accumulating(candidate)
                            }
                        }
                    }
                }

                // Departure time is the LAST sample we were actually inside the place —
                // not this exit-confirming sample, which sits exitHysteresis samples (and
                // the walk-out) later and would inflate the dwell (T10). Falls back to the
                // current sample only if we somehow never recorded an inside sample.
                val departureAt = candidate.lastInsideSampleAt ?: sample.capturedAt

                // Confirmed departure — persist context for return continuation
                // (survives process kill so quick-return detection works after restart)
                consecutiveOutsideSamples = 0
                stateStore.update {
                    it.copy(
                        lastDepartedCentroidLat = candidate.centroidLat,
                        lastDepartedCentroidLng = candidate.centroidLng,
                        lastDepartureTime = departureAt,
                        lastDepartedVisitId = lastConfirmedVisitId
                    )
                }

                if (lastConfirmedVisitId != null) {
                    val visit = visitDao.getById(lastConfirmedVisitId)
                    if (visit != null && visit.departureAt == null) {
                        visitDao.update(visit.copy(
                            departureAt = departureAt,
                            dwellMs = departureAt - visit.arrivalAt
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
                val distFromPrevious = LocationUtils.calculateDistance(
                    depLat, depLng, sample.lat, sample.lng
                )
                // Movement that closed during the departure→return gap. A motorised hop
                // (drive/cycle/run/flight/transit) or a meaningful on-foot excursion is hard
                // evidence the user genuinely moved away — see QuickReturnPolicy (T6).
                val gapSegments = movementSegmentDao
                    .getByDayKey(dayKey)
                    .filter { it.endAt > depTime && it.endAt <= sample.capturedAt }
                    .map { QuickReturnPolicy.GapSegment(it.segmentType, it.distanceM) }
                val decision = QuickReturnPolicy.decide(
                    timeSinceDepartureMs = sample.capturedAt - depTime,
                    distFromPreviousM = distFromPrevious,
                    placeRadiusM = placeRadiusM,
                    hasPreviousVisit = depVisitId != null,
                    gapSegments = gapSegments,
                )
                when (decision) {
                    QuickReturnPolicy.Decision.CLEAR_MEMORY -> clearDepartureMemory()
                    QuickReturnPolicy.Decision.KEEP_WAITING -> { /* retain memory; start fresh below */ }
                    QuickReturnPolicy.Decision.CONTINUE -> {
                        // Quick return — reopen previous visit as continuation
                        val previousVisit = depVisitId?.let { visitDao.getById(it) }
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
        // Anchor arrival at the first-stable sample (centroid converged inside
        // half-radius); falls back to the candidate start if convergence didn't
        // happen — a small visit confirmed via raw sample-count alone.
        val arrivalAt = candidate.firstStableSampleAt ?: candidate.accumulationStartAt
        val visit = VisitEntity(
            placeId = candidate.matchedPlaceId ?: 0,
            arrivalAt = arrivalAt,
            departureAt = null, // ongoing
            dwellMs = currentTime - arrivalAt,
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
