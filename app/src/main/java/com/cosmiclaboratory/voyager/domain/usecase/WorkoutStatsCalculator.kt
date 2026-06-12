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
     * 300 m/s "max speed" — nor inflate the total distance. Legs whose implied speed exceeds
     * this (or with a non-positive time delta) are dropped from both distance and peak speed,
     * matching [WorkoutRecorder]'s live accumulation so the saved summary agrees with what the
     * user watched.
     */
    private const val MAX_PLAUSIBLE_SPEED_MPS = 50f // ~180 km/h

    fun summarize(points: List<RoutePoint>): WorkoutStats {
        if (points.size < 2) return WorkoutStats(0.0, 0L, 0f, 0f)

        var distance = 0.0
        var maxSpeed = 0f
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dtMs = cur.timeMs - prev.timeMs
            if (dtMs <= 0) continue // can't validate an out-of-order / zero-duration leg
            val segDist = LocationUtils.calculateDistance(prev.lat, prev.lng, cur.lat, cur.lng)
            val segSpeed = LocationUtils.speedMps(segDist, dtMs)
            if (segSpeed !in 0f..MAX_PLAUSIBLE_SPEED_MPS) continue // drop the glitch leg entirely
            distance += segDist
            if (segSpeed > maxSpeed) maxSpeed = segSpeed
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
