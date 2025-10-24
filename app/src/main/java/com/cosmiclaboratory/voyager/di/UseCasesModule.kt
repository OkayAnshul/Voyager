package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.domain.repository.GeofenceRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceUseCases
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import com.cosmiclaboratory.voyager.utils.ProductionLogger
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
        visitRepository: VisitRepository,
        geofenceRepository: GeofenceRepository
    ): PlaceUseCases {
        return PlaceUseCases(placeRepository, locationRepository, visitRepository, geofenceRepository)
    }
    
    @Provides
    @Singleton
    fun provideAnalyticsUseCases(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository,
        currentStateRepository: CurrentStateRepository,
        locationServiceManager: LocationServiceManager,
        placeUseCases: PlaceUseCases,
        logger: ProductionLogger
    ): AnalyticsUseCases {
        return AnalyticsUseCases(locationRepository, placeRepository, visitRepository, currentStateRepository, locationServiceManager, placeUseCases, logger)
    }
    
    @Provides
    @Singleton
    fun providePlaceDetectionUseCases(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository,
        preferencesRepository: PreferencesRepository,
        errorHandler: com.cosmiclaboratory.voyager.utils.ErrorHandler,
        validationService: com.cosmiclaboratory.voyager.domain.validation.ValidationService
    ): PlaceDetectionUseCases {
        return PlaceDetectionUseCases(locationRepository, placeRepository, visitRepository, preferencesRepository, errorHandler, validationService)
    }
}