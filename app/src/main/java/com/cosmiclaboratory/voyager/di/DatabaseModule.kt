package com.cosmiclaboratory.voyager.di

import android.content.Context
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.*
import com.cosmiclaboratory.voyager.storage.encryption.DatabaseEncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Voyager's database is always encrypted at rest. The passphrase is derived from a
     * non-extractable Android Keystore key, so the database can only be opened on the
     * device that created it. There is no unencrypted mode.
     */
    @Provides
    @Singleton
    fun provideVoyagerDatabase(
        @ApplicationContext context: Context,
        encryptionManager: DatabaseEncryptionManager
    ): VoyagerDatabase {
        return VoyagerDatabase.create(context, encryptionManager.getPassphrase())
    }

    // Raw tables
    @Provides fun provideRawLocationSampleDao(db: VoyagerDatabase) = db.rawLocationSampleDao()
    @Provides fun provideRawActivitySampleDao(db: VoyagerDatabase) = db.rawActivitySampleDao()
    @Provides fun provideRawStepSampleDao(db: VoyagerDatabase) = db.rawStepSampleDao()
    @Provides fun provideTrackingSessionDao(db: VoyagerDatabase) = db.trackingSessionDao()

    // Derived tables
    @Provides fun provideMovementSegmentDao(db: VoyagerDatabase) = db.movementSegmentDao()
    @Provides fun provideSegmentEvidenceDao(db: VoyagerDatabase) = db.segmentEvidenceDao()
    @Provides fun provideRouteDao(db: VoyagerDatabase) = db.routeDao()

    // Semantic tables
    @Provides fun providePlaceDao(db: VoyagerDatabase) = db.placeDao()
    @Provides fun provideVisitDao(db: VoyagerDatabase) = db.visitDao()
    @Provides fun provideVisitEvidenceDao(db: VoyagerDatabase) = db.visitEvidenceDao()
    @Provides fun providePlaceEvidenceDao(db: VoyagerDatabase) = db.placeEvidenceDao()
    @Provides fun provideGeocodeCandidateDao(db: VoyagerDatabase) = db.geocodeCandidateDao()

    // Ops tables
    @Provides fun provideCurrentRuntimeStateDao(db: VoyagerDatabase) = db.currentRuntimeStateDao()
    @Provides fun providePendingPlaceUpdateDao(db: VoyagerDatabase) = db.pendingPlaceUpdateDao()
    @Provides fun provideHealthLogDao(db: VoyagerDatabase) = db.healthLogDao()

    // Search tables
    @Provides fun provideSearchDao(db: VoyagerDatabase) = db.searchDao()

    // Analytics tables
    @Provides fun provideDailyRollupDao(db: VoyagerDatabase) = db.dailyRollupDao()
    @Provides fun provideWeeklyRollupDao(db: VoyagerDatabase) = db.weeklyRollupDao()
    @Provides fun providePlaceRollupDao(db: VoyagerDatabase) = db.placeRollupDao()

    // Feedback
    @Provides fun provideCorrectionFeedbackDao(db: VoyagerDatabase) = db.correctionFeedbackDao()

    // Mileage
    @Provides fun provideMileageClassificationDao(db: VoyagerDatabase) = db.mileageClassificationDao()

    // Trips
    @Provides fun provideTripDao(db: VoyagerDatabase) = db.tripDao()
}
