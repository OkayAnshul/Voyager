package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Domain model representing the current real-time state of the user
 */
data class CurrentState(
    val id: Int = 1,
    
    // Current location state
    val currentPlace: Place? = null,
    val currentVisit: Visit? = null,
    val lastLocationUpdate: LocalDateTime = LocalDateTime.now(),
    
    // Tracking status
    val isLocationTrackingActive: Boolean = false,
    val trackingStartTime: LocalDateTime? = null,
    
    // Current session information
    val currentSessionStartTime: LocalDateTime? = null,
    val currentPlaceEntryTime: LocalDateTime? = null,
    
    // Daily statistics
    val totalLocationsToday: Int = 0,
    val totalPlacesVisitedToday: Int = 0,
    val totalTimeTrackedToday: Long = 0L, // in milliseconds
    
    // Derived properties
    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    
    /**
     * Check if currently at a place
     * Based on whether there's a current place and entry time (indicating active visit)
     */
    val isAtPlace: Boolean
        get() = currentPlace != null && currentPlaceEntryTime != null
    
    /**
     * Get current visit duration if active
     * Calculate from currentPlaceEntryTime if at a place
     */
    val currentVisitDuration: Long
        get() = if (isAtPlace && currentPlaceEntryTime != null) {
            java.time.Duration.between(currentPlaceEntryTime, LocalDateTime.now()).toMillis()
        } else {
            currentVisit?.getCurrentDuration() ?: 0L
        }
    
    /**
     * Get tracking session duration
     */
    val trackingSessionDuration: Long
        get() = if (isLocationTrackingActive && trackingStartTime != null) {
            java.time.Duration.between(trackingStartTime, LocalDateTime.now()).toMillis()
        } else 0L
    
    /**
     * Get time since last location update
     */
    val timeSinceLastLocation: Long
        get() = java.time.Duration.between(lastLocationUpdate, LocalDateTime.now()).toMillis()
    
    /**
     * Check if location data is stale (> 5 minutes)
     */
    val isLocationDataStale: Boolean
        get() = timeSinceLastLocation > 5 * 60 * 1000L // 5 minutes
    
    /**
     * Get today's total time as formatted string
     */
    val todayTimeFormatted: String
        get() {
            val hours = totalTimeTrackedToday / (1000 * 60 * 60)
            val minutes = (totalTimeTrackedToday % (1000 * 60 * 60)) / (1000 * 60)
            return "${hours}h ${minutes}m"
        }
    
    /**
     * Get current visit time as formatted string
     */
    val currentVisitTimeFormatted: String
        get() {
            val duration = currentVisitDuration
            val hours = duration / (1000 * 60 * 60)
            val minutes = (duration % (1000 * 60 * 60)) / (1000 * 60)
            return "${hours}h ${minutes}m"
        }
}

/**
 * Simplified state for UI display
 */
data class DashboardState(
    val isTracking: Boolean = false,
    val currentPlaceName: String? = null,
    val currentVisitDuration: String = "0h 0m",
    val todayTotalTime: String = "0h 0m",
    val locationsToday: Int = 0,
    val placesVisitedToday: Int = 0,
    val lastLocationTime: LocalDateTime? = null,
    val isLocationDataFresh: Boolean = false
)