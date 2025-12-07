package com.cosmiclaboratory.voyager.data.api

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Geocoder service implementation
 *
 * Advantages:
 * - FREE - no API key required
 * - Built-in Android API
 * - No rate limiting
 * - Works offline (cached results)
 *
 * Limitations:
 * - Basic addresses only (no business names)
 * - Quality varies by region
 * - May return null in some areas
 */
@Singleton
class AndroidGeocoderService @Inject constructor(
    @ApplicationContext private val context: Context
) : GeocodingService {

    private val geocoder: Geocoder? = if (Geocoder.isPresent()) {
        Geocoder(context, Locale.getDefault())
    } else {
        null
    }

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): AddressResult? = withContext(Dispatchers.IO) {
        try {
            if (geocoder == null) {
                Log.w(TAG, "Geocoder not present on this device")
                return@withContext null
            }

            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses.isNullOrEmpty()) {
                Log.d(TAG, "No address found for coordinates: $latitude, $longitude")
                return@withContext null
            }

            val address = addresses[0]

            AddressResult(
                formattedAddress = formatAddress(address),
                streetName = address.thoroughfare,
                locality = address.locality ?: address.subAdminArea,
                subLocality = address.subLocality,
                postalCode = address.postalCode,
                countryCode = address.countryCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocode failed for $latitude, $longitude", e)
            null
        }
    }

    override suspend fun getPlaceDetails(
        latitude: Double,
        longitude: Double
    ): PlaceDetails? {
        // Android Geocoder doesn't provide business names or place types
        return null
    }

    override suspend fun isAvailable(): Boolean {
        return geocoder != null
    }

    /**
     * Format address from Android Address object
     * Combines available address components into readable string
     */
    private fun formatAddress(address: Address): String {
        val addressLine = address.getAddressLine(0)

        if (!addressLine.isNullOrBlank()) {
            return addressLine
        }

        // Fallback: Build address from components
        val components = mutableListOf<String>()

        address.thoroughfare?.let { components.add(it) }
        address.subLocality?.let { components.add(it) }
        address.locality?.let { components.add(it) }
        address.postalCode?.let { components.add(it) }
        address.countryName?.let { components.add(it) }

        return components.joinToString(", ").ifBlank { "Unknown Address" }
    }

    companion object {
        private const val TAG = "AndroidGeocoderService"
    }
}
