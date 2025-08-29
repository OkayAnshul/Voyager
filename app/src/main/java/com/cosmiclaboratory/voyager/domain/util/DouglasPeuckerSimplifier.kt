package com.cosmiclaboratory.voyager.domain.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Douglas-Peucker polyline simplification.
 * Removes points that are within [epsilonM] meters of the simplified line,
 * reducing point count while preserving route shape.
 */
object DouglasPeuckerSimplifier {

    fun simplify(points: List<Pair<Double, Double>>, epsilonM: Double = 5.0): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        return douglasPeucker(points, epsilonM)
    }

    private fun douglasPeucker(points: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        var maxDist = 0.0
        var maxIdx = 0

        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistanceM(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIdx = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, maxIdx + 1), epsilon)
            val right = douglasPeucker(points.subList(maxIdx, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * Perpendicular distance from point to the line segment (start→end), in meters.
     * Uses a local flat-Earth approximation which is accurate enough for route segments.
     */
    private fun perpendicularDistanceM(
        point: Pair<Double, Double>,
        start: Pair<Double, Double>,
        end: Pair<Double, Double>
    ): Double {
        val midLat = (start.first + end.first) / 2.0
        val cosLat = cos(Math.toRadians(midLat))
        val mPerDegLat = 111_320.0
        val mPerDegLng = 111_320.0 * cosLat

        // Convert to local meters
        val px = (point.second - start.second) * mPerDegLng
        val py = (point.first - start.first) * mPerDegLat
        val ex = (end.second - start.second) * mPerDegLng
        val ey = (end.first - start.first) * mPerDegLat

        val lineLen = sqrt(ex * ex + ey * ey)
        if (lineLen == 0.0) return sqrt(px * px + py * py)

        return abs(ey * px - ex * py) / lineLen
    }
}
