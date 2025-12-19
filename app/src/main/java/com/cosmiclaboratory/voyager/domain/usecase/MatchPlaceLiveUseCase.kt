package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import javax.inject.Inject

data class PlaceMatchResult(
    val matchedPlace: PlaceEntity?,
    val distanceM: Double?,
    val isWithinRadius: Boolean
)

class MatchPlaceLiveUseCase @Inject constructor(
    private val placeDao: PlaceDao
) {
    companion object {
        private const val HYSTERESIS_BUFFER_M = 30.0
        private const val SEARCH_RADIUS_M = 200.0
    }

    private var consecutiveInsideCount = 0
    private var consecutiveOutsideCount = 0
    private var currentMatchedPlaceId: Long? = null

    suspend fun matchSample(lat: Double, lng: Double): PlaceMatchResult {
        val geohash = GeohashEncoder.encode(lat, lng)
        val prefix = geohash.take(5) // ~5km search radius

        val nearbyPlaces = placeDao.getByGeohashPrefix(prefix)
        if (nearbyPlaces.isEmpty()) {
            resetHysteresis()
            return PlaceMatchResult(null, null, false)
        }

        var nearestPlace: PlaceEntity? = null
        var nearestDistance = Double.MAX_VALUE

        for (place in nearbyPlaces) {
            val dist = LocationUtils.calculateDistance(lat, lng, place.centroidLat, place.centroidLng)
            if (dist < nearestDistance) {
                nearestDistance = dist
                nearestPlace = place
            }
        }

        if (nearestPlace == null || nearestDistance > SEARCH_RADIUS_M) {
            handleOutside()
            return PlaceMatchResult(null, nearestDistance, false)
        }

        val isWithinRadius = nearestDistance <= nearestPlace.radiusM + HYSTERESIS_BUFFER_M

        if (isWithinRadius) {
            handleInside(nearestPlace.placeId)
        } else {
            handleOutside()
        }

        val isConfirmedMatch = currentMatchedPlaceId == nearestPlace.placeId
        return PlaceMatchResult(
            matchedPlace = if (isConfirmedMatch) nearestPlace else null,
            distanceM = nearestDistance,
            isWithinRadius = isWithinRadius
        )
    }

    // Note: Thread-safety relies on PipelineConsumer being @Singleton and processing
    // samples sequentially on a single coroutine. If that invariant changes, add @Synchronized.
    private fun handleInside(placeId: Long) {
        consecutiveOutsideCount = 0
        if (currentMatchedPlaceId == placeId) {
            consecutiveInsideCount++
        } else if (currentMatchedPlaceId == null) {
            // No current match — accumulate toward entry
            consecutiveInsideCount++
            if (consecutiveInsideCount >= 2) { // Entry hysteresis
                currentMatchedPlaceId = placeId
            }
        } else {
            // Different place — clear match so entry hysteresis can start fresh
            currentMatchedPlaceId = null
            consecutiveInsideCount = 1
        }
    }

    private fun handleOutside() {
        consecutiveInsideCount = 0
        consecutiveOutsideCount++
        if (consecutiveOutsideCount >= 3) { // Exit hysteresis
            currentMatchedPlaceId = null
        }
    }

    fun resetHysteresis() {
        consecutiveInsideCount = 0
        consecutiveOutsideCount = 0
        currentMatchedPlaceId = null
    }
}
