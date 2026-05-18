package com.cosmiclaboratory.voyager.data.geocoding

import android.util.Log
import com.cosmiclaboratory.voyager.data.api.OverpassApiService
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.model.ProviderGeoResult
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geocoding provider backed by OpenStreetMap's Overpass API.
 *
 * Unlike the reverse geocoders (Android Geocoder, Photon, Nominatim) — which return
 * postal *addresses* — Overpass returns the *name* of the nearest point of interest
 * ("Blue Bottle Coffee", not "123 Main St"). It is tried first so a real place name,
 * when one exists nearby, can win over a bare address.
 *
 * It is reverse-only: a place is named from its coordinates. Forward geocoding (text
 * search) is left to the address providers. When there is no POI nearby — a home in a
 * residential street — it simply returns no result and the address providers take over.
 */
@Singleton
class OverpassGeocodingProvider @Inject constructor(
    private val overpassApiService: OverpassApiService
) : GeocodingProvider {

    override val providerId = GeocodingProviderId.OVERPASS

    /** Tried first: a nearby POI name should be able to out-rank a plain address. */
    override val priority = 0

    override val isAvailable: Boolean get() = true

    override suspend fun reverseGeocode(lat: Double, lng: Double): Result<ProviderGeoResult> {
        return try {
            val poi = overpassApiService.findNearbyPoi(lat, lng, SEARCH_RADIUS_M)
                ?: return Result.failure(NoResultException("No named POI within ${SEARCH_RADIUS_M}m"))

            Result.success(
                ProviderGeoResult(
                    displayName = poi.name,
                    // A POI carries a name, not a postal address — no structured parts.
                    structuredParts = null,
                    confidence = confidenceForDistance(poi.distance)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Overpass reverseGeocode failed", e)
            Result.failure(e)
        }
    }

    /** Overpass is a POI lookup, not a text search — forward geocoding is unsupported. */
    override suspend fun forwardGeocode(query: String): Result<List<ProviderGeoResult>> =
        Result.success(emptyList())

    /**
     * Confidence falls off with distance: a POI right on the centroid is almost
     * certainly the place; one near the edge of the search radius is a weak guess and
     * must not out-rank a precise address.
     */
    private fun confidenceForDistance(distanceM: Double): Float {
        val fraction = (distanceM / SEARCH_RADIUS_M).coerceIn(0.0, 1.0)
        return (MAX_CONFIDENCE - fraction * (MAX_CONFIDENCE - MIN_CONFIDENCE)).toFloat()
    }

    private companion object {
        const val TAG = "OverpassGeocodingProvider"
        const val SEARCH_RADIUS_M = 50
        const val MAX_CONFIDENCE = 0.92
        const val MIN_CONFIDENCE = 0.50
    }
}
