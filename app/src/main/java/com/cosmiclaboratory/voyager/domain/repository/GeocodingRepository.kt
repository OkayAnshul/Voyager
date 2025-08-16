package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.model.GeocodingResult
import com.cosmiclaboratory.voyager.domain.model.ProviderStatus

/**
 * Repository for geocoding operations.
 *
 * Multi-provider pipeline: tries providers in priority order (Android → Photon → Nominatim),
 * stores ALL candidates in geocode_candidates table, and uses the conflict resolver to pick
 * the best result.
 */
interface GeocodingRepository {

    /** Reverse geocode using all enabled providers, store candidates, return ranked result. */
    suspend fun reverseGeocode(lat: Double, lng: Double): GeocodingResult

    /** Forward geocode a query string using all enabled providers. */
    suspend fun forwardGeocode(query: String): GeocodingResult

    /** Resolve the display name for a place using the conflict resolver chain. */
    suspend fun resolveDisplayName(placeId: Long): String

    /** Re-fetch geocoding from all providers for an existing place. */
    suspend fun refreshGeocodeForPlace(placeId: Long): Result<Unit>

    /** Get all stored geocode candidates for a place. */
    suspend fun getCandidatesForPlace(placeId: Long): List<GeocodeCandidate>

    /** Get status of all registered providers. */
    suspend fun getProviderStatus(): List<ProviderStatus>
}
