package com.cosmiclaboratory.voyager.data.geocoding

import com.cosmiclaboratory.voyager.data.api.AddressResult
import com.cosmiclaboratory.voyager.data.api.AndroidGeocoderService
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [AndroidGeocodingProvider] — that the enriched Android Geocoder fields
 * (house number, state) flow into the structured address, and that a genuine
 * landmark name is used as a display-name prefix.
 */
class AndroidGeocodingProviderTest {

    private val service = mockk<AndroidGeocoderService>()
    private val provider = AndroidGeocodingProvider(service)

    @Test
    fun `house number and state flow into the structured address`() = runTest {
        coEvery { service.reverseGeocode(any(), any()) } returns AddressResult(
            formattedAddress = "221B Baker Street, London",
            streetName = "Baker Street",
            houseNumber = "221B",
            locality = "London",
            subLocality = "Marylebone",
            state = "England",
            postalCode = "NW1 6XE",
            countryCode = "GB"
        )

        val parts = provider.reverseGeocode(51.52, -0.15).getOrThrow().structuredParts!!

        assertThat(parts.houseNumber).isEqualTo("221B")
        assertThat(parts.state).isEqualTo("England")
        assertThat(parts.street).isEqualTo("Baker Street")
        assertThat(parts.city).isEqualTo("London")
    }

    @Test
    fun `a landmark hint is prefixed onto the display name`() = runTest {
        coEvery { service.reverseGeocode(any(), any()) } returns AddressResult(
            formattedAddress = "Speedwell Ave, London",
            streetName = "Speedwell Ave",
            landmarkHint = "Sherlock Holmes Museum"
        )

        val name = provider.reverseGeocode(51.52, -0.15).getOrThrow().displayName

        assertThat(name).isEqualTo("Sherlock Holmes Museum, Speedwell Ave, London")
    }

    @Test
    fun `a landmark hint already in the address is not duplicated`() = runTest {
        coEvery { service.reverseGeocode(any(), any()) } returns AddressResult(
            formattedAddress = "Sherlock Holmes Museum, Baker Street",
            landmarkHint = "Sherlock Holmes Museum"
        )

        val name = provider.reverseGeocode(51.52, -0.15).getOrThrow().displayName

        assertThat(name).isEqualTo("Sherlock Holmes Museum, Baker Street")
    }

    @Test
    fun `a null service result is a failure`() = runTest {
        coEvery { service.reverseGeocode(any(), any()) } returns null

        assertThat(provider.reverseGeocode(51.52, -0.15).isFailure).isTrue()
    }
}
