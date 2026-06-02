package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Service / maintenance entries per vehicle — driving service-due reminders
 * (M7). User-authored; the system never auto-writes these.
 */
@Entity(
    tableName = "vehicle_service_log",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["vehicleId"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId", "serviceAt"])]
)
data class VehicleServiceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val serviceId: Long = 0,
    val vehicleId: Long,
    val serviceAt: Long,
    val odometerKm: Double?,
    /** OIL_CHANGE / TIRE_ROTATION / BRAKE_SERVICE / INSPECTION / OTHER */
    val serviceType: String,
    val costMinor: Long? = null,
    val currencyCode: String? = null,
    val notes: String? = null,
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
    val userId: String = ""
)
