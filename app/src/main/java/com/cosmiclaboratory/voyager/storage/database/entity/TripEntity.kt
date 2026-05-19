package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A detected multi-day trip away from home.
 *
 * Trips are derived data — [com.cosmiclaboratory.voyager.domain.usecase.DetectTripsUseCase]
 * recomputes the whole table from visits on each run, so a row is a denormalized summary
 * that powers the trip list cheaply; the day-by-day detail is re-derived from visits on
 * demand. The summary fields are recomputed on every detection run; the
 * user-authored fields ([userTitle], [notes], [coverPhotoUri], [dayCaptionsJson])
 * are preserved across rebuilds by `TripDao.replaceAll`, matched on [startDayKey].
 *
 * Keyed by [startDayKey] (unique) — a trip is identified by the day it began.
 * Audit columns mirror the v3 syncable tables — inert until cloud sync ships.
 */
@Entity(
    tableName = "trips",
    indices = [Index(value = ["startDayKey"], unique = true)]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val tripId: Long = 0,
    /** ISO `yyyy-MM-dd` of the first away-from-home day (inclusive). */
    val startDayKey: String,
    /** ISO `yyyy-MM-dd` of the last away-from-home day (inclusive). */
    val endDayKey: String,
    val title: String,
    val placeCount: Int,
    val visitCount: Int,
    val distanceMeters: Double,
    /** True while the trip may still be in progress (ended today or yesterday). */
    val isOngoing: Boolean,
    val detectedAt: Long,
    // User-authored fields (v6) — survive a detection rebuild via replaceAll.
    /** Overrides the derived [title] when set. */
    val userTitle: String? = null,
    val notes: String? = null,
    val coverPhotoUri: String? = null,
    /** JSON map of `dayKey` → caption; null until the user writes one. */
    val dayCaptionsJson: String? = null,
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
