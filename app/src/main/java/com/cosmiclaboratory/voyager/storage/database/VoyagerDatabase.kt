package com.cosmiclaboratory.voyager.storage.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cosmiclaboratory.voyager.storage.database.converter.Converters
import com.cosmiclaboratory.voyager.storage.database.dao.*
import com.cosmiclaboratory.voyager.storage.database.entity.*
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        // Raw tables
        RawLocationSampleEntity::class,
        RawActivitySampleEntity::class,
        RawStepSampleEntity::class,
        TrackingSessionEntity::class,
        // Derived tables
        MovementSegmentEntity::class,
        SegmentEvidenceEntity::class,
        RouteEntity::class,
        // Semantic tables
        PlaceEntity::class,
        VisitEntity::class,
        VisitEvidenceEntity::class,
        PlaceEvidenceEntity::class,
        GeocodeCandidateEntity::class,
        // Ops tables
        CurrentRuntimeStateEntity::class,
        PendingPlaceUpdateEntity::class,
        HealthLogEntity::class,
        // Search tables
        SearchIndexEntity::class,
        SearchMetadataEntity::class,
        // Analytics tables
        DailyRollupEntity::class,
        WeeklyRollupEntity::class,
        PlaceRollupEntity::class,
        // Feedback
        CorrectionFeedbackEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
/**
 * Voyager's encrypted local database.
 *
 * Migration policy: every schema change MUST ship with a [androidx.room.migration.Migration]
 * object covering the bump. Destructive migration is deliberately NOT enabled — a missing
 * migration will throw [IllegalStateException] at open time so the gap is caught in
 * development, never on a user's device. User data is sacred; we lose nothing silently.
 *
 * If you bump [version], you must also:
 *   1. Add a `Migration(N, N+1)` to [MIGRATIONS].
 *   2. Add a Room migration test exercising the new migration with realistic data.
 *   3. Update the exported schema under `app/schemas/`.
 */
abstract class VoyagerDatabase : RoomDatabase() {

    // Raw DAOs
    abstract fun rawLocationSampleDao(): RawLocationSampleDao
    abstract fun rawActivitySampleDao(): RawActivitySampleDao
    abstract fun rawStepSampleDao(): RawStepSampleDao
    abstract fun trackingSessionDao(): TrackingSessionDao

    // Derived DAOs
    abstract fun movementSegmentDao(): MovementSegmentDao
    abstract fun segmentEvidenceDao(): SegmentEvidenceDao
    abstract fun routeDao(): RouteDao

    // Semantic DAOs
    abstract fun placeDao(): PlaceDao
    abstract fun visitDao(): VisitDao
    abstract fun visitEvidenceDao(): VisitEvidenceDao
    abstract fun placeEvidenceDao(): PlaceEvidenceDao
    abstract fun geocodeCandidateDao(): GeocodeCandidateDao

    // Ops DAOs
    abstract fun currentRuntimeStateDao(): CurrentRuntimeStateDao
    abstract fun pendingPlaceUpdateDao(): PendingPlaceUpdateDao
    abstract fun healthLogDao(): HealthLogDao

    // Search DAOs
    abstract fun searchDao(): SearchDao

    // Analytics DAOs
    abstract fun dailyRollupDao(): DailyRollupDao
    abstract fun weeklyRollupDao(): WeeklyRollupDao
    abstract fun placeRollupDao(): PlaceRollupDao

    // Feedback DAOs
    abstract fun correctionFeedbackDao(): CorrectionFeedbackDao

    companion object {
        private const val DATABASE_NAME = "voyager_database"

        /**
         * All schema migrations, in order. Append a new entry every time [version] is bumped.
         * Never use destructive migration — see the class KDoc.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN emoji TEXT")
            }
        }

        private val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context, passphrase: ByteArray?): VoyagerDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                VoyagerDatabase::class.java,
                DATABASE_NAME
            )

            if (passphrase != null && passphrase.isNotEmpty()) {
                val factory = SupportFactory(passphrase)
                builder.openHelperFactory(factory)
            }

            return builder
                .addMigrations(*MIGRATIONS)
                .build()
        }

        fun createInMemory(context: Context): VoyagerDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                VoyagerDatabase::class.java
            ).build()
        }
    }
}
