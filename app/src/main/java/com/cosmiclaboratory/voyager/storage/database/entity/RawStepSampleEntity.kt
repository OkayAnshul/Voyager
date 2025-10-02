package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_step_samples",
    foreignKeys = [
        ForeignKey(
            entity = TrackingSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["trackingSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["periodStart", "periodEnd"])
    ]
)
data class RawStepSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val stepSampleId: Long = 0,
    val periodStart: Long,
    val periodEnd: Long,
    val stepCount: Int,
    val source: String, // HEALTH_CONNECT/STEP_SENSOR/PEDOMETER
    val confidence: Float = 1.0f,
    val trackingSessionId: Long
)
