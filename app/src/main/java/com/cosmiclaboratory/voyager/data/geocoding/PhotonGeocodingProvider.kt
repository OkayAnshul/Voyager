package com.cosmiclaboratory.voyager.data.geocoding

import android.util.Log
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.model.ProviderGeoResult
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.data.api.RateLimiter
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geocoding provider backed by the Photon API (komoot.io or self-hosted).
 *
 * Pros: fast, no strict rate limit, good POI coverage, GeoJSON output.
 * Cons: requires network, fewer structured address fields than Nominatim.
 *
 * The base URL is configurable via [UserSettings.photonServerUrl].
 */
@Singleton
class PhotonGeocodingProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : GeocodingProvider {

    override val providerId = GeocodingProviderId.PHOTON
    override val priority = 2
    override val isAvailable: Boolean get() = true

    // Public komoot Photon has no published hard limit — throttle conservatively
    // (matching Nominatim) so the shared instance is not hammered.
    private val rateLimiter = RateLimiter(minIntervalMs = 1000L)

    private suspend fun getBaseUrl(): String = try {
        settingsRepository.observeSettings().value.photonServerUrl
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read photonServerUrl from settings, using default", e)
        DEFAULT_BASE_URL
    }

    /** `&lang=` fragment from the user's geocode-language setting, or "". */
    private fun languageParam(): String {
        val lang = try {
            settingsRepository.observeSettings().value.geocodeLanguage
        } catch (_: Exception) { "" }
        return if (lang.isNotBlank()) "&lang=$lang" else ""
    }

    override suspend fun reverseGeocode(lat: Double, lng: Double): Result<ProviderGeoResult> {
        return withContext(Dispatchers.IO) {
            try {
                rateLimiter.acquire()
                val url = "${getBaseUrl()}/reverse?lat=$lat&lon=$lng&limit=1${languageParam()}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            ProviderHttpException("Photon", resp.code)
                        )
                    }

                    val body = resp.body?.string()
                        ?: return@withContext Result.failure(NoResultException("Photon returned empty body"))

                    parseReverseResult(body)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Photon reverseGeocode failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun forwardGeocode(query: String): Result<List<ProviderGeoResult>> {
        return withContext(Dispatchers.IO) {
            try {
                rateLimiter.acquire()
                val url = "${getBaseUrl()}/api?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=5${languageParam()}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            ProviderHttpException("Photon", resp.code)
                        )
                    }

                    val body = resp.body?.string()
                        ?: return@withContext Result.success(emptyList())

                    parseForwardResults(body)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Photon forwardGeocode failed", e)
                Result.failure(e)
            }
        }
    }

    // ---- Parsing ----

    private fun parseReverseResult(geoJson: String): Result<ProviderGeoResult> {
        val root = JSONObject(geoJson)
        val features = root.optJSONArray("features")
        if (features == null || features.length() == 0) {
            return Result.failure(NoResultException("Photon returned no features"))
        }

        val props = features.getJSONObject(0).optJSONObject("properties")
            ?: return Result.failure(NoResultException("Photon feature missing properties"))

        return Result.success(propsToResult(props))
    }

    private fun parseForwardResults(geoJson: String): Result<List<ProviderGeoResult>> {
        val root = JSONObject(geoJson)
        val features = root.optJSONArray("features") ?: return Result.success(emptyList())

        val results = mutableListOf<ProviderGeoResult>()
        for (i in 0 until features.length()) {
            val props = features.getJSONObject(i).optJSONObject("properties") ?: continue
            results.add(propsToResult(props))
        }
        return Result.success(results)
    }

    private fun propsToResult(props: JSONObject): ProviderGeoResult {
        val name = props.optString("name", "").takeIf { it.isNotBlank() }
        val street = props.optString("street", "").takeIf { it.isNotBlank() }
        val houseNumber = props.optString("housenumber", "").takeIf { it.isNotBlank() }
        val city = props.optString("city", "").takeIf { it.isNotBlank() }
            ?: props.optString("county", "").takeIf { it.isNotBlank() }
        val state = props.optString("state", "").takeIf { it.isNotBlank() }
        val country = props.optString("country", "").takeIf { it.isNotBlank() }
        val postcode = props.optString("postcode", "").takeIf { it.isNotBlank() }
        val district = props.optString("district", "").takeIf { it.isNotBlank() }
        val countryCode = props.optString("countrycode", "").takeIf { it.isNotBlank() }?.uppercase()

        // Detect POI results via osm_key — POIs should not have their name as the display name
        val osmKey = props.optString("osm_key", "").lowercase()
        val poiKeys = setOf("amenity", "shop", "tourism", "leisure", "office", "craft", "healthcare")
        val isPoi = osmKey in poiKeys || (name != null && street != null && name != street)

        val displayParts = if (isPoi) {
            // POI result: use street address only, exclude POI name
            val addressPart = if (houseNumber != null && street != null) "$houseNumber $street"
                else street
            listOfNotNull(addressPart, city, countryCode)
        } else {
            listOfNotNull(name, street, city, countryCode)
        }
        val displayName = displayParts.joinToString(", ").ifBlank { "Unknown" }

        return ProviderGeoResult(
            displayName = displayName,
            structuredParts = StructuredAddress(
                street = street,
                houseNumber = houseNumber,
                city = city,
                state = state,
                country = country ?: countryCode,
                postalCode = postcode,
                neighborhood = district
            ),
            confidence = if (isPoi) 0.55f else 0.75f
        )
    }

    companion object {
        private const val TAG = "PhotonGeocodingProvider"
        private const val USER_AGENT = "Voyager-Android/1.0 (Location Analytics App)"
        private const val DEFAULT_BASE_URL = "https://photon.komoot.io"
    }
}
