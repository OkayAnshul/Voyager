package com.cosmiclaboratory.voyager.di

import com.cosmiclaboratory.voyager.data.repository.*
import com.cosmiclaboratory.voyager.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository
    
    @Binds
    @Singleton
    abstract fun bindPlaceRepository(
        placeRepositoryImpl: PlaceRepositoryImpl
    ): PlaceRepository
    
    @Binds
    @Singleton
    abstract fun bindVisitRepository(
        visitRepositoryImpl: VisitRepositoryImpl
    ): VisitRepository
    
    @Binds
    @Singleton
    abstract fun bindGeofenceRepository(
        geofenceRepositoryImpl: GeofenceRepositoryImpl
    ): GeofenceRepository
    
    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(
        analyticsRepositoryImpl: AnalyticsRepositoryImpl
    ): AnalyticsRepository
    
    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository
    
    @Binds
    @Singleton
    abstract fun bindCurrentStateRepository(
        currentStateRepositoryImpl: CurrentStateRepositoryImpl
    ): CurrentStateRepository

    @Binds
    @Singleton
    abstract fun bindGeocodingRepository(
        geocodingRepositoryImpl: GeocodingRepositoryImpl
    ): GeocodingRepository

    // Week 2: Review System Repositories
    @Binds
    @Singleton
    abstract fun bindPlaceReviewRepository(
        placeReviewRepositoryImpl: PlaceReviewRepositoryImpl
    ): PlaceReviewRepository

    @Binds
    @Singleton
    abstract fun bindVisitReviewRepository(
        visitReviewRepositoryImpl: VisitReviewRepositoryImpl
    ): VisitReviewRepository

    @Binds
    @Singleton
    abstract fun bindUserCorrectionRepository(
        userCorrectionRepositoryImpl: UserCorrectionRepositoryImpl
    ): UserCorrectionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryPreferenceRepository(
        categoryPreferenceRepositoryImpl: CategoryPreferenceRepositoryImpl
    ): CategoryPreferenceRepository
}