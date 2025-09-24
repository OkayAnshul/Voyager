package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_activity_samples",
    foreignKeys = [
        ForeignKey(
            entity = TrackingSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["trackingSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackingSessionId", "capturedAt"])
    ]
)
data class RawActivitySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val activitySampleId: Long = 0,
    val activityType: String, // STILL/WALKING/RUNNING/CYCLING/IN_VEHICLE/ON_BICYCLE/TILTING/UNKNOWN
    val confidence: Int, // 0-100
    val source: String, // TRANSITION_API/CLASSIFIER/SPEED_HEURISTIC/ACCELEROMETER
    val transition: String? = null, // ENTER/EXIT/null
    val capturedAt: Long,
    val trackingSessionId: Long
)
