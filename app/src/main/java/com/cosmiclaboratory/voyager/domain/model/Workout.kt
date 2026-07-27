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

/**
 * One GPS fix on a recorded route.
 *
 * [altitudeM] is the best available elevation for this fix (barometric when the device has a
 * pressure sensor, GPS altitude otherwise) — null when neither is available. [accuracyM] is the
 * horizontal accuracy used to gate whether the leg contributes to distance.
 */
data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val timeMs: Long,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null
)

/** Per-km or per-mile unit for splits. */
enum class DistanceUnit(val meters: Double) {
    KM(1000.0),
    MILE(1609.344)
}

/** One completed split (a whole km/mile) of a recorded route. */
data class Split(
    val index: Int,          // 1-based
    val distanceMeters: Double,
    val durationMs: Long,
    val elevationGainM: Double
) {
    /** Seconds per km for this split (unit-normalised so km and mile splits are comparable). */
    val paceSecPerKm: Double
        get() = if (distanceMeters > 0) (durationMs / 1000.0) / (distanceMeters / 1000.0) else 0.0
}

/** Computed summary of a recorded route — see [com.cosmiclaboratory.voyager.domain.usecase.WorkoutStatsCalculator]. */
data class WorkoutStats(
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0
)

/** Live stats surfaced while a workout is being recorded. */
data class LiveWorkoutStats(
    val type: WorkoutType,
    val distanceMeters: Double,
    val durationMs: Long,
    val currentSpeedMps: Float,
    val avgSpeedMps: Float,
    val elevationGainM: Double = 0.0,
    /** Elapsed time excluding auto-/manually-paused spans; pace is computed against this. */
    val movingTimeMs: Long = 0L,
    val isPaused: Boolean = false
) {
    /** Seconds per km at the current average moving pace; null when not yet moving. */
    val avgPaceSecPerKm: Double?
        get() {
            val timeMs = if (movingTimeMs > 0) movingTimeMs else durationMs
            return if (distanceMeters > 0 && timeMs > 0) (timeMs / 1000.0) / (distanceMeters / 1000.0) else null
        }
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
    val notes: String?,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0,
    /** Delta-encoded per-point time offsets (ms from [startedAt]); empty when unavailable. */
    val encodedTimes: String = "",
    /** Delta-encoded per-point altitude (decimetres); empty when no elevation was captured. */
    val encodedAltitudes: String = ""
) {
    val distanceKm: Double get() = distanceMeters / 1000.0

    /** Seconds per km over the whole activity; null when no distance was covered. */
    val avgPaceSecPerKm: Double?
        get() = if (distanceMeters > 0) (durationMs / 1000.0) / distanceKm else null

    /** What to show the user — their title if set, else the type + date is the caller's job. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: type.displayName
}
