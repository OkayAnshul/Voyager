package com.cosmiclaboratory.voyager.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OSM Overpass API Service for querying nearby POIs with business names
 *
 * API: https://overpass-api.de/api/interpreter
 * Rate Limit: Respectful usage (max 1 req/sec via RateLimiter)
 *
 * Strategy:
 * 1. Query within 50m radius for nodes/ways with "name" tag
 * 2. Filter for common business types (amenity, shop, office, leisure)
 * 3. Return closest match with name
 *
 * Example Response:
 * {
 *   "elements": [
 *     {
 *       "type": "node",
 *       "id": 123456789,
 *       "lat": 12.9716,
 *       "lon": 77.5946,
 *       "tags": {
 *         "name": "Starbucks Coffee",
 *         "amenity": "cafe"
 *       }
 *     }
 *   ]
 * }
 */
interface OverpassApiService {
    /**
     * Find nearby POI with business name
     *
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param radiusMeters Search radius in meters (default 50m)
     * @return OverpassResult with name, type, distance or null if not found
     */
    suspend fun findNearbyPoi(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 50
    ): OverpassResult?
}

/**
 * Result from Overpass API query
 */
data class OverpassResult(
    val name: String,
    val type: String,  // e.g., "amenity=cafe", "shop=supermarket"
    val distance: Double,  // meters from query point
    val latitude: Double,
    val longitude: Double
)

/**
 * Overpass API implementation with error handling and rate limiting
 */
@Singleton
class OverpassApiServiceImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val rateLimiter: RateLimiter
) : OverpassApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun findNearbyPoi(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): OverpassResult? {
        try {
            // Apply rate limiting (1 req/sec)
            rateLimiter.acquire()

            Log.d(TAG, "Querying Overpass API for POI near $latitude, $longitude (radius: ${radiusMeters}m)")

            // Build Overpass QL query
            val query = buildOverpassQuery(latitude, longitude, radiusMeters)

            // Execute request with 5 second timeout
            val response = withTimeout(5000L) {
                httpClient.post(OVERPASS_API_URL) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("data=$query")
                }
            }

            val responseBody = response.bodyAsText()

            if (!response.status.isSuccess()) {
                Log.w(TAG, "Overpass API returned error: ${response.status}")
                return null
            }

            // Parse response
            val overpassResponse = json.decodeFromString<OverpassResponse>(responseBody)

            if (overpassResponse.elements.isEmpty()) {
                Log.d(TAG, "No POIs found within ${radiusMeters}m")
                return null
            }

            // Find closest POI with a valid name
            val closestPoi = overpassResponse.elements
                .filter { it.tags?.name != null && !isGenericName(it.tags.name) }
                .minByOrNull { element ->
                    calculateDistance(latitude, longitude, element.lat, element.lon)
                }

            if (closestPoi == null) {
                Log.d(TAG, "No POIs with valid names found")
                return null
            }

            val distance = calculateDistance(latitude, longitude, closestPoi.lat, closestPoi.lon)
            val poiType = extractPoiType(closestPoi.tags!!)

            Log.i(TAG, "Found POI: ${closestPoi.tags.name} ($poiType) at ${distance.toInt()}m")

            return OverpassResult(
                name = closestPoi.tags.name,
                type = poiType,
                distance = distance,
                latitude = closestPoi.lat,
                longitude = closestPoi.lon
            )

        } catch (e: Exception) {
            Log.e(TAG, "Overpass API query failed", e)
            return null
        }
    }

    /**
     * Build Overpass QL query for nearby POIs
     */
    private fun buildOverpassQuery(lat: Double, lon: Double, radius: Int): String {
        return """
            [out:json][timeout:5];
            (
              node["name"](around:$radius,$lat,$lon);
              way["name"](around:$radius,$lat,$lon);
            );
            out body 1;
        """.trimIndent()
    }

    /**
     * Extract POI type from tags (prioritize business-relevant tags)
     */
    private fun extractPoiType(tags: Tags): String {
        return when {
            tags.amenity != null -> "amenity=${tags.amenity}"
            tags.shop != null -> "shop=${tags.shop}"
            tags.office != null -> "office=${tags.office}"
            tags.leisure != null -> "leisure=${tags.leisure}"
            tags.tourism != null -> "tourism=${tags.tourism}"
            tags.building != null -> "building=${tags.building}"
            else -> "unknown"
        }
    }

    /**
     * Check if name is generic/useless
     */
    private fun isGenericName(name: String): Boolean {
        val lowerName = name.lowercase()
        val genericTerms = setOf(
            "building", "place", "location", "unnamed", "null",
            "untitled", "unknown", "n/a", "none"
        )
        return genericTerms.any { lowerName.contains(it) }
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    companion object {
        private const val TAG = "OverpassApiService"
        private const val OVERPASS_API_URL = "https://overpass-api.de/api/interpreter"
    }
}

/**
 * Overpass API JSON response models
 */
@Serializable
private data class OverpassResponse(
    val elements: List<Element>
)

@Serializable
private data class Element(
    val type: String,
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Tags? = null
)

@Serializable
private data class Tags(
    val name: String,
    val amenity: String? = null,
    val shop: String? = null,
    val office: String? = null,
    val leisure: String? = null,
    val tourism: String? = null,
    val building: String? = null
)
