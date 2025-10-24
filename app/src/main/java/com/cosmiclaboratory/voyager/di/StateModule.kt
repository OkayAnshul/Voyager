package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.data.event.StateEventDispatcher
import com.cosmiclaboratory.voyager.data.sync.StateSynchronizer
import com.cosmiclaboratory.voyager.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for state management components
 * CRITICAL: Ensures proper initialization order and singleton behavior
 */
@Module
@InstallIn(SingletonComponent::class)
object StateModule {
    
    @Provides
    @Singleton
    fun provideStateEventDispatcher(): StateEventDispatcher {
        return StateEventDispatcher()
    }
    
    @Provides
    @Singleton
    fun provideAppStateManager(
        eventDispatcher: StateEventDispatcher,
        placeRepository: PlaceRepository
    ): AppStateManager {
        return AppStateManager(eventDispatcher, placeRepository)
    }
    
    @Provides
    @Singleton
    fun provideStateSynchronizer(
        appStateManager: AppStateManager,
        eventDispatcher: StateEventDispatcher,
        currentStateRepository: CurrentStateRepository,
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        visitRepository: VisitRepository
    ): StateSynchronizer {
        return StateSynchronizer(
            appStateManager,
            eventDispatcher,
            currentStateRepository,
            locationRepository,
            placeRepository,
            visitRepository
        )
    }
}