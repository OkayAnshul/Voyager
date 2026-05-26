package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.capture.PlaceGeofenceManager
import com.cosmiclaboratory.voyager.capture.WifiFingerprinter
import com.cosmiclaboratory.voyager.domain.model.enums.PlaceLifecycleStatus
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.domain.usecase.EnrichPlaceWithDetailsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.IntegrityRepairUseCase
import com.cosmiclaboratory.voyager.domain.usecase.MatchPlaceLiveUseCase
import com.cosmiclaboratory.voyager.domain.usecase.VisitDetectionResult
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles place matching, linking, creation, and geocoding after visit detection.
 * Extracted from PipelineConsumer to keep pipeline orchestration focused.
 *
 * All persistence goes through [PipelineGateway] — this file holds **only**
 * orchestration logic (which visit/segment/place is linked to what, when to
 * auto-promote, when to geocode). The gateway impl in `data/` does the Room mapping.
 */
@Singleton
class PlaceLinkingService @Inject constructor(
    private val matchPlaceLiveUseCase: MatchPlaceLiveUseCase,
    private val pipelineGateway: PipelineGateway,
    private val enrichPlaceUseCase: EnrichPlaceWithDetailsUseCase,
    private val geocodingRepository: GeocodingRepository,
    private val settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository,
    private val integrityRepairUseCase: IntegrityRepairUseCase,
    private val placeGeofenceManager: PlaceGeofenceManager,
    private val wifiFingerprinter: WifiFingerprinter,
    private val timelineStateStore: TimelineStateStore,
    private val logger: ProductionLogger
) {
    private lateinit var geocodeScope: CoroutineScope

    companion object {
        private const val AUTO_PROMOTE_VISIT_THRESHOLD = 3
    }

    fun setGeocodeScope(scope: CoroutineScope) {
        geocodeScope = scope
    }

    /**
     * When a visit is confirmed, run place matching -> if no existing place matches,
     * create a new place and geocode it immediately so the UI shows a real name.
     * During accumulation, also attempt to link if the visit is still unassigned.
     */
    suspend fun handleVisitResult(result: VisitDetectionResult, sample: RawSample, dayKey: String? = null) {
        when (result) {
            is VisitDetectionResult.Confirmed -> {
                logger.d("PlaceLinkingService", "Visit confirmed: id=${result.visitId}")
                val placeMatch = matchPlaceLiveUseCase.matchSample(sample.lat, sample.lng)
                if (placeMatch.matchedPlace != null) {
                    linkVisitToPlace(result.visitId, placeMatch.matchedPlace.placeId)
                } else {
                    val candidate = timelineStateStore.getState().pendingVisitCandidate
                    val radiusM = candidate?.maxDistanceFromCentroidM
                        ?.coerceIn(40.0, 200.0)?.toFloat() ?: 60f
                    // Use visit centroid (average of all dwell samples) for place location,
                    // not the latest sample which may be at the edge of the dwell area.
                    val placeLat = candidate?.centroidLat ?: sample.lat
                    val placeLng = candidate?.centroidLng ?: sample.lng
                    createAndLinkPlace(result.visitId, placeLat, placeLng, radiusM)
                }
            }
            is VisitDetectionResult.Accumulating -> {
                val placeMatch = matchPlaceLiveUseCase.matchSample(sample.lat, sample.lng)
                if (placeMatch.matchedPlace != null) {
                    val state = timelineStateStore.getState()
                    val confirmedVisitId = state.lastConfirmedVisitId
                    if (confirmedVisitId != null) {
                        val visit = pipelineGateway.getVisit(confirmedVisitId)
                        if (visit != null && visit.placeId == 0L) {
                            linkVisitToPlace(confirmedVisitId, placeMatch.matchedPlace.placeId)
                        }
                    }
                }
            }
            is VisitDetectionResult.Departed -> {
                if (dayKey != null) {
                    integrityRepairUseCase.repairDay(dayKey)
                }
            }
            else -> { /* no-op */ }
        }
    }

    private suspend fun linkVisitToPlace(visitId: Long, placeId: Long) {
        val visit = pipelineGateway.getVisit(visitId) ?: return
        pipelineGateway.setVisitPlace(visitId, placeId)
        // Stamp placeId on overlapping VISIT/DWELL segments within this visit's time window
        val segments = pipelineGateway.segmentsForDay(visit.dayKey)
        for (seg in segments) {
            if ((seg.segmentType == SegmentType.VISIT.name ||
                    seg.segmentType == SegmentType.DWELL.name) &&
                seg.placeId == null && seg.startAt >= visit.arrivalAt &&
                (visit.departureAt == null || seg.startAt <= visit.departureAt)
            ) {
                pipelineGateway.setSegmentPlace(seg.segmentId, placeId)
            }
        }
        logger.d("PlaceLinkingService", "Linked visit $visitId to place $placeId")

        // Capture Wi-Fi fingerprint at CONFIRMED places
        val place = pipelineGateway.getPlace(placeId)
        if (place != null && place.lifecycleStatus == PlaceLifecycleStatus.CONFIRMED.name) {
            wifiFingerprinter.captureFingerprint(placeId)
        }

        autoPromotePlaceIfEligible(placeId)
    }

    private suspend fun autoPromotePlaceIfEligible(placeId: Long) {
        val place = pipelineGateway.getPlace(placeId) ?: return
        if (place.lifecycleStatus != PlaceLifecycleStatus.CANDIDATE.name) return
        val visitCount = pipelineGateway.countVisitsForPlace(placeId)
        if (visitCount >= AUTO_PROMOTE_VISIT_THRESHOLD) {
            pipelineGateway.setPlaceLifecycle(
                placeId = placeId,
                lifecycleStatus = PlaceLifecycleStatus.CONFIRMED.name,
                confidence = maxOf(place.confidence, 0.8f)
            )
            logger.d("PlaceLinkingService", "Auto-promoted place $placeId to CONFIRMED ($visitCount visits)")
            placeGeofenceManager.syncGeofences()
        }
    }

    private suspend fun createAndLinkPlace(visitId: Long, lat: Double, lng: Double, radiusM: Float = 60f) {
        val geohash = GeohashEncoder.encode(lat, lng)
        val nearbyPlaces = pipelineGateway.placesNearGeohash(geohash.take(5))
        val existingNearby = nearbyPlaces.firstOrNull { place ->
            LocationUtils.calculateDistance(lat, lng, place.centroidLat, place.centroidLng) <= 150.0
        }
        if (existingNearby != null) {
            linkVisitToPlace(visitId, existingNearby.placeId)
            pipelineGateway.touchPlaceVisited(existingNearby.placeId, System.currentTimeMillis())
            logger.d("PlaceLinkingService", "Reused existing place ${existingNearby.placeId} for visit $visitId")
            return
        }

        val now = System.currentTimeMillis()
        val placeId = pipelineGateway.createCandidatePlace(
            PlaceDraft(
                centroidLat = lat,
                centroidLng = lng,
                radiusM = radiusM,
                geohash = geohash,
                confidence = 0.5f,
                lifecycleStatus = PlaceLifecycleStatus.CANDIDATE.name,
                createdAt = now,
                lastVisitedAt = now
            )
        )
        linkVisitToPlace(visitId, placeId)
        logger.d("PlaceLinkingService", "Created place $placeId for visit $visitId, geocoding async")

        geocodeScope.launch(Dispatchers.IO) {
            try {
                // Skip the network lookup when auto-geocoding is off — the place
                // keeps its coordinate name. Respects the user's privacy lever.
                if (!settingsRepository.observeSettings().value.autoGeocodeNewPlaces) {
                    return@launch
                }
                // Use refreshGeocodeForPlace which stores ALL provider candidates
                // in geocode_candidates table, not just the best result.
                geocodingRepository.refreshGeocodeForPlace(placeId)
            } catch (e: Exception) {
                logger.e("PlaceLinkingService", "Async geocode failed for place $placeId", e)
            }
        }
    }
}
