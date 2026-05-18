package com.cosmiclaboratory.voyager.data.api

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
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
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : GeocodingService {

    /**
     * Builds a [Geocoder] for the user's geocode-language setting (device default
     * when unset). Cheap to construct, so it is done per call to honour live
     * language changes.
     */
    private fun geocoderForCurrentLanguage(): Geocoder? {
        if (!Geocoder.isPresent()) return null
        val tag = try {
            settingsRepository.observeSettings().value.geocodeLanguage
        } catch (_: Exception) { "" }
        val locale = if (tag.isNotBlank()) Locale.forLanguageTag(tag) else Locale.getDefault()
        return Geocoder(context, locale)
    }

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): AddressResult? = withContext(Dispatchers.IO) {
        try {
            val geocoder = geocoderForCurrentLanguage()
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
                houseNumber = address.subThoroughfare,
                locality = address.locality ?: address.subAdminArea,
                subLocality = address.subLocality,
                state = address.adminArea,
                postalCode = address.postalCode,
                countryCode = address.countryCode,
                landmarkHint = extractLandmarkHint(address)
            )
        } catch (e: Exception) {
            // Coordinates omitted — never write the user's location to logcat.
            Log.e(TAG, "Reverse geocode failed", e)
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
        return Geocoder.isPresent()
    }

    /**
     * Extracts a named landmark from [Address.featureName] / [Address.premises].
     *
     * Android frequently sets `featureName` to the bare house number, so the hint is
     * kept only when it is a real name: non-blank, not purely numeric, and not equal
     * to the house number. Returns null when there is no genuine landmark name.
     */
    private fun extractLandmarkHint(address: Address): String? {
        val candidate = address.featureName?.takeIf { it.isNotBlank() }
            ?: address.premises?.takeIf { it.isNotBlank() }
            ?: return null
        val isNumeric = candidate.all { it.isDigit() || it == '-' || it == ' ' }
        if (isNumeric) return null
        if (candidate.equals(address.subThoroughfare, ignoreCase = true)) return null
        return candidate
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
