package com.cosmiclaboratory.voyager.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenStreetMap Nominatim geocoding service implementation
 *
 * Advantages:
 * - FREE - no API key required
 * - Better quality than Android Geocoder
 * - Sometimes includes business names
 * - Open-source, privacy-friendly
 *
 * Limitations:
 * - Rate limited (1 request per second)
 * - Requires internet connection
 * - Usage policy requires User-Agent header
 *
 * API Documentation: https://nominatim.openstreetmap.org/reverse
 * Usage Policy: https://operations.osmfoundation.org/policies/nominatim/
 */
@Singleton
class NominatimGeocodingService @Inject constructor(
    private val okHttpClient: OkHttpClient
) : GeocodingService {

    private val baseUrl = "https://nominatim.openstreetmap.org"
    private val rateLimiter = RateLimiter(minIntervalMs = 1000) // 1 req/sec for OSM compliance

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): AddressResult? = withContext(Dispatchers.IO) {
        try {
            // Enforce rate limiting + per-request jitter
            rateLimiter.acquire()
            applyJitter()

            val url = "$baseUrl/reverse?format=json&lat=$latitude&lon=$longitude&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Voyager-Android/1.0 (Location Analytics App)")
                .build()

            Log.d(TAG, "Nominatim reverse geocode: $latitude, $longitude")

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Nominatim request failed: ${response.code}")
                return@withContext null
            }

            if (!isJsonResponse(response)) {
                Log.w(TAG, "Nominatim returned non-JSON (captive portal?) — skipping")
                return@withContext null
            }

            val jsonString = response.body?.string()
            if (jsonString.isNullOrBlank()) {
                Log.w(TAG, "Nominatim returned empty response")
                return@withContext null
            }

            val json = JSONObject(jsonString)

            // Check if we got a valid result
            if (json.has("error")) {
                Log.w(TAG, "Nominatim error: ${json.optString("error")}")
                return@withContext null
            }

            val address = json.optJSONObject("address")
            if (address == null) {
                Log.w(TAG, "Nominatim response missing address object")
                return@withContext null
            }

            AddressResult(
                formattedAddress = json.optString("display_name", ""),
                streetName = address.optString("road", null).takeIf { !it.isNullOrBlank() },
                locality = address.optString("city", null)
                    ?: address.optString("town", null)
                    ?: address.optString("village", null),
                subLocality = address.optString("suburb", null)
                    ?: address.optString("neighbourhood", null)
                    ?: address.optString("quarter", null),
                postalCode = address.optString("postcode", null).takeIf { !it.isNullOrBlank() },
                countryCode = address.optString("country_code", null)?.uppercase()
            ).also {
                Log.d(TAG, "Nominatim result: ${it.formattedAddress}")
            }

        } catch (e: Exception) {
            // Coordinates omitted — never write the user's location to logcat.
            Log.e(TAG, "Nominatim reverse geocode failed", e)
            null
        }
    }

    override suspend fun getPlaceDetails(
        latitude: Double,
        longitude: Double
    ): PlaceDetails? = withContext(Dispatchers.IO) {
        try {
            // Enforce rate limiting + per-request jitter
            rateLimiter.acquire()
            applyJitter()

            // Use zoom=18 for more specific results (building level)
            val url = "$baseUrl/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Voyager-Android/1.0 (Location Analytics App)")
                .build()

            Log.d(TAG, "Nominatim place details: $latitude, $longitude")

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Nominatim place details request failed: ${response.code}")
                return@withContext null
            }

            if (!isJsonResponse(response)) {
                Log.w(TAG, "Nominatim returned non-JSON (captive portal?) — skipping")
                return@withContext null
            }

            val jsonString = response.body?.string()
            if (jsonString.isNullOrBlank()) {
                return@withContext null
            }

            val json = JSONObject(jsonString)

            if (json.has("error")) {
                Log.w(TAG, "Nominatim place details error: ${json.optString("error")}")
                return@withContext null
            }

            val name = json.optString("name", null).takeIf { !it.isNullOrBlank() }
            val type = json.optString("type", null).takeIf { !it.isNullOrBlank() }
            val category = json.optString("category", null).takeIf { !it.isNullOrBlank() }
            val displayName = json.optString("display_name", null)

            // Nominatim sometimes provides business/place names
            if (name != null || type != null) {
                PlaceDetails(
                    name = name,
                    type = type ?: category,
                    formattedAddress = displayName
                ).also {
                    Log.d(TAG, "Nominatim place details result: name=${it.name}, type=${it.type}")
                }
            } else {
                Log.d(TAG, "Nominatim place details: no name or type found")
                null
            }

        } catch (e: Exception) {
            // Coordinates omitted — never write the user's location to logcat.
            Log.e(TAG, "Nominatim place details failed", e)
            null
        }
    }

    override suspend fun isAvailable(): Boolean {
        // Nominatim requires internet connection
        // In a production app, you might check network connectivity here
        return true
    }

    /**
     * Guards against captive portals (hotel/airport WiFi) that return an HTML login
     * page with HTTP 200 for every request. Parsing that HTML as JSON would either
     * throw (caught silently) or, worse, succeed enough to poison a place name.
     */
    private fun isJsonResponse(response: Response): Boolean {
        val contentType = response.header("Content-Type") ?: return false
        return contentType.contains("json", ignoreCase = true)
    }

    /**
     * Random 0–3s delay before each request. Many privacy-conscious users share a VPN
     * exit IP; without jitter they collectively hammer Nominatim's per-IP rate limit
     * in lockstep and get the whole exit IP banned.
     */
    private suspend fun applyJitter() {
        delay(Random.nextLong(0, JITTER_MAX_MS))
    }

    companion object {
        private const val TAG = "NominatimGeocodingService"
        private const val JITTER_MAX_MS = 3000L
    }
}
