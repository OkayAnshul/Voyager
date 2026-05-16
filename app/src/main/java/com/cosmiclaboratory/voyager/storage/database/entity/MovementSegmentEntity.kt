package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movement_segments",
    indices = [
        Index(value = ["dayKey", "startAt"]),
        Index(value = ["placeId"]),
        Index(value = ["routeId"])
    ]
)
data class MovementSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val segmentId: Long = 0,
    val segmentType: String, // VISIT/WALK/RUN/CYCLE/DRIVE/TRANSIT/GAP/UNKNOWN_MOTION
    val startAt: Long,
    val endAt: Long,
    val startSampleId: Long? = null,
    val endSampleId: Long? = null,
    val distanceM: Double = 0.0,
    val confidence: Float = 0.5f,
    val routeId: Long? = null,
    val placeId: Long? = null,
    val gapReason: String? = null, // PERMISSION/DOZE/PROCESS_DEAD/GPS_LOSS/MANUAL_PAUSE/UNKNOWN
    val dayKey: String, // YYYY-MM-DD
    val isUserCorrected: Boolean = false,
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
