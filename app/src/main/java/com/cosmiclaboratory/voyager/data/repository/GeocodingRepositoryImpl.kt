package com.cosmiclaboratory.voyager.data.repository

import android.util.Log
import com.cosmiclaboratory.voyager.data.api.AddressResult
import com.cosmiclaboratory.voyager.data.api.AndroidGeocoderService
import com.cosmiclaboratory.voyager.data.api.NominatimGeocodingService
import com.cosmiclaboratory.voyager.data.api.OverpassApiService
import com.cosmiclaboratory.voyager.data.api.PlaceDetails
import com.cosmiclaboratory.voyager.data.database.dao.GeocodingCacheDao
import com.cosmiclaboratory.voyager.data.database.entity.GeocodingCacheEntity
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of GeocodingRepository with intelligent caching and fallback
 *
 * Strategy:
 * 1. Check cache first (~100m precision, 30-day TTL)
 * 2. Try Android Geocoder (fast, free, offline-capable)
 * 3. Fallback to Nominatim (better quality, requires network)
 * 4. Cache successful results
 *
 * Performance:
 * - Cache hit: <1ms
 * - Cache miss + Android Geocoder: ~50-200ms
 * - Cache miss + Nominatim: ~200-500ms
 * - Expected cache hit rate: 90%+
 */
@Singleton
class GeocodingRepositoryImpl @Inject constructor(
    private val androidGeocoder: AndroidGeocoderService,
    private val nominatimGeocoder: NominatimGeocodingService,
    private val overpassService: OverpassApiService,
    private val geocodingCacheDao: GeocodingCacheDao
) : GeocodingRepository {

    private val cacheDurationDays = 30

    override suspend fun getAddressForCoordinates(
        latitude: Double,
        longitude: Double
    ): AddressResult? {
        // Round coordinates to ~100m precision for cache key
        val roundedLat = roundToDecimalPlaces(latitude, 3)
        val roundedLng = roundToDecimalPlaces(longitude, 3)

        Log.d(TAG, "Getting address for $latitude, $longitude (rounded: $roundedLat, $roundedLng)")

        // STEP 1: Check cache first
        val cached = geocodingCacheDao.getAddress(roundedLat, roundedLng)
        if (cached != null && !cached.isExpired(cacheDurationDays)) {
            Log.d(TAG, "Cache HIT for $roundedLat, $roundedLng")
            return cached.toAddressResult()
        }

        if (cached != null) {
            Log.d(TAG, "Cache EXPIRED for $roundedLat, $roundedLng")
        } else {
            Log.d(TAG, "Cache MISS for $roundedLat, $roundedLng")
        }

        // STEP 2: Try Android Geocoder (fast, free, offline-capable)
        Log.d(TAG, "Trying Android Geocoder...")
        val androidResult = try {
            androidGeocoder.reverseGeocode(latitude, longitude)
        } catch (e: Exception) {
            Log.w(TAG, "Android Geocoder failed", e)
            null
        }

        if (androidResult != null) {
            Log.d(TAG, "Android Geocoder SUCCESS: ${androidResult.formattedAddress}")
            // Cache the result
            cacheAddress(roundedLat, roundedLng, androidResult)
            return androidResult
        }

        Log.d(TAG, "Android Geocoder returned null, trying Nominatim...")

        // STEP 3: Fallback to Nominatim (better quality, requires network)
        val nominatimResult = try {
            nominatimGeocoder.reverseGeocode(latitude, longitude)
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim failed", e)
            null
        }

        if (nominatimResult != null) {
            Log.d(TAG, "Nominatim SUCCESS: ${nominatimResult.formattedAddress}")
            // Cache the result
            cacheAddress(roundedLat, roundedLng, nominatimResult)
            return nominatimResult
        }

        // STEP 4: All geocoding methods failed
        Log.w(TAG, "All geocoding methods failed for $latitude, $longitude")
        return null
    }

    override suspend fun getPlaceDetailsForCoordinates(
        latitude: Double,
        longitude: Double
    ): PlaceDetails? {
        Log.d(TAG, "Getting place details for $latitude, $longitude")

        // PRIORITY 1: Try Overpass API for business names (most accurate)
        val overpassResult = try {
            overpassService.findNearbyPoi(latitude, longitude, radiusMeters = 50)
        } catch (e: Exception) {
            Log.w(TAG, "Overpass API failed, trying Nominatim", e)
            null
        }

        if (overpassResult != null && !isGenericName(overpassResult.name)) {
            Log.i(TAG, "Overpass SUCCESS: ${overpassResult.name} (${overpassResult.type}) at ${overpassResult.distance.toInt()}m")
            return PlaceDetails(
                name = overpassResult.name,
                type = overpassResult.type,
                osmType = parseOsmType(overpassResult.type),
                osmValue = parseOsmValue(overpassResult.type)
            )
        }

        // PRIORITY 2: Fallback to Nominatim (sometimes has business names)
        return try {
            val details = nominatimGeocoder.getPlaceDetails(latitude, longitude)
            if (details != null) {
                Log.d(TAG, "Nominatim place details found: name=${details.name}, type=${details.type}")
            } else {
                Log.d(TAG, "No place details found from Nominatim")
            }
            details
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get place details from Nominatim", e)
            null
        }
    }

    /**
     * Check if name is generic/useless
     */
    private fun isGenericName(name: String): Boolean {
        val lowerName = name.lowercase()
        val generic = setOf("building", "place", "location", "unnamed", "null", "untitled", "unknown")
        return generic.any { lowerName.contains(it) }
    }

    /**
     * Parse OSM type from type string (e.g., "amenity=cafe" -> "amenity")
     */
    private fun parseOsmType(typeString: String): String? {
        return typeString.split("=").getOrNull(0)
    }

    /**
     * Parse OSM value from type string (e.g., "amenity=cafe" -> "cafe")
     */
    private fun parseOsmValue(typeString: String): String? {
        return typeString.split("=").getOrNull(1)
    }

    override suspend fun clearExpiredCache(durationDays: Int) {
        Log.d(TAG, "Clearing cache older than $durationDays days")
        geocodingCacheDao.clearExpiredCache(durationDays)
    }

    override suspend fun getCacheSize(): Int {
        return geocodingCacheDao.getCacheSize()
    }

    /**
     * Cache a geocoding result
     */
    private suspend fun cacheAddress(
        lat: Double,
        lng: Double,
        address: AddressResult
    ) {
        try {
            val cacheEntity = GeocodingCacheEntity.fromAddressResult(lat, lng, address)
            geocodingCacheDao.insertAddress(cacheEntity)
            Log.d(TAG, "Cached address for $lat, $lng")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache address", e)
        }
    }

    /**
     * Round coordinate to specified decimal places
     * @param value Coordinate value
     * @param places Number of decimal places (3 = ~111m precision at equator)
     */
    private fun roundToDecimalPlaces(value: Double, places: Int): Double {
        val multiplier = Math.pow(10.0, places.toDouble())
        return Math.round(value * multiplier) / multiplier
    }

    companion object {
        private const val TAG = "GeocodingRepository"
    }
}
