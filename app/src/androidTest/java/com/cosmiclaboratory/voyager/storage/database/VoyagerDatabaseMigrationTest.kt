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
 * These tests prove the full v1 → v4 chain runs and that real rows written at v1
 * survive to v4 intact.
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
     * segment, then migrate v1 → v2 → v3 → v4 in one pass and assert the data is intact.
     */
    @Test
    @Throws(IOException::class)
    fun migrate1To4_preservesData() {
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
            testDb, 4, true, *VoyagerDatabase.MIGRATIONS
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

        val db = helper.runMigrationsAndValidate(testDb, 4, true, VoyagerDatabase.MIGRATIONS.last())

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
