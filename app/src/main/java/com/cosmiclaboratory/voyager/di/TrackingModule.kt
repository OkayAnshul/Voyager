package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.domain.map.MapEngine
import com.cosmiclaboratory.voyager.platform.map.MapLibreMapEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    @Singleton
    abstract fun bindMapEngine(impl: MapLibreMapEngine): MapEngine

    // TrackingRuntimeCoordinator and capture components use constructor injection (@Singleton @Inject)
}
