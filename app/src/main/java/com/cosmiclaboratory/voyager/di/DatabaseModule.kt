package com.cosmiclaboratory.voyager.di

import android.content.Context
import com.cosmiclaboratory.voyager.data.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.data.database.dao.*
import com.cosmiclaboratory.voyager.utils.SecurityUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideVoyagerDatabase(@ApplicationContext context: Context): VoyagerDatabase {
        val passphrase = SecurityUtils.getDatabasePassphrase(context)
        return VoyagerDatabase.create(context, passphrase)
    }
    
    @Provides
    fun provideLocationDao(database: VoyagerDatabase): LocationDao {
        return database.locationDao()
    }
    
    @Provides
    fun providePlaceDao(database: VoyagerDatabase): PlaceDao {
        return database.placeDao()
    }
    
    @Provides
    fun provideVisitDao(database: VoyagerDatabase): VisitDao {
        return database.visitDao()
    }
    
    @Provides
    fun provideGeofenceDao(database: VoyagerDatabase): GeofenceDao {
        return database.geofenceDao()
    }
    
    @Provides
    fun provideCurrentStateDao(database: VoyagerDatabase): CurrentStateDao {
        return database.currentStateDao()
    }

    @Provides
    fun provideGeocodingCacheDao(database: VoyagerDatabase): GeocodingCacheDao {
        return database.geocodingCacheDao()
    }

    // Week 2: Review System DAOs
    @Provides
    fun providePlaceReviewDao(database: VoyagerDatabase): PlaceReviewDao {
        return database.placeReviewDao()
    }

    @Provides
    fun provideVisitReviewDao(database: VoyagerDatabase): VisitReviewDao {
        return database.visitReviewDao()
    }

    @Provides
    fun provideUserCorrectionDao(database: VoyagerDatabase): UserCorrectionDao {
        return database.userCorrectionDao()
    }

    @Provides
    fun provideCategoryPreferenceDao(database: VoyagerDatabase): CategoryPreferenceDao {
        return database.categoryPreferenceDao()
    }
}