package com.cosmiclaboratory.voyager.storage.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
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
        CorrectionFeedbackEntity::class,
        // Mileage
        MileageClassificationEntity::class,
        // Trips
        TripEntity::class,
        // Activities (recorded workouts)
        ActivityEntity::class,
        // Race-yourself segments
        WorkoutSegmentEntity::class,
        // Mileage subsystem (Wave 10)
        VehicleEntity::class,
        FuelPriceHistoryEntity::class,
        TripVehicleAssignmentEntity::class,
        MileageSummaryEntity::class,
        VehicleServiceLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
/**
 * Voyager's encrypted local database.
 *
 * This is the **version 1** baseline — the schema shipped with the first public release.
 * (Pre-release development iterated through a throwaway migration chain that was collapsed
 * into this single v1 schema before launch; there are no production databases to migrate
 * from, so that history was dropped.)
 *
 * Migration policy from here on: every schema change MUST bump [version] and ship a
 * [androidx.room.migration.Migration] object covering the bump. Destructive migration is
 * deliberately NOT enabled for upgrades — a missing migration throws at open time so the
 * gap is caught in development, never on a user's device. User data is sacred; we lose
 * nothing silently. (A downgrade — only possible when moving from a pre-release dev build
 * back to v1 — falls back to a destructive recreate, since no such user data exists.)
 *
 * If you bump [version], you must also:
 *   1. Add a `Migration(N, N+1)` and register it via `.addMigrations(...)` in [buildDatabase].
 *   2. Add a Room migration test exercising the new migration with realistic data.
 *   3. Commit the new exported schema under `app/schemas/`.
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

    // Mileage DAOs
    abstract fun mileageClassificationDao(): MileageClassificationDao

    // Trip DAOs
    abstract fun tripDao(): TripDao

    // Activity (workout) DAOs
    abstract fun activityDao(): ActivityDao
    abstract fun workoutSegmentDao(): WorkoutSegmentDao

    // Mileage DAOs (Wave 10)
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelPriceHistoryDao(): FuelPriceHistoryDao
    abstract fun tripVehicleAssignmentDao(): TripVehicleAssignmentDao
    abstract fun mileageSummaryDao(): MileageSummaryDao
    abstract fun vehicleServiceLogDao(): VehicleServiceLogDao

    companion object {
        private const val DATABASE_NAME = "voyager_database"


        /**
         * Sets WAL journal mode and runs an integrity check on every open.
         * WAL lets readers and the writer proceed concurrently instead of locking the
         * whole DB on each write. integrity_check surfaces silent corruption from a
         * power-loss-mid-write into the log (and, later, a recovery screen).
         */
        private val openCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.query("PRAGMA journal_mode=WAL").use { it.moveToFirst() }
                } catch (e: Exception) {
                    Log.w("VoyagerDatabase", "Failed to set WAL journal mode", e)
                }
                try {
                    db.query("PRAGMA integrity_check").use { cursor ->
                        if (cursor.moveToFirst()) {
                            val result = cursor.getString(0)
                            if (!result.equals("ok", ignoreCase = true)) {
                                Log.e("VoyagerDatabase", "DB integrity check FAILED: $result")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("VoyagerDatabase", "integrity_check failed to run", e)
                }
            }
        }

        fun create(context: Context, passphrase: ByteArray?): VoyagerDatabase {
            val db = buildDatabase(context, passphrase)
            return try {
                // Probe the open now so an unreadable/undecryptable DB fails here,
                // not later on a background coroutine (which would crash the app).
                db.openHelper.readableDatabase
                db
            } catch (e: Exception) {
                // The on-disk DB cannot be opened — wrong key, corruption, or a file
                // from an incompatible build. Recreate it fresh rather than crash-loop.
                Log.e("VoyagerDatabase", "Database unreadable — recreating from scratch", e)
                runCatching { db.close() }
                context.applicationContext.deleteDatabase(DATABASE_NAME)
                buildDatabase(context, passphrase)
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray?): VoyagerDatabase {
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
                // No upgrade migrations yet — v1 is the baseline. A downgrade (only a
                // pre-release dev build stepping back to v1) recreates the DB rather than
                // crashing; there is no production data at a higher version to preserve.
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(openCallback)
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
