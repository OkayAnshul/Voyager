package com.cosmiclaboratory.voyager.di

import android.content.Context
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.domain.repository.GeofenceRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import com.cosmiclaboratory.voyager.domain.usecase.AnalyzePlacePatternsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.CompareMonthlyAnalyticsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.CompareWeeklyAnalyticsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.DetectAnomaliesUseCase
import com.cosmiclaboratory.voyager.domain.usecase.EnrichPlaceWithDetailsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.ExportDataUseCase
import com.cosmiclaboratory.voyager.domain.usecase.GatherPlaceNameSuggestionsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceUseCases
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
        validationService: com.cosmiclaboratory.voyager.domain.validation.ValidationService,
        enrichPlaceWithDetailsUseCase: EnrichPlaceWithDetailsUseCase,
        autoAcceptDecisionUseCase: com.cosmiclaboratory.voyager.domain.usecase.AutoAcceptDecisionUseCase,
        placeReviewUseCases: com.cosmiclaboratory.voyager.domain.usecase.PlaceReviewUseCases,
        categoryLearningEngine: com.cosmiclaboratory.voyager.domain.usecase.CategoryLearningEngine
    ): PlaceDetectionUseCases {
        return PlaceDetectionUseCases(
            locationRepository,
            placeRepository,
            visitRepository,
            preferencesRepository,
            errorHandler,
            validationService,
            enrichPlaceWithDetailsUseCase,
            autoAcceptDecisionUseCase,
            placeReviewUseCases,
            categoryLearningEngine
        )
    }

    // ===== NEW ANALYTICS USE CASES (Session #6 - Bug Fix) =====

    @Provides
    @Singleton
    fun provideCompareWeeklyAnalyticsUseCase(
        visitRepository: VisitRepository,
        placeRepository: PlaceRepository
    ): CompareWeeklyAnalyticsUseCase {
        return CompareWeeklyAnalyticsUseCase(visitRepository, placeRepository)
    }

    @Provides
    @Singleton
    fun provideCompareMonthlyAnalyticsUseCase(
        visitRepository: VisitRepository,
        placeRepository: PlaceRepository
    ): CompareMonthlyAnalyticsUseCase {
        return CompareMonthlyAnalyticsUseCase(visitRepository, placeRepository)
    }

    @Provides
    @Singleton
    fun provideAnalyzePlacePatternsUseCase(
        visitRepository: VisitRepository,
        placeRepository: PlaceRepository,
        preferencesRepository: PreferencesRepository
    ): AnalyzePlacePatternsUseCase {
        return AnalyzePlacePatternsUseCase(visitRepository, placeRepository, preferencesRepository)
    }

    @Provides
    @Singleton
    fun provideDetectAnomaliesUseCase(
        visitRepository: VisitRepository,
        placeRepository: PlaceRepository,
        analyzePlacePatternsUseCase: AnalyzePlacePatternsUseCase,
        preferencesRepository: PreferencesRepository
    ): DetectAnomaliesUseCase {
        return DetectAnomaliesUseCase(visitRepository, placeRepository, analyzePlacePatternsUseCase, preferencesRepository)
    }

    @Provides
    @Singleton
    fun provideExportDataUseCase(
        @ApplicationContext context: Context,
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository
    ): ExportDataUseCase {
        return ExportDataUseCase(context, locationRepository, placeRepository, visitRepository)
    }

    // ISSUE #2: Place name suggestions use case
    @Provides
    @Singleton
    fun provideGatherPlaceNameSuggestionsUseCase(
        geocodingRepository: com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository,
        placeRepository: PlaceRepository,
        userCorrectionRepository: com.cosmiclaboratory.voyager.domain.repository.UserCorrectionRepository,
        logger: ProductionLogger
    ): GatherPlaceNameSuggestionsUseCase {
        return GatherPlaceNameSuggestionsUseCase(geocodingRepository, placeRepository, userCorrectionRepository, logger)
    }
}