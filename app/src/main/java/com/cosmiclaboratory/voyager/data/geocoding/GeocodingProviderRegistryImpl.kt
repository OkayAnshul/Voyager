package com.cosmiclaboratory.voyager.data.geocoding

import android.util.Log
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProviderRegistry
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocodingProviderRegistryImpl @Inject constructor(
    private val nominatimProvider: NominatimGeocodingProvider,
    private val androidGeocoderProvider: AndroidGeocodingProvider,
    private val photonProvider: PhotonGeocodingProvider,
    private val settingsRepository: SettingsRepository
) : GeocodingProviderRegistry {

    private val allProviders: List<GeocodingProvider> = listOf(
        androidGeocoderProvider,
        nominatimProvider,
        photonProvider
    )

    override fun getEnabledProviders(): List<GeocodingProvider> {
        val providerOrder = try {
            settingsRepository.observeSettings().value.providerOrder.toSet()
        } catch (e: Exception) {
            Log.w("GeocodingRegistry", "Failed to read provider order from settings, using defaults", e)
            setOf(GeocodingProviderId.ANDROID_GEOCODER, GeocodingProviderId.NOMINATIM, GeocodingProviderId.PHOTON)
        }
        return allProviders.filter { it.providerId in providerOrder }
    }

    override fun getProvider(id: String): GeocodingProvider? {
        val parsedId = try { GeocodingProviderId.valueOf(id) } catch (_: Exception) { return null }
        return allProviders.find { it.providerId == parsedId }
    }

    override fun getAllProviders(): List<GeocodingProvider> = allProviders
}
