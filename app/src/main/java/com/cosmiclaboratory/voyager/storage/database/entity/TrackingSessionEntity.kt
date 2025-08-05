package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_sessions")
data class TrackingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startedBy: String, // USER/BOOT/CRASH_RESTORE/SCHEDULE/PERMISSION_REGAINED
    val endedBy: String? = null, // USER/PERMISSION_LOST/BATTERY_CRITICAL/SYSTEM_KILL/ERROR
    val interruptionReason: String? = null,
    val restartReason: String? = null,
    val localTimeZone: String,
    val totalSamples: Int = 0,
    val totalDistanceM: Double = 0.0
)
