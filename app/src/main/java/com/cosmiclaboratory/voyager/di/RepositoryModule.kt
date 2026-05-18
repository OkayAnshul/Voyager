package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.media.MediaStorePhotoLibrary
import com.cosmiclaboratory.voyager.data.repository.*
import com.cosmiclaboratory.voyager.domain.photo.PhotoLibrary
import com.cosmiclaboratory.voyager.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindTrackingRepository(impl: TrackingRepositoryImpl): TrackingRepository

    @Binds @Singleton
    abstract fun bindTimelineRepository(impl: TimelineRepositoryImpl): TimelineRepository

    @Binds @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds @Singleton
    abstract fun bindEvidenceRepository(impl: EvidenceRepositoryImpl): EvidenceRepository

    @Binds @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository

    @Binds @Singleton
    abstract fun bindGeocodingRepository(impl: GeocodingRepositoryImpl): GeocodingRepository

    @Binds @Singleton
    abstract fun bindStepsRepository(impl: StepsRepositoryImpl): StepsRepository

    @Binds @Singleton
    abstract fun bindMapRepository(impl: MapRepositoryImpl): MapRepository

    @Binds @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindCorrectionRepository(impl: CorrectionRepositoryImpl): CorrectionRepository

    @Binds @Singleton
    abstract fun bindPhotoLibrary(impl: MediaStorePhotoLibrary): PhotoLibrary
}
