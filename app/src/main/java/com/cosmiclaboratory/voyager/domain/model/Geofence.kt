package com.cosmiclaboratory.voyager.domain.model

data class Geofence(
    val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double, // in meters
    val isActive: Boolean = true,
    val enterAlert: Boolean = false,
    val exitAlert: Boolean = false,
    val placeId: Long? = null
)

data class GeofenceEvent(
    val id: Long = 0L,
    val geofenceId: Long,
    val eventType: GeofenceEventType,
    val timestamp: java.time.LocalDateTime,
    val accuracy: Float
)

enum class GeofenceEventType {
    ENTER,
    EXIT,
    DWELL
}