package com.cosmiclaboratory.voyager.domain.util

import kotlin.math.abs
import kotlin.math.sqrt

object DouglasPeuckerSimplifier {

    fun simplify(points: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        if (points.size < 3) return points

        var maxDist = 0.0
        var maxIndex = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }

        return if (maxDist > epsilon) {
            val left = simplify(points.subList(0, maxIndex + 1), epsilon)
            val right = simplify(points.subList(maxIndex, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(
        point: Pair<Double, Double>,
        lineStart: Pair<Double, Double>,
        lineEnd: Pair<Double, Double>
    ): Double {
        val dx = lineEnd.first - lineStart.first
        val dy = lineEnd.second - lineStart.second
        val mag = sqrt(dx * dx + dy * dy)
        if (mag == 0.0) return sqrt(
            (point.first - lineStart.first).let { it * it } +
            (point.second - lineStart.second).let { it * it }
        )
        return abs(
            dx * (lineStart.second - point.second) -
            (lineStart.first - point.first) * dy
        ) / mag
    }
}
