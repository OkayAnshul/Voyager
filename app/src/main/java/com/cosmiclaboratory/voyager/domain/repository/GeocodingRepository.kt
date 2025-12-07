package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.data.api.AddressResult
import com.cosmiclaboratory.voyager.data.api.PlaceDetails

/**
 * Repository for geocoding operations
 *
 * Provides reverse geocoding with intelligent caching and fallback strategy:
 * 1. Check cache (instant, free)
 * 2. Try Android Geocoder (fast, free, basic addresses)
 * 3. Try OSM Nominatim (slower, free, better quality + business names)
 * 4. Return null if all fail
 */
interface GeocodingRepository {

    /**
     * Get address for coordinates with caching and fallback
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Address result or null if unavailable
     */
    suspend fun getAddressForCoordinates(latitude: Double, longitude: Double): AddressResult?

    /**
     * Get place details (business name, type) for coordinates
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Place details or null if unavailable
     */
    suspend fun getPlaceDetailsForCoordinates(latitude: Double, longitude: Double): PlaceDetails?

    /**
     * Clear expired cache entries
     * @param durationDays Number of days to keep cache (default 30)
     */
    suspend fun clearExpiredCache(durationDays: Int = 30)

    /**
     * Get cache statistics for monitoring
     * @return Total cached entries
     */
    suspend fun getCacheSize(): Int
}
