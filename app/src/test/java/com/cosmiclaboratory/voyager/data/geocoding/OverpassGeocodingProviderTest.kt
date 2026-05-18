package com.cosmiclaboratory.voyager.data.geocoding

import com.cosmiclaboratory.voyager.data.api.OverpassApiService
import com.cosmiclaboratory.voyager.data.api.OverpassResult
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [OverpassGeocodingProvider] — turning a nearby OSM POI into a
 * geocoding candidate, with distance-scaled confidence.
 */
class OverpassGeocodingProviderTest {

    private val overpassApi = mockk<OverpassApiService>()
    private val provider = OverpassGeocodingProvider(overpassApi)

    private fun poi(distance: Double) = OverpassResult(
        name = "Blue Bottle Coffee",
        type = "amenity=cafe",
        distance = distance,
        latitude = 37.77,
        longitude = -122.41
    )

    @Test
    fun `provider identity is Overpass, tried first`() {
        assertThat(provider.providerId).isEqualTo(GeocodingProviderId.OVERPASS)
        assertThat(provider.priority).isEqualTo(0)
    }

    @Test
    fun `a nearby POI becomes a high-confidence named result`() = runTest {
        coEvery { overpassApi.findNearbyPoi(any(), any(), any()) } returns poi(distance = 5.0)

        val result = provider.reverseGeocode(37.77, -122.41)

        assertThat(result.isSuccess).isTrue()
        val geo = result.getOrThrow()
        assertThat(geo.displayName).isEqualTo("Blue Bottle Coffee")
        assertThat(geo.structuredParts).isNull()
        assertThat(geo.confidence).isGreaterThan(0.85f)
    }

    @Test
    fun `a POI at the edge of the radius is low confidence`() = runTest {
        coEvery { overpassApi.findNearbyPoi(any(), any(), any()) } returns poi(distance = 50.0)

        val confidence = provider.reverseGeocode(37.77, -122.41).getOrThrow().confidence

        // Far POIs must not out-rank a precise address.
        assertThat(confidence).isWithin(0.01f).of(0.50f)
    }

    @Test
    fun `no nearby POI yields a failure so address providers take over`() = runTest {
        coEvery { overpassApi.findNearbyPoi(any(), any(), any()) } returns null

        assertThat(provider.reverseGeocode(37.77, -122.41).isFailure).isTrue()
    }

    @Test
    fun `a service exception is reported as a failure, not a crash`() = runTest {
        coEvery { overpassApi.findNearbyPoi(any(), any(), any()) } throws RuntimeException("network")

        assertThat(provider.reverseGeocode(37.77, -122.41).isFailure).isTrue()
    }

    @Test
    fun `forward geocoding is unsupported and returns empty`() = runTest {
        val result = provider.forwardGeocode("coffee")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEmpty()
    }
}
