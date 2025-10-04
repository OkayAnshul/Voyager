package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDateTime

/**
 * Entity to store the current real-time state of the user
 * This includes current place, active visit, and tracking status
 * 
 * CRITICAL INTEGRITY: Added foreign key constraints to prevent orphaned references
 */
@Entity(
    tableName = "current_state",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentPlaceId"],
            onDelete = ForeignKey.SET_NULL // When place is deleted, set reference to null
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentVisitId"],
            onDelete = ForeignKey.SET_NULL // When visit is deleted, set reference to null
        )
    ],
    indices = [
        Index(value = ["currentPlaceId"]),
        Index(value = ["currentVisitId"]),
        Index(value = ["isLocationTrackingActive"]),
        Index(value = ["lastUpdated"])
    ]
)
data class CurrentStateEntity(
    @PrimaryKey
    val id: Int = 1, // Always use ID 1 for singleton state
    
    // Current location state
    val currentPlaceId: Long? = null,
    val currentVisitId: Long? = null,
    val lastLocationUpdate: LocalDateTime = LocalDateTime.now(),
    
    // Tracking status
    val isLocationTrackingActive: Boolean = false,
    val trackingStartTime: LocalDateTime? = null,
    
    // Current session information
    val currentSessionStartTime: LocalDateTime? = null,
    val currentPlaceEntryTime: LocalDateTime? = null,
    
    // Statistics for quick access
    val totalLocationsToday: Int = 0,
    val totalPlacesVisitedToday: Int = 0,
    val totalTimeTrackedToday: Long = 0L, // in milliseconds
    
    // Last update timestamp for cache invalidation
    val lastUpdated: LocalDateTime = LocalDateTime.now()
)

/**
 * Enum for tracking status
 */
enum class TrackingStatus {
    STOPPED,
    ACTIVE,
    PAUSED,
    ERROR
}

/**
 * Current place status
 */
enum class PlaceStatus {
    NOT_AT_PLACE,
    ENTERING_PLACE,
    AT_PLACE,
    LEAVING_PLACE
}