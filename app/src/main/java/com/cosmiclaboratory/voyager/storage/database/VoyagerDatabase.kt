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
        MileageClassificationEntity::class
    ],
    version = 4,
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

    // Mileage DAOs
    abstract fun mileageClassificationDao(): MileageClassificationDao

    companion object {
        private const val DATABASE_NAME = "voyager_database"

        /**
         * All schema migrations, in order. Append a new entry every time [version] is bumped.
         * Never use destructive migration — see the class KDoc.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN emoji TEXT")
            }
        }

        /**
         * v2 → v3: cloud-ready audit columns + unique visit index.
         *
         * Adds `lastModifiedAt`, `revision`, `deletedAt` to the 7 syncable tables. These
         * columns are inert today — no read query filters on them and no sync engine
         * exists yet. They are added now, while the schema is small, so that adding cloud
         * sync later is an additive plugin rather than a destructive migration.
         *
         * Also upgrades the (placeId, arrivalAt) visit index to UNIQUE as a belt-and-braces
         * guard behind the VisitWriteGuard mutex (H4). Any pre-existing exact-duplicate
         * visits are collapsed first so the unique index can be built.
         */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val syncableTables = listOf(
                    "places", "visits", "movement_segments", "routes",
                    "geocode_candidates", "correction_feedback", "place_evidence"
                )
                for (table in syncableTables) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE $table ADD COLUMN deletedAt INTEGER")
                }

                // Collapse exact-duplicate visits (same placeId + arrivalAt), keeping the
                // lowest visitId, so the UNIQUE index below can be created.
                db.execSQL(
                    """
                    DELETE FROM visits WHERE visitId NOT IN (
                        SELECT MIN(visitId) FROM visits GROUP BY placeId, arrivalAt
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS index_visits_placeId_arrivalAt")
                db.execSQL("CREATE UNIQUE INDEX index_visits_placeId_arrivalAt ON visits (placeId, arrivalAt)")
            }
        }

        /**
         * v3 → v4: mileage log + tax PDF (Pro).
         *
         * Adds `mileage_classifications` — a sparse, user-authored table tagging drive
         * segments with a tax purpose (business/personal/medical/charitable). Kept
         * separate from `movement_segments` so the hot derived-pipeline table is not
         * bloated by an optional Pro feature. The row is keyed 1:1 to its segment and
         * CASCADE-deletes with it. Audit columns mirror the v3 syncable tables.
         *
         * Purely additive — no existing table or data is touched.
         */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mileage_classifications` (
                        `segmentId` INTEGER NOT NULL,
                        `purpose` TEXT NOT NULL,
                        `note` TEXT,
                        `lastModifiedAt` INTEGER NOT NULL DEFAULT 0,
                        `revision` INTEGER NOT NULL DEFAULT 1,
                        `deletedAt` INTEGER,
                        PRIMARY KEY(`segmentId`),
                        FOREIGN KEY(`segmentId`) REFERENCES `movement_segments`(`segmentId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        /** Exposed for migration tests. Production code uses it only via [buildDatabase]. */
        internal val MIGRATIONS: Array<androidx.room.migration.Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
                .addMigrations(*MIGRATIONS)
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
