package com.cosmiclaboratory.voyager.domain.model

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TimeRange(
    val start: LocalDateTime,
    val end: LocalDateTime
) {
    fun formatTimeRange(): String {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        return "${start.format(fmt)} – ${end.format(fmt)}"
    }
    fun formatDuration(): String {
        val dur = Duration.between(start, end)
        val h = dur.toHours()
        val m = dur.toMinutes() % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

/**
 * Unified segment model for the Movement Truth timeline.
 * Every moment of the user's day is represented as one of these segment types,
 * producing a gap-free narrative of their movement patterns.
 */
data class TrackingStateSegment(
    val type: TrackingSegmentType,
    val timeRange: TimeRange,
    /** Place reference for CONFIRMED_PLACE_VISIT segments */
    val place: Place? = null,
    /** Visit(s) for CONFIRMED_PLACE_VISIT segments */
    val visits: List<Visit> = emptyList(),
    /** Best-available address label for TRANSIENT_STOP / TRANSIT */
    val addressLabel: String? = null,
    /** Confidence score 0.0–1.0 for inferred segments */
    val confidence: Double = 1.0,
    /** Explanation metadata for UI display */
    val explanation: String? = null,
    // Transit-specific fields (speed from GPS Location.speed)
    val averageSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val totalDistanceMeters: Double? = null,
    /** Secondary hint from activity recognition — used only when GPS speed unavailable */
    val activityHint: UserActivity? = null,
    /** Coordinates summary for map rendering */
    val coordinates: List<Pair<Double, Double>> = emptyList()
) {
    /** Duration of this segment */
    val duration: Duration
        get() = Duration.between(timeRange.start, timeRange.end)

    /** Duration in milliseconds */
    val durationMs: Long
        get() = duration.toMillis()

    /** Human-readable duration string */
    fun formatDuration(): String = timeRange.formatDuration()

    /** Human-readable summary for UI cards */
    fun formatSummary(): String = when (type) {
        TrackingSegmentType.CONFIRMED_PLACE_VISIT -> {
            val name = place?.name ?: "Unknown place"
            "$name - ${formatDuration()}"
        }
        TrackingSegmentType.TRANSIT -> {
            val dist = totalDistanceMeters?.let { formatDistance(it) } ?: ""
            val speed = averageSpeedKmh?.let { "at avg ${it.toInt()} km/h" } ?: ""
            listOfNotNull(
                "Traveled",
                dist.ifEmpty { null },
                speed.ifEmpty { null },
                "- ${formatDuration()}"
            ).joinToString(" ")
        }
        TrackingSegmentType.TRANSIENT_STOP -> {
            val addr = addressLabel ?: "Unknown location"
            "Paused ${formatDuration()} - $addr"
        }
        TrackingSegmentType.UNTRACKED_WHILE_TRACKING -> {
            explanation ?: "No location data - ${formatDuration()}"
        }
        TrackingSegmentType.NOT_TRACKING -> {
            explanation ?: "Tracking was off - ${formatDuration()}"
        }
    }

    companion object {
        fun formatDistance(meters: Double): String = when {
            meters >= 1000 -> String.format("%.1f km", meters / 1000)
            else -> String.format("%.0f m", meters)
        }
    }
}

/**
 * The five segment types that fully describe a user's day.
 */
enum class TrackingSegmentType {
    /** Tracking was not enabled during this period */
    NOT_TRACKING,
    /** Tracking was on but no location data was received (GPS lost, etc.) */
    UNTRACKED_WHILE_TRACKING,
    /** User was moving between locations — carries speed/distance from GPS */
    TRANSIT,
    /** Brief stationary period without a confirmed place (> 2 min, < threshold) */
    TRANSIENT_STOP,
    /** User was at a known, confirmed place with visit record */
    CONFIRMED_PLACE_VISIT
}
