package com.cosmiclaboratory.voyager.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Represents a detected behavioral pattern for a specific place
 *
 * Patterns help users understand their habits:
 * - "You visit Gym on Mon/Wed/Fri at 6 PM"
 * - "You're at Home every night after 10 PM"
 * - "You go to Coffee Shop every weekday at 8 AM"
 */
data class PlacePattern(
    val place: Place,
    val patternType: PlacePatternType,
    val description: String,
    val confidence: Float,  // 0.0 to 1.0 (percentage of time pattern holds)
    val details: PatternDetails
)

/**
 * Type of pattern detected
 */
enum class PlacePatternType {
    DAY_OF_WEEK,      // Visits on specific days (e.g., Mon/Wed/Fri)
    TIME_OF_DAY,      // Visits at specific times (e.g., 8 AM)
    FREQUENCY,        // Regular visit frequency (e.g., 3x per week)
    DAILY_ROUTINE     // Daily occurrence (e.g., every night)
}

/**
 * Detailed pattern information
 */
sealed class PatternDetails {
    /**
     * Pattern for specific days of the week
     */
    data class DayOfWeekPattern(
        val days: List<DayOfWeek>,  // e.g., [MONDAY, WEDNESDAY, FRIDAY]
        val typicalTime: LocalTime? = null  // Average time of visits on these days
    ) : PatternDetails()

    /**
     * Pattern for specific time of day
     */
    data class TimeOfDayPattern(
        val time: LocalTime,
        val timeWindow: Int  // Minutes of variation (e.g., ±30 minutes)
    ) : PatternDetails()

    /**
     * Pattern for visit frequency
     */
    data class FrequencyPattern(
        val visitsPerWeek: Float,  // Average visits per week
        val isRegular: Boolean  // True if frequency is consistent
    ) : PatternDetails()

    /**
     * Pattern for daily routines
     */
    data class DailyRoutinePattern(
        val timeRange: Pair<LocalTime, LocalTime>,  // e.g., 10 PM - 7 AM for sleep
        val occursDaily: Boolean  // True if happens every day
    ) : PatternDetails()
}

/**
 * Helper to format pattern confidence as percentage
 */
fun Float.formatConfidence(): String {
    return "${(this * 100).toInt()}%"
}

/**
 * Helper to format days of week
 */
fun List<DayOfWeek>.formatDays(): String {
    if (isEmpty()) return ""
    if (size == 7) return "Every day"

    // Check for weekdays/weekends
    val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                         DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val weekends = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    return when {
        this.containsAll(weekdays) && size == 5 -> "Weekdays"
        this.containsAll(weekends) && size == 2 -> "Weekends"
        size == 1 -> this[0].name.lowercase().replaceFirstChar { it.uppercase() }
        else -> this.joinToString("/") {
            it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }
        }
    }
}

/**
 * Helper to format time
 */
fun LocalTime.formatTime(): String {
    val hour = if (this.hour == 0) 12 else if (this.hour > 12) this.hour - 12 else this.hour
    val amPm = if (this.hour < 12) "AM" else "PM"
    val minute = if (this.minute == 0) "" else ":${this.minute.toString().padStart(2, '0')}"
    return "$hour$minute $amPm"
}

/**
 * Helper to generate confidence strength indicator
 */
fun Float.toConfidenceStrength(): String {
    return when {
        this >= 0.8f -> "Very Strong"
        this >= 0.6f -> "Strong"
        this >= 0.4f -> "Moderate"
        this >= 0.2f -> "Weak"
        else -> "Very Weak"
    }
}
