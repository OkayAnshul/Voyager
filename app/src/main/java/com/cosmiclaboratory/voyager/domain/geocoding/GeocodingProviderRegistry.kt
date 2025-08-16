package com.cosmiclaboratory.voyager.domain.geocoding

/**
 * Registry of all available geocoding providers.
 * Filters by user-enabled preferences for the active set.
 */
interface GeocodingProviderRegistry {
    /** All providers whose IDs appear in the user's enabledGeocodingProviders preference */
    fun getEnabledProviders(): List<GeocodingProvider>

    /** Lookup a provider by ID */
    fun getProvider(id: String): GeocodingProvider?

    /** All registered providers regardless of enabled state */
    fun getAllProviders(): List<GeocodingProvider>
}
