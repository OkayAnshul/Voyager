package com.cosmiclaboratory.voyager.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {
    // Pipeline stages use constructor injection (@Singleton @Inject) — no explicit providers needed
}
