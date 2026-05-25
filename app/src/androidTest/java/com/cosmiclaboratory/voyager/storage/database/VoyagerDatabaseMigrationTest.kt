package com.cosmiclaboratory.voyager.storage.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Exercises every shipped Room migration of [VoyagerDatabase].
 *
 * The contract from the database KDoc: no destructive migration, no silent data loss.
 * These tests prove the full v1 → v5 chain runs and that real rows written at v1
 * survive intact.
 */
@RunWith(AndroidJUnit4::class)
class VoyagerDatabaseMigrationTest {

    private val testDb = "voyager_migration_test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VoyagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Full chain: create the DB at the original v1 schema, write a place and a drive
     * segment, then migrate v1 → … → v5 in one pass and assert the data is intact.
     */
    @Test
    @Throws(IOException::class)
    fun migrate1To6_preservesData() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                INSERT INTO places
                    (centroidLat, centroidLng, radiusM, geohash, confidence,
                     lifecycleStatus, category, categoryConfidence, createdAt)
                VALUES (40.7128, -74.0060, 50.0, 'dr5regw', 0.9,
                        'CONFIRMED', 'HOME', 0.8, 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO movement_segments
                    (segmentType, startAt, endAt, distanceM, confidence, dayKey, isUserCorrected)
                VALUES ('DRIVE', 1000, 5000, 12500.0, 0.92, '2026-05-17', 0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 6, true, *VoyagerDatabase.MIGRATIONS
        )

        db.query("SELECT segmentId, segmentType, distanceM FROM movement_segments").use { c ->
            assertThat(c.count).isEqualTo(1)
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(c.getColumnIndexOrThrow("segmentType"))).isEqualTo("DRIVE")
            assertThat(c.getDouble(c.getColumnIndexOrThrow("distanceM"))).isEqualTo(12500.0)
        }
        db.query("SELECT placeId, category FROM places").use { c ->
            assertThat(c.count).isEqualTo(1)
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(c.getColumnIndexOrThrow("category"))).isEqualTo("HOME")
        }

        // The v4 table exists and accepts a classification keyed to the surviving segment.
        db.execSQL(
            """
            INSERT INTO mileage_classifications (segmentId, purpose, note, lastModifiedAt, revision)
            VALUES (1, 'BUSINESS', 'client visit', 5000, 1)
            """.trimIndent()
        )
        db.query("SELECT purpose FROM mileage_classifications WHERE segmentId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("BUSINESS")
        }

        // The v5 trips table exists and accepts a row.
        db.execSQL(
            """
            INSERT INTO trips
                (startDayKey, endDayKey, title, placeCount, visitCount, distanceMeters,
                 isOngoing, detectedAt, lastModifiedAt, revision)
            VALUES ('2026-05-01', '2026-05-04', 'Trip to Paris', 5, 12, 84000.0, 0, 9000, 0, 1)
            """.trimIndent()
        )
        db.query("SELECT title FROM trips WHERE startDayKey = '2026-05-01'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Trip to Paris")
        }

        // The v6 user-authored trip columns exist and are writable.
        db.execSQL(
            "UPDATE trips SET userTitle = 'My Paris trip', notes = 'great food' " +
                "WHERE startDayKey = '2026-05-01'"
        )
        db.query("SELECT userTitle, notes FROM trips WHERE startDayKey = '2026-05-01'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("My Paris trip")
            assertThat(c.getString(1)).isEqualTo("great food")
        }
    }

    /**
     * Direct v5 → v6 hop: the user-authored trip columns are purely additive and
     * a trip row written at v5 survives intact with the new columns NULL.
     */
    @Test
    @Throws(IOException::class)
    fun migrate5To6_addsTripUserFields() {
        helper.createDatabase(testDb, 5).apply {
            execSQL(
                """
                INSERT INTO trips
                    (startDayKey, endDayKey, title, placeCount, visitCount, distanceMeters,
                     isOngoing, detectedAt, lastModifiedAt, revision)
                VALUES ('2026-06-01', '2026-06-03', 'Trip to Goa', 4, 9, 60000.0, 0, 7000, 0, 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true, *VoyagerDatabase.MIGRATIONS)

        // The v5 row survives; the new columns default to NULL.
        db.query(
            "SELECT title, userTitle, notes, coverPhotoUri, dayCaptionsJson " +
                "FROM trips WHERE startDayKey = '2026-06-01'"
        ).use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(c.getColumnIndexOrThrow("title"))).isEqualTo("Trip to Goa")
            assertThat(c.isNull(c.getColumnIndexOrThrow("userTitle"))).isTrue()
            assertThat(c.isNull(c.getColumnIndexOrThrow("notes"))).isTrue()
            assertThat(c.isNull(c.getColumnIndexOrThrow("coverPhotoUri"))).isTrue()
            assertThat(c.isNull(c.getColumnIndexOrThrow("dayCaptionsJson"))).isTrue()
        }
    }

    /**
     * Direct v4 → v5 hop: the trips migration is purely additive and leaves the existing
     * v4 schema untouched.
     */
    @Test
    @Throws(IOException::class)
    fun migrate4To5_addsTripsTable() {
        helper.createDatabase(testDb, 4).apply {
            execSQL(
                """
                INSERT INTO movement_segments
                    (segmentType, startAt, endAt, distanceM, confidence, dayKey,
                     isUserCorrected, lastModifiedAt, revision)
                VALUES ('DRIVE', 1000, 5000, 8000.0, 0.9, '2026-05-17', 0, 0, 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 5, true, *VoyagerDatabase.MIGRATIONS)

        // The pre-existing v4 row survives.
        db.query("SELECT COUNT(*) FROM movement_segments").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
        // The new trips table is present and writable, and its unique startDayKey index holds.
        db.execSQL(
            "INSERT INTO trips (startDayKey, endDayKey, title, placeCount, visitCount, " +
                "distanceMeters, isOngoing, detectedAt) " +
                "VALUES ('2026-06-01', '2026-06-03', '3-day trip', 2, 4, 12000.0, 1, 100)"
        )
        db.query("SELECT COUNT(*) FROM trips").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    /**
     * Direct v3 → v4 hop: the mileage migration is purely additive and leaves the
     * existing v3 schema untouched.
     */
    @Test
    @Throws(IOException::class)
    fun migrate3To4_addsMileageTable() {
        helper.createDatabase(testDb, 3).apply {
            execSQL(
                """
                INSERT INTO movement_segments
                    (segmentType, startAt, endAt, distanceM, confidence, dayKey,
                     isUserCorrected, lastModifiedAt, revision)
                VALUES ('DRIVE', 1000, 5000, 8000.0, 0.9, '2026-05-17', 0, 0, 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true, *VoyagerDatabase.MIGRATIONS)

        // CASCADE delete: removing the parent segment removes its classification.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO mileage_classifications (segmentId, purpose, lastModifiedAt, revision) " +
                "VALUES (1, 'PERSONAL', 0, 1)"
        )
        db.execSQL("DELETE FROM movement_segments WHERE segmentId = 1")
        db.query("SELECT COUNT(*) FROM mileage_classifications").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
    }
}
