package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_log")
data class HealthLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val eventType: String, // SAMPLE_GAP/WORKER_FAILURE/PERMISSION_CHANGE/BATTERY_CRITICAL/CRASH_RESTORE/WATCHDOG_TRIGGER
    val eventAt: Long,
    val detailsJson: String? = null,
    val acknowledged: Boolean = false
)
