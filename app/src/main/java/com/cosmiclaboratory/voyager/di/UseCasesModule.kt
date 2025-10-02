package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceUseCases
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCasesModule {
    
    @Provides
    @Singleton
    fun provideLocationUseCases(
        locationRepository: LocationRepository,
        locationServiceManager: LocationServiceManager
    ): LocationUseCases {
        return LocationUseCases(locationRepository, locationServiceManager)
    }
    
    @Provides
    @Singleton
    fun providePlaceUseCases(
        placeRepository: PlaceRepository,
        locationRepository: LocationRepository,
        visitRepository: VisitRepository
    ): PlaceUseCases {
        return PlaceUseCases(placeRepository, locationRepository, visitRepository)
    }
    
    @Provides
    @Singleton
    fun provideAnalyticsUseCases(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository,
        placeUseCases: PlaceUseCases
    ): AnalyticsUseCases {
        return AnalyticsUseCases(locationRepository, placeRepository, visitRepository, placeUseCases)
    }
    
    @Provides
    @Singleton
    fun providePlaceDetectionUseCases(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository
    ): PlaceDetectionUseCases {
        return PlaceDetectionUseCases(locationRepository, placeRepository, visitRepository)
    }
}