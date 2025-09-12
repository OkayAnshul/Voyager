package com.cosmiclaboratory.voyager.data.geocoding

import android.util.Log
import com.cosmiclaboratory.voyager.data.api.AndroidGeocoderService
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.model.ProviderGeoResult
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.model.enums.LicenseClass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geocoding provider backed by [android.location.Geocoder].
 *
 * Pros: instant, offline-capable, no rate limit, no API key.
 * Cons: basic addresses only (no POI/business names), quality varies by region.
 */
@Singleton
class AndroidGeocodingProvider @Inject constructor(
    private val service: AndroidGeocoderService
) : GeocodingProvider {

    override val providerId = GeocodingProviderId.ANDROID_GEOCODER
    override val priority = 1

    // Cached at construction — Geocoder.isPresent() is a static check that doesn't change
    override val isAvailable: Boolean = android.location.Geocoder.isPresent()

    override suspend fun reverseGeocode(lat: Double, lng: Double): Result<ProviderGeoResult> {
        return try {
            val address = service.reverseGeocode(lat, lng)
                ?: return Result.failure(NoResultException("Android Geocoder returned null for $lat, $lng"))

            val structured = StructuredAddress(
                street = address.streetName,
                city = address.locality,
                neighborhood = address.subLocality,
                postalCode = address.postalCode,
                country = address.countryCode
            )

            Result.success(
                ProviderGeoResult(
                    displayName = address.formattedAddress,
                    structuredParts = structured,
                    confidence = 0.85f
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Android Geocoder reverseGeocode failed", e)
            Result.failure(e)
        }
    }

    override suspend fun forwardGeocode(query: String): Result<List<ProviderGeoResult>> {
        // Android Geocoder does not support a useful forward geocode for our use case
        return Result.success(emptyList())
    }

    companion object {
        private const val TAG = "AndroidGeocodingProvider"
    }
}
