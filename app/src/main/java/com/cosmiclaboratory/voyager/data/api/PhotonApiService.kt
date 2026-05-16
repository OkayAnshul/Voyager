package com.cosmiclaboratory.voyager.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photon geocoding service (https://photon.komoot.io)
 *
 * Advantages over Nominatim:
 * - No strict rate limit
 * - Good POI name coverage
 * - GeoJSON output format
 * - Open-source, free, no API key required
 *
 * API: GET https://photon.komoot.io/reverse?lat={lat}&lon={lon}
 */
@Singleton
class PhotonApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val baseUrl = "https://photon.komoot.io"

    suspend fun reverseGeocode(latitude: Double, longitude: Double): AddressResult? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/reverse?lat=$latitude&lon=$longitude&limit=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Voyager-Android/1.0 (Location Analytics App)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Photon reverse geocode failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                parseAddressResult(body)
            } catch (e: Exception) {
                // Coordinates omitted — never write the user's location to logcat.
                Log.e(TAG, "Photon reverse geocode exception", e)
                null
            }
        }

    suspend fun getPlaceDetails(latitude: Double, longitude: Double): PlaceDetails? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/reverse?lat=$latitude&lon=$longitude&limit=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Voyager-Android/1.0 (Location Analytics App)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                parsePlaceDetails(body)
            } catch (e: Exception) {
                // Coordinates omitted — never write the user's location to logcat.
                Log.e(TAG, "Photon place details exception", e)
                null
            }
        }

    private fun parseAddressResult(geoJson: String): AddressResult? {
        return try {
            val root = JSONObject(geoJson)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null

            val props = features.getJSONObject(0).optJSONObject("properties") ?: return null

            val name = props.optString("name", "").takeIf { it.isNotBlank() }
            val street = props.optString("street", "").takeIf { it.isNotBlank() }
            val city = props.optString("city", "").takeIf { it.isNotBlank() }
                ?: props.optString("county", "").takeIf { it.isNotBlank() }
            val district = props.optString("district", "").takeIf { it.isNotBlank() }
            val postcode = props.optString("postcode", "").takeIf { it.isNotBlank() }
            val countryCode = props.optString("countrycode", "").takeIf { it.isNotBlank() }?.uppercase()

            val parts = listOfNotNull(name, street, city, countryCode)
            val formatted = parts.joinToString(", ").ifBlank { return null }

            AddressResult(
                formattedAddress = formatted,
                streetName = street,
                locality = city,
                subLocality = district,
                postalCode = postcode,
                countryCode = countryCode
            ).also { Log.d(TAG, "Photon address: $formatted") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Photon address response", e)
            null
        }
    }

    private fun parsePlaceDetails(geoJson: String): PlaceDetails? {
        return try {
            val root = JSONObject(geoJson)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null

            val props = features.getJSONObject(0).optJSONObject("properties") ?: return null

            val name = props.optString("name", "").takeIf { it.isNotBlank() } ?: return null
            val osmKey = props.optString("osm_key", "").takeIf { it.isNotBlank() }
            val osmValue = props.optString("osm_value", "").takeIf { it.isNotBlank() }

            PlaceDetails(
                name = name,
                type = osmValue,
                osmType = osmKey,
                osmValue = osmValue
            ).also { Log.d(TAG, "Photon POI: $name ($osmKey=$osmValue)") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Photon place details", e)
            null
        }
    }

    companion object {
        private const val TAG = "PhotonApiService"
    }
}
