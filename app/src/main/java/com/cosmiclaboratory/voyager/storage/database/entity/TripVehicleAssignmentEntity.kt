package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-DRIVE-segment vehicle attribution + tax classification.
 *
 * One row per segmentId — primary-keyed on the segment so a CASCADE delete
 * from the parent segment cleans this up too. `manuallyAssigned = true`
 * locks the assignment against the auto-attribution worker (M3/M6).
 */
@Entity(
    tableName = "trip_vehicle_assignments",
    foreignKeys = [
        ForeignKey(
            entity = MovementSegmentEntity::class,
            parentColumns = ["segmentId"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["vehicleId"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId"]), Index(value = ["assignedAt"])]
)
data class TripVehicleAssignmentEntity(
    @PrimaryKey
    val segmentId: Long,
    val vehicleId: Long,
    val isBusiness: Boolean = false,
    val businessPurpose: String? = null,
    val manuallyAssigned: Boolean = false,
    val assignedAt: Long,
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
    val userId: String = ""
)
