package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.orchestrator.DataFlowOrchestrator
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
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
}