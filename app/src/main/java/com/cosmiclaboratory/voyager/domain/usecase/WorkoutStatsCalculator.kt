package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutStats
import com.cosmiclaboratory.voyager.domain.util.LocationUtils

/**
 * Pure summariser for a recorded route — distance, duration, average and peak speed.
 *
 * Stateless and deterministic so it's trivially unit-testable; [WorkoutRecorder]
 * uses it to finalise an activity, and tests pin the maths directly.
 */
object WorkoutStatsCalculator {

    /**
     * A single implausible jump (GPS glitch / tunnel re-acquire) shouldn't become a
     * 300 m/s "max speed". Segment speeds above this are ignored for the peak.
     */
    private const val MAX_PLAUSIBLE_SPEED_MPS = 50f // ~180 km/h

    fun summarize(points: List<RoutePoint>): WorkoutStats {
        if (points.size < 2) return WorkoutStats(0.0, 0L, 0f, 0f)

        var distance = 0.0
        var maxSpeed = 0f
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val segDist = LocationUtils.calculateDistance(prev.lat, prev.lng, cur.lat, cur.lng)
            distance += segDist
            val dtMs = cur.timeMs - prev.timeMs
            if (dtMs > 0) {
                val segSpeed = LocationUtils.speedMps(segDist, dtMs)
                if (segSpeed in 0f..MAX_PLAUSIBLE_SPEED_MPS && segSpeed > maxSpeed) {
                    maxSpeed = segSpeed
                }
            }
        }
        val duration = points.last().timeMs - points.first().timeMs
        val avgSpeed = if (duration > 0) LocationUtils.speedMps(distance, duration) else 0f
        return WorkoutStats(
            distanceMeters = distance,
            durationMs = duration,
            avgSpeedMps = avgSpeed,
            maxSpeedMps = maxSpeed
        )
    }
}
