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

    // ---- Display-name resolution chain ----

    @Test
    fun `resolveDisplayName prefers the user name above all`() {
        assertThat(
            resolver.resolveDisplayName(
                userDisplayName = "Home", userCategory = "WORK",
                bestProviderName = "123 Main St", nearbyContext = "Downtown",
                semanticLabel = "Frequent stop", lat = 0.0, lng = 0.0
            )
        ).isEqualTo("Home")
    }

    @Test
    fun `resolveDisplayName uses the gated provider name over nearby context`() {
        assertThat(
            resolver.resolveDisplayName(
                userDisplayName = null, userCategory = null,
                bestProviderName = "123 Main St", nearbyContext = "Downtown",
                semanticLabel = null, lat = 0.0, lng = 0.0
            )
        ).isEqualTo("123 Main St")
    }

    @Test
    fun `resolveDisplayName falls back to Near-context when there is no provider name`() {
        // The bug this fixes: nearbyContext was declared but ignored, so an unnamed
        // place fell straight through to coordinates.
        assertThat(
            resolver.resolveDisplayName(
                userDisplayName = null, userCategory = null,
                bestProviderName = null, nearbyContext = "Patia, Bhubaneswar",
                semanticLabel = null, lat = 20.29, lng = 85.82
            )
        ).isEqualTo("Near Patia, Bhubaneswar")
    }

    @Test
    fun `resolveDisplayName falls back to coordinates only when nothing is known`() {
        assertThat(
            resolver.resolveDisplayName(
                userDisplayName = null, userCategory = null,
                bestProviderName = null, nearbyContext = null,
                semanticLabel = null, lat = 20.2961, lng = 85.8245
            )
        ).isEqualTo("20.2961, 85.8245")
    }

    // ---- Nearby-context builder ----

    @Test
    fun `nearbyContext prefers neighborhood, then street+city, then city`() {
        val neighborhood = candidate(
            GeocodingProviderId.PHOTON, 0.5f,
            structured = StructuredAddress(neighborhood = "Marylebone", city = "London")
        )
        assertThat(resolver.nearbyContext(listOf(neighborhood))).isEqualTo("Marylebone")

        val streetCity = candidate(
            GeocodingProviderId.PHOTON, 0.5f,
            structured = StructuredAddress(street = "Baker Street", city = "London")
        )
        assertThat(resolver.nearbyContext(listOf(streetCity))).isEqualTo("Baker Street, London")

        val cityOnly = candidate(
            GeocodingProviderId.PHOTON, 0.5f,
            structured = StructuredAddress(city = "London")
        )
        assertThat(resolver.nearbyContext(listOf(cityOnly))).isEqualTo("London")

        assertThat(resolver.nearbyContext(emptyList())).isNull()
    }

    @Test
    fun `coordinatePlaceholder matches the coordinate fallback format`() {
        assertThat(resolver.coordinatePlaceholder(20.2961, 85.8245)).isEqualTo("20.2961, 85.8245")
    }
}
