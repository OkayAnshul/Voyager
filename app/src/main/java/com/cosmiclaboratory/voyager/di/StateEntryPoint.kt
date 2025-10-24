package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceUseCases
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing state management and use case components from non-Hilt contexts
 * Used to break circular dependencies and provide manual dependency injection for fallback workers
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StateEntryPoint {
    fun appStateManager(): AppStateManager
    fun placeDetectionUseCases(): PlaceDetectionUseCases
    fun placeUseCases(): PlaceUseCases
}