package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.orchestrator.DataFlowOrchestrator
import com.cosmiclaboratory.voyager.data.processor.SmartDataProcessor
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrchestratorModule {
    
    @Provides
    @Singleton
    fun provideDataFlowOrchestrator(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository,
        currentStateRepository: CurrentStateRepository,
        analyticsUseCases: AnalyticsUseCases
    ): DataFlowOrchestrator {
        return DataFlowOrchestrator(
            locationRepository,
            placeRepository,
            visitRepository,
            currentStateRepository,
            analyticsUseCases
        )
    }
    
    @Provides
    @Singleton
    fun provideSmartDataProcessor(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository,
        currentStateRepository: CurrentStateRepository,
        placeDetectionUseCases: PlaceDetectionUseCases,
        logger: ProductionLogger,
        appStateManager: com.cosmiclaboratory.voyager.data.state.AppStateManager,
        errorHandler: com.cosmiclaboratory.voyager.utils.ErrorHandler,
        validationService: com.cosmiclaboratory.voyager.domain.validation.ValidationService,
        preferencesRepository: com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
    ): SmartDataProcessor {
        return SmartDataProcessor(
            locationRepository,
            placeRepository,
            visitRepository,
            currentStateRepository,
            placeDetectionUseCases,
            logger,
            appStateManager,
            errorHandler,
            validationService,
            preferencesRepository
        )
    }
}