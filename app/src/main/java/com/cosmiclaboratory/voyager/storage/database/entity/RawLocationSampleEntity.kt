package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_location_samples",
    foreignKeys = [
        ForeignKey(
            entity = TrackingSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["trackingSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackingSessionId", "capturedAt"]),
        Index(value = ["capturedAt"]),
        Index(value = ["geohash"])
    ]
)
data class RawLocationSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val sampleId: Long = 0,
    val capturedAt: Long,
    val receivedAt: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val verticalAccuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val altitudeM: Double? = null,
    val provider: String, // gps/network/fused/passive
    val isMock: Boolean = false,
    val batteryPct: Int? = null,
    val isCharging: Boolean = false,
    val deviceIdleMode: Boolean = false,
    val permissionSnapshot: String, // fine/coarse/none
    val trackingSessionId: Long,
    val localTimeZone: String,
    val geohash: String
)
