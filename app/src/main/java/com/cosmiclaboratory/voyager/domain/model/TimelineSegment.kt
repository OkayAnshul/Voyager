package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

/**
 * Timeline segment representing a meaningful stay at a place
 * Groups consecutive visits at the same place or within a time window
 *
 * This model replaces the "arrived/left" wording with time ranges
 * and shows logical groupings of visits.
 */
data class TimelineSegment(
    val place: Place,
    val timeRange: TimeRange,
    val visits: List<Visit>,  // One or more visits grouped together
    val distanceToNext: Double? = null,  // Distance to next segment in meters
    val travelTimeToNext: Long? = null   // Travel time to next segment in milliseconds
) {
    /**
     * Format distance to next place for display
     * Returns "2.3 km" or "150 m"
     */
    fun formatDistanceToNext(): String {
        val distance = distanceToNext ?: return ""
        return when {
            distance >= 1000 -> String.format("%.1f km", distance / 1000)
            else -> String.format("%.0f m", distance)
        }
    }

    /**
     * Format travel time to next place for display
     * Returns "5 min" or "1h 20min"
     */
    fun formatTravelTimeToNext(): String {
        val travelMs = travelTimeToNext ?: return ""
        val duration = Duration.ofMillis(travelMs)
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}min"
            else -> "${minutes}min"
        }
    }
}

/**
 * Time range for a timeline segment
 * Represents start and end time of a visit or grouped visits
 */
data class TimeRange(
    val start: LocalDateTime,
    val end: LocalDateTime
) {
    /**
     * Duration in minutes
     */
    val durationMinutes: Long
        get() = Duration.between(start, end).toMinutes()

    /**
     * Duration in hours and minutes
     */
    val durationHoursMinutes: Pair<Long, Long>
        get() {
            val duration = Duration.between(start, end)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            return Pair(hours, minutes)
        }

    /**
     * Format time range for display
     * Returns "2:30 PM - 4:15 PM (1h 45m)"
     */
    fun formatDisplay(): String {
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        val startStr = start.format(timeFormatter)
        val endStr = end.format(timeFormatter)
        val duration = formatDuration()
        return "$startStr - $endStr ($duration)"
    }

    /**
     * Format just the time range without duration
     * Returns "2:30 PM - 4:15 PM"
     */
    fun formatTimeRange(): String {
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        val startStr = start.format(timeFormatter)
        val endStr = end.format(timeFormatter)
        return "$startStr - $endStr"
    }

    /**
     * Format duration for display
     * Returns "1h 45m" or "45m"
     */
    fun formatDuration(): String {
        val (hours, minutes) = durationHoursMinutes
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    /**
     * Check if this time range overlaps with another
     */
    fun overlaps(other: TimeRange): Boolean {
        return start.isBefore(other.end) && end.isAfter(other.start)
    }

    /**
     * Check if this time range contains a specific time
     */
    fun contains(time: LocalDateTime): Boolean {
        return !time.isBefore(start) && !time.isAfter(end)
    }
}
