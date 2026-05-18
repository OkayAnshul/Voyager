package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.geocoding.AndroidGeocodingProvider
import com.cosmiclaboratory.voyager.data.geocoding.NominatimGeocodingProvider
import com.cosmiclaboratory.voyager.data.geocoding.OverpassGeocodingProvider
import com.cosmiclaboratory.voyager.data.geocoding.PhotonGeocodingProvider
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeocodingModule {

    /**
     * Provides the ordered list of geocoding providers for the multi-provider pipeline.
     * The repository sorts by priority, but insertion order here matches default priority.
     */
    @Provides
    @Singleton
    fun provideGeocodingProviders(
        overpassProvider: OverpassGeocodingProvider,
        androidProvider: AndroidGeocodingProvider,
        photonProvider: PhotonGeocodingProvider,
        nominatimProvider: NominatimGeocodingProvider
    ): List<GeocodingProvider> = listOf(
        overpassProvider, // priority 0 — real POI names
        androidProvider,  // priority 1
        photonProvider,   // priority 2
        nominatimProvider // priority 3
    )
}
