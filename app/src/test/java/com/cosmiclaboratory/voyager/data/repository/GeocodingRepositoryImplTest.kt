package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingConflictResolver
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.model.ProviderGeoResult
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [GeocodingRepositoryImpl]'s sequential short-circuit: providers are
 * tried in the user's order and a HIGH-tier result stops the stack early.
 */
class GeocodingRepositoryImplTest {

    private val settingsRepository = mockk<SettingsRepository>()

    private fun fakeProvider(
        id: GeocodingProviderId,
        confidence: Float,
        structured: StructuredAddress? = StructuredAddress(street = "Main St", city = "Townsville"),
    ): GeocodingProvider = mockk {
        every { providerId } returns id
        every { priority } returns id.ordinal
        every { isAvailable } returns true
        coEvery { reverseGeocode(any(), any()) } returns Result.success(
            ProviderGeoResult("Result from $id", structured, confidence)
        )
    }

    private fun repository(
        providers: List<GeocodingProvider>,
        order: List<GeocodingProviderId>,
    ): GeocodingRepositoryImpl {
        every { settingsRepository.observeSettings() } returns
            MutableStateFlow(UserSettings(providerOrder = order))
        return GeocodingRepositoryImpl(
            providers = providers,
            geocodeCandidateDao = mockk(relaxed = true),
            placeDao = mockk(relaxed = true),
            conflictResolver = GeocodingConflictResolver(),
            settingsRepository = settingsRepository
        )
    }

    @Test
    fun `a HIGH-tier first result short-circuits the remaining providers`() = runTest {
        val android = fakeProvider(GeocodingProviderId.ANDROID_GEOCODER, confidence = 0.85f)
        val nominatim = fakeProvider(GeocodingProviderId.NOMINATIM, confidence = 0.85f)
        val repo = repository(
            providers = listOf(android, nominatim),
            order = listOf(GeocodingProviderId.ANDROID_GEOCODER, GeocodingProviderId.NOMINATIM)
        )

        val result = repo.reverseGeocode(1.0, 2.0)

        assertThat(result.bestCandidate?.provider).isEqualTo(GeocodingProviderId.ANDROID_GEOCODER)
        coVerify(exactly = 1) { android.reverseGeocode(any(), any()) }
        coVerify(exactly = 0) { nominatim.reverseGeocode(any(), any()) }
    }

    @Test
    fun `a below-HIGH first result falls through to the next provider`() = runTest {
        // Photon 0.50 → normalized 0.475 → LOW tier, so the stack continues.
        val photon = fakeProvider(GeocodingProviderId.PHOTON, confidence = 0.50f)
        val android = fakeProvider(GeocodingProviderId.ANDROID_GEOCODER, confidence = 0.85f)
        val repo = repository(
            providers = listOf(photon, android),
            order = listOf(GeocodingProviderId.PHOTON, GeocodingProviderId.ANDROID_GEOCODER)
        )

        val result = repo.reverseGeocode(1.0, 2.0)

        coVerify(exactly = 1) { photon.reverseGeocode(any(), any()) }
        coVerify(exactly = 1) { android.reverseGeocode(any(), any()) }
        assertThat(result.bestCandidate).isNotNull()
    }

    @Test
    fun `a provider absent from providerOrder is never queried`() = runTest {
        val android = fakeProvider(GeocodingProviderId.ANDROID_GEOCODER, confidence = 0.85f)
        val nominatim = fakeProvider(GeocodingProviderId.NOMINATIM, confidence = 0.85f)
        val repo = repository(
            providers = listOf(android, nominatim),
            order = listOf(GeocodingProviderId.ANDROID_GEOCODER) // Nominatim disabled
        )

        repo.reverseGeocode(1.0, 2.0)

        coVerify(exactly = 0) { nominatim.reverseGeocode(any(), any()) }
    }
}
