package com.cosmiclaboratory.voyager.data.geocoding

import android.util.Log
import com.cosmiclaboratory.voyager.data.api.RateLimiter
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.model.ProviderGeoResult
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geocoding provider backed by OpenStreetMap Nominatim.
 *
 * Pros: high-quality structured addresses, good global coverage, sometimes has business names.
 * Cons: strict 1 req/sec rate limit (OSM usage policy), requires network.
 *
 * Rate limit is enforced via [RateLimiter] to comply with OSM Nominatim usage policy.
 */
@Singleton
class NominatimGeocodingProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : GeocodingProvider {

    override val providerId = GeocodingProviderId.NOMINATIM
    override val priority = 3
    override val isAvailable: Boolean get() = true

    private val rateLimiter = RateLimiter(minIntervalMs = 1000L) // 1 req/sec for OSM compliance

    override suspend fun reverseGeocode(lat: Double, lng: Double): Result<ProviderGeoResult> {
        return withContext(Dispatchers.IO) {
            try {
                rateLimiter.acquire()

                val url = "$BASE_URL/reverse?format=json&lat=$lat&lon=$lng&addressdetails=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ProviderHttpException("Nominatim", response.code)
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(NoResultException("Nominatim returned empty body"))

                parseReverseResult(body)
            } catch (e: Exception) {
                Log.w(TAG, "Nominatim reverseGeocode failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun forwardGeocode(query: String): Result<List<ProviderGeoResult>> {
        return withContext(Dispatchers.IO) {
            try {
                rateLimiter.acquire()

                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$BASE_URL/search?format=json&q=$encoded&addressdetails=1&limit=5"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ProviderHttpException("Nominatim", response.code)
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.success(emptyList())

                parseForwardResults(body)
            } catch (e: Exception) {
                Log.w(TAG, "Nominatim forwardGeocode failed", e)
                Result.failure(e)
            }
        }
    }

    // ---- Parsing ----

    private fun parseReverseResult(json: String): Result<ProviderGeoResult> {
        val root = JSONObject(json)

        if (root.has("error")) {
            return Result.failure(NoResultException("Nominatim error: ${root.optString("error")}"))
        }

        val addressObj = root.optJSONObject("address")
            ?: return Result.failure(NoResultException("Nominatim response missing address object"))

        val displayName = root.optString("display_name", "")
        if (displayName.isBlank()) {
            return Result.failure(NoResultException("Nominatim returned blank display_name"))
        }

        val structured = parseStructuredAddress(addressObj)

        return Result.success(
            ProviderGeoResult(
                displayName = displayName,
                structuredParts = structured,
                confidence = 0.85f,
                rawResponseJson = json
            )
        )
    }

    private fun parseForwardResults(json: String): Result<List<ProviderGeoResult>> {
        val array = org.json.JSONArray(json)
        val results = mutableListOf<ProviderGeoResult>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val displayName = item.optString("display_name", "").takeIf { it.isNotBlank() } ?: continue
            val addressObj = item.optJSONObject("address")
            val structured = addressObj?.let { parseStructuredAddress(it) }

            results.add(
                ProviderGeoResult(
                    displayName = displayName,
                    structuredParts = structured,
                    confidence = 0.8f,
                    rawResponseJson = item.toString()
                )
            )
        }

        return Result.success(results)
    }

    private fun parseStructuredAddress(address: JSONObject): StructuredAddress {
        return StructuredAddress(
            street = address.optStringOrNull("road"),
            houseNumber = address.optStringOrNull("house_number"),
            city = address.optStringOrNull("city")
                ?: address.optStringOrNull("town")
                ?: address.optStringOrNull("village"),
            state = address.optStringOrNull("state"),
            country = address.optStringOrNull("country_code")?.uppercase(),
            postalCode = address.optStringOrNull("postcode"),
            neighborhood = address.optStringOrNull("suburb")
                ?: address.optStringOrNull("neighbourhood")
                ?: address.optStringOrNull("quarter")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return optString(key, "").takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "NominatimGeocodingProvider"
        private const val USER_AGENT = "Voyager-Android/1.0 (Location Analytics App)"
        private const val BASE_URL = "https://nominatim.openstreetmap.org"
    }
}
