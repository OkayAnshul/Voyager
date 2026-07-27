package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-saved favourite sub-route ("segment") to race yourself on — the private, on-device
 * analogue to a Strava segment. The reference path is an encoded polyline; efforts are computed
 * on the fly by matching it against recorded activities (no stored leaderboard, no cloud).
 */
@Entity(tableName = "workout_segments", indices = [Index(value = ["createdAt"])])
data class WorkoutSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val segmentId: Long = 0,
    val name: String,
    val encodedPolyline: String,
    val distanceMeters: Double,
    val createdAt: Long,
    // Cloud-ready audit columns, inert until sync ships (mirrors the other syncable tables).
    val userId: String = "",
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
)
