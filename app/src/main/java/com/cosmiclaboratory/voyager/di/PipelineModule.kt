package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.domain.time.Clock
import com.cosmiclaboratory.voyager.domain.time.SystemDefaultClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {
    // Pipeline stages use constructor injection (@Singleton @Inject) — no explicit providers needed

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemDefaultClock
}
