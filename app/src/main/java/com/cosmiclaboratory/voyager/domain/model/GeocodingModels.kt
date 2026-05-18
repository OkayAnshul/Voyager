package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.model.enums.LicenseClass

data class GeocodingResult(
    val candidates: List<GeocodeCandidate>,
    val bestCandidate: GeocodeCandidate?
)

data class GeocodeCandidate(
    val provider: GeocodingProviderId,
    val rank: Int,
    val displayName: String,
    val structuredParts: StructuredAddress?,
    val confidence: Float,
    val licenseClass: LicenseClass,
    val fetchedAt: Long,
    /**
     * Accuracy-gated name safe to show the user — coarsened when confidence is low so
     * a wrong house number/street is never presented as fact. Computed by
     * [com.cosmiclaboratory.voyager.domain.geocoding.GeocodingConflictResolver].
     */
    val safeDisplayName: String? = null
)

/**
 * How much the resolved name can be trusted — drives both the accuracy gate
 * (how precise a name to show) and the sequential short-circuit (HIGH stops the stack).
 */
enum class ConfidenceTier { HIGH, MEDIUM, LOW, NONE }

data class StructuredAddress(
    val street: String? = null,
    val houseNumber: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    val neighborhood: String? = null
)

data class ProviderStatus(
    val providerId: GeocodingProviderId,
    val isAvailable: Boolean,
    val lastSuccessAt: Long?,
    val errorCount: Int,
    val rateLimitRemaining: Int?
)

interface GeocodingProvider {
    val providerId: GeocodingProviderId
    val priority: Int
    val licenseClass: LicenseClass
    val rateLimitPerMinute: Int
    suspend fun reverseGeocode(lat: Double, lng: Double): ProviderGeoResult?
    fun isAvailable(): Boolean
}

data class ProviderGeoResult(
    val displayName: String,
    val structuredParts: StructuredAddress?,
    val confidence: Float,
    val rawResponseJson: String? = null
)
