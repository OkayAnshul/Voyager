package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class TimeAnalytics(
    val totalTimeTracked: Long, // in milliseconds
    val timeByCategory: Map<PlaceCategory, Long>,
    val timeByPlace: Map<Place, Long>,
    val averageDailyMovement: Long,
    val mostVisitedPlaces: List<PlaceRanking>,
    val peakActivityHours: List<HourActivity>
)

data class PlaceRanking(
    val place: Place,
    val totalTime: Long,
    val visitCount: Int,
    val rank: Int
)

data class HourActivity(
    val hour: Int, // 0-23
    val activityLevel: Float, // 0.0-1.0
    val averageLocations: Int
)

data class MovementPattern(
    val id: Long = 0L,
    val patternType: PatternType,
    val name: String,
    val confidence: Float,
    val frequency: Int,
    val locations: List<Location>,
    val timePattern: TimePattern?,
    val detectedAt: LocalDateTime
)

data class TimePattern(
    val startTime: java.time.LocalTime,
    val endTime: java.time.LocalTime,
    val daysOfWeek: Set<java.time.DayOfWeek>
)

enum class PatternType {
    COMMUTE,
    ROUTINE,
    WEEKEND_PATTERN,
    FREQUENT_ROUTE,
    WORK_SCHEDULE,
    EXERCISE_ROUTINE
}

data class DayAnalytics(
    val date: LocalDate,
    val totalTimeTracked: Long,
    val placesVisited: Int,
    val distanceTraveled: Double, // in meters
    val timeByCategory: Map<PlaceCategory, Long>,
    val longestStay: Visit?,
    val mostFrequentPlace: Place?
)

data class CurrentStateAnalytics(
    val isAtPlace: Boolean = false,
    val currentPlace: Place? = null,
    val currentVisitDuration: Long = 0L,
    val todayTimeTracked: Long = 0L,
    val todayPlacesVisited: Int = 0,
    val isLocationTrackingActive: Boolean = false
)