package com.cosmiclaboratory.voyager.domain.geocoding

import com.cosmiclaboratory.voyager.domain.model.ConfidenceTier
import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.model.enums.LicenseClass
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the accuracy gate in [GeocodingConflictResolver] — the confidence
 * tiers and the coarsened safe display name that keeps a wrong house number from
 * ever being shown as fact.
 */
class GeocodingConflictResolverTest {

    private val resolver = GeocodingConflictResolver()

    private fun candidate(
        provider: GeocodingProviderId,
        confidence: Float,
        displayName: String = "Result",
        structured: StructuredAddress? = null,
    ) = GeocodeCandidate(
        provider = provider,
        rank = 1,
        displayName = displayName,
        structuredParts = structured,
        confidence = confidence,
        licenseClass = LicenseClass.FREE,
        fetchedAt = 0L
    )

    private val fullAddress = StructuredAddress(
        street = "Baker Street",
        houseNumber = "221B",
        city = "London",
        neighborhood = "Marylebone"
    )

    @Test
    fun `normalized confidence scales per provider`() {
        assertThat(resolver.normalizedConfidence(candidate(GeocodingProviderId.ANDROID_GEOCODER, 0.85f)))
            .isWithin(0.001f).of(0.85f)
        assertThat(resolver.normalizedConfidence(candidate(GeocodingProviderId.PHOTON, 0.80f)))
            .isWithin(0.001f).of(0.76f)
        assertThat(resolver.normalizedConfidence(candidate(GeocodingProviderId.OVERPASS, 0.92f)))
            .isWithin(0.001f).of(0.92f)
    }

    @Test
    fun `confidence tiers map from normalized confidence`() {
        val high = candidate(GeocodingProviderId.ANDROID_GEOCODER, 0.85f, structured = fullAddress)
        val medium = candidate(GeocodingProviderId.PHOTON, 0.75f, structured = fullAddress)
        val low = candidate(GeocodingProviderId.PHOTON, 0.55f, structured = fullAddress)
        val none = candidate(GeocodingProviderId.ANDROID_GEOCODER, 0.30f, structured = fullAddress)

        assertThat(resolver.confidenceTier(high, listOf(high))).isEqualTo(ConfidenceTier.HIGH)
        assertThat(resolver.confidenceTier(medium, listOf(medium))).isEqualTo(ConfidenceTier.MEDIUM)
        assertThat(resolver.confidenceTier(low, listOf(low))).isEqualTo(ConfidenceTier.LOW)
        assertThat(resolver.confidenceTier(none, listOf(none))).isEqualTo(ConfidenceTier.NONE)
    }

    @Test
    fun `HIGH shows the full address unchanged`() {
        val best = candidate(
            GeocodingProviderId.ANDROID_GEOCODER, 0.85f,
            displayName = "221B Baker Street, London", structured = fullAddress
        )
        assertThat(resolver.safeDisplayName(best, listOf(best), 51.52, -0.15))
            .isEqualTo("221B Baker Street, London")
    }

    @Test
    fun `MEDIUM drops the house number`() {
        val best = candidate(
            GeocodingProviderId.PHOTON, 0.75f,
            displayName = "221B Baker Street, London", structured = fullAddress
        )
        // Street + city, no house number.
        assertThat(resolver.safeDisplayName(best, listOf(best), 51.52, -0.15))
            .isEqualTo("Baker Street, London")
    }

    @Test
    fun `LOW coarsens to the neighbourhood or city`() {
        val best = candidate(
            GeocodingProviderId.PHOTON, 0.55f,
            displayName = "221B Baker Street, London", structured = fullAddress
        )
        assertThat(resolver.safeDisplayName(best, listOf(best), 51.52, -0.15))
            .isEqualTo("Marylebone")
    }

    @Test
    fun `NONE falls back to coordinates`() {
        val best = candidate(
            GeocodingProviderId.ANDROID_GEOCODER, 0.30f,
            displayName = "221B Baker Street", structured = fullAddress
        )
        assertThat(resolver.safeDisplayName(best, listOf(best), 51.52, -0.15))
            .isEqualTo("51.5200, -0.1500")
    }

    @Test
    fun `an Overpass POI name is shown as-is with no house number`() {
        // POI: high confidence, no structured address parts.
        val best = candidate(
            GeocodingProviderId.OVERPASS, 0.90f, displayName = "Blue Bottle Coffee"
        )
        assertThat(resolver.confidenceTier(best, listOf(best))).isEqualTo(ConfidenceTier.HIGH)
        assertThat(resolver.safeDisplayName(best, listOf(best), 37.0, -122.0))
            .isEqualTo("Blue Bottle Coffee")
    }

    @Test
    fun `agreement between providers on city and street raises the tier`() {
        // A lone LOW result...
        val low = candidate(GeocodingProviderId.PHOTON, 0.55f, structured = fullAddress)
        assertThat(resolver.confidenceTier(low, listOf(low))).isEqualTo(ConfidenceTier.LOW)

        // ...but a second provider independently agrees on city + street → bumped up.
        val corroborating = candidate(GeocodingProviderId.NOMINATIM, 0.55f, structured = fullAddress)
        assertThat(resolver.confidenceTier(low, listOf(low, corroborating)))
            .isEqualTo(ConfidenceTier.MEDIUM)
    }
}
