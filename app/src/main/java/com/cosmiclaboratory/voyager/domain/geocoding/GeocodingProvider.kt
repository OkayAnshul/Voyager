package com.cosmiclaboratory.voyager.domain.geocoding

import com.cosmiclaboratory.voyager.domain.model.ProviderGeoResult
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId

/**
 * Common interface for all geocoding providers in the multi-provider stack.
 *
 * Each provider wraps a geocoding service (Android Geocoder, Photon, Nominatim)
 * and returns structured results that can be scored and ranked by the conflict resolver.
 */
interface GeocodingProvider {
    /** Enum identifier for this provider */
    val providerId: GeocodingProviderId

    /** Priority order (lower = tried first). Android=1, Photon=2, Nominatim=3 */
    val priority: Int

    /** Whether this provider is currently usable (e.g., Geocoder present, network available) */
    val isAvailable: Boolean

    /** Reverse geocode coordinates to a structured result */
    suspend fun reverseGeocode(lat: Double, lng: Double): Result<ProviderGeoResult>

    /** Forward geocode a query string to a list of candidate results */
    suspend fun forwardGeocode(query: String): Result<List<ProviderGeoResult>>
}
