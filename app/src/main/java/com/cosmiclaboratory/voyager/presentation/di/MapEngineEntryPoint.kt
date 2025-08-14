package com.cosmiclaboratory.voyager.presentation.di

import com.cosmiclaboratory.voyager.domain.map.MapEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapEngineEntryPoint {
    fun mapEngine(): MapEngine
}
