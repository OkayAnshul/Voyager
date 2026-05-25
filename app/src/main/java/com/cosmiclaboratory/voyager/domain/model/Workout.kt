package com.cosmiclaboratory.voyager.domain.model

/** The kind of activity a user records — the Athlete persona's Strava-style modes. */
enum class WorkoutType(val displayName: String) {
    RUN("Run"),
    WALK("Walk"),
    CYCLE("Cycle"),
    HIKE("Hike"),
    OTHER("Workout");

    companion object {
        fun fromName(name: String?): WorkoutType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** One GPS fix on a recorded route. */
data class RoutePoint(val lat: Double, val lng: Double, val timeMs: Long)

/** Computed summary of a recorded route — see [com.cosmiclaboratory.voyager.domain.usecase.WorkoutStatsCalculator]. */
data class WorkoutStats(
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float
)

/** Live stats surfaced while a workout is being recorded. */
data class LiveWorkoutStats(
    val type: WorkoutType,
    val distanceMeters: Double,
    val durationMs: Long,
    val currentSpeedMps: Float,
    val avgSpeedMps: Float
) {
    /** Seconds per km at the current average pace; null when not yet moving. */
    val avgPaceSecPerKm: Double?
        get() = if (distanceMeters > 0) (durationMs / 1000.0) / (distanceMeters / 1000.0) else null
}

/** A completed, recorded workout — the Athlete persona's analogue to a Strava activity. */
data class Activity(
    val id: Long,
    val type: WorkoutType,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val steps: Int?,
    val encodedPolyline: String,
    val dayKey: String,
    val title: String?,
    val notes: String?
) {
    val distanceKm: Double get() = distanceMeters / 1000.0

    /** Seconds per km over the whole activity; null when no distance was covered. */
    val avgPaceSecPerKm: Double?
        get() = if (distanceMeters > 0) (durationMs / 1000.0) / distanceKm else null

    /** What to show the user — their title if set, else the type + date is the caller's job. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: type.displayName
}
