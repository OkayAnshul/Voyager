package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-recorded workout (Athlete persona) — a run/ride/walk captured intentionally,
 * distinct from the passively-detected movement segments.
 *
 * The route is stored as an encoded polyline; summary stats are precomputed by
 * [com.cosmiclaboratory.voyager.domain.usecase.WorkoutStatsCalculator] at save time.
 * Audit columns mirror the v3 syncable tables — inert until cloud sync ships.
 */
@Entity(
    tableName = "activities",
    indices = [Index(value = ["dayKey"]), Index(value = ["startedAt"])]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val activityId: Long = 0,
    val activityType: String, // WorkoutType.name
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val steps: Int? = null,
    val encodedPolyline: String,
    val dayKey: String, // YYYY-MM-DD
    val title: String? = null,
    val notes: String? = null,
    // Multi-user scoping (v8) — inert until sync/multi-user ships; default = install id.
    val userId: String = "",
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
