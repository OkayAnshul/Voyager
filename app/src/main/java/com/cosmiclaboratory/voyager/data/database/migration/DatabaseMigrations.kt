package com.cosmiclaboratory.voyager.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database Migrations for Voyager Database
 * CRITICAL: Maintains data integrity during schema changes
 */
object DatabaseMigrations {

    /**
     * Migration from version 1 to 2
     * ISSUE #3 FIX: Adds customCategoryName field to places table
     * to support custom category names when user selects CUSTOM category
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add customCategoryName column to places table
            database.execSQL(
                "ALTER TABLE places ADD COLUMN customCategoryName TEXT"
            )
        }
    }

    /**
     * Migration from version 2 to 3
     * Adds foreign key constraints and indexes to current_state table
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // CRITICAL: Cannot add foreign keys to existing table directly
            // Need to recreate the table with proper constraints
            
            // 1. Create new table with foreign key constraints
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS current_state_new (
                    id INTEGER NOT NULL PRIMARY KEY,
                    currentPlaceId INTEGER,
                    currentVisitId INTEGER,
                    lastLocationUpdate TEXT NOT NULL,
                    isLocationTrackingActive INTEGER NOT NULL DEFAULT 0,
                    trackingStartTime TEXT,
                    currentSessionStartTime TEXT,
                    currentPlaceEntryTime TEXT,
                    totalLocationsToday INTEGER NOT NULL DEFAULT 0,
                    totalPlacesVisitedToday INTEGER NOT NULL DEFAULT 0,
                    totalTimeTrackedToday INTEGER NOT NULL DEFAULT 0,
                    lastUpdated TEXT NOT NULL,
                    FOREIGN KEY(currentPlaceId) REFERENCES places(id) ON DELETE SET NULL,
                    FOREIGN KEY(currentVisitId) REFERENCES visits(id) ON DELETE SET NULL
                )
            """.trimIndent())
            
            // 2. Copy data from old table to new table
            database.execSQL("""
                INSERT INTO current_state_new (
                    id, currentPlaceId, currentVisitId, lastLocationUpdate,
                    isLocationTrackingActive, trackingStartTime, currentSessionStartTime,
                    currentPlaceEntryTime, totalLocationsToday, totalPlacesVisitedToday,
                    totalTimeTrackedToday, lastUpdated
                )
                SELECT 
                    id, currentPlaceId, currentVisitId, lastLocationUpdate,
                    isLocationTrackingActive, trackingStartTime, currentSessionStartTime,
                    currentPlaceEntryTime, totalLocationsToday, totalPlacesVisitedToday,
                    totalTimeTrackedToday, lastUpdated
                FROM current_state
            """.trimIndent())
            
            // 3. Drop old table
            database.execSQL("DROP TABLE current_state")
            
            // 4. Rename new table to original name
            database.execSQL("ALTER TABLE current_state_new RENAME TO current_state")
            
            // 5. Create indexes for performance
            database.execSQL("CREATE INDEX IF NOT EXISTS index_current_state_currentPlaceId ON current_state(currentPlaceId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_current_state_currentVisitId ON current_state(currentVisitId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_current_state_isLocationTrackingActive ON current_state(isLocationTrackingActive)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_current_state_lastUpdated ON current_state(lastUpdated)")
            
            // 6. Enable foreign key constraints
            database.execSQL("PRAGMA foreign_keys = ON")
        }
    }
    
    /**
     * All available migrations
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3
    )
}