package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

data class Place(
    val id: Long = 0L,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0L, // in milliseconds
    val lastVisit: LocalDateTime? = null,
    val isCustom: Boolean = false,
    val radius: Double = 100.0, // in meters
    val placeId: String? = null, // Google Places ID
    val confidence: Float = 1.0f // Confidence level for automatic categorization (0.0 - 1.0)
)

enum class PlaceCategory {
    HOME,
    WORK,
    GYM,
    RESTAURANT,
    SHOPPING,
    ENTERTAINMENT,
    HEALTHCARE,
    EDUCATION,
    TRANSPORT,
    TRAVEL,
    OUTDOOR,
    SOCIAL,
    SERVICES,
    UNKNOWN,
    CUSTOM
}

data class PlaceWithVisits(
    val place: Place,
    val visits: List<Visit>
)