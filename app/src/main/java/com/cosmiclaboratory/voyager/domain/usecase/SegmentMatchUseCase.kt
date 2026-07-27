package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.SegmentEffort
import com.cosmiclaboratory.voyager.domain.model.WorkoutSegment
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import javax.inject.Inject

/**
 * Matches a saved [WorkoutSegment]'s reference path against recorded activities and times each
 * traversal — the private, on-device "race yourself" engine (no leaderboard, no other users).
 *
 * Matching is a pragmatic Fréchet-lite: resample the segment into evenly-spaced points, then walk
 * the activity route monotonically, requiring each resampled point to have a nearby route fix
 * within [DEFAULT_TOLERANCE_M]. The effort time is the route timestamp span between the first and
 * last matched fixes. Pure/deterministic so it's unit-tested directly.
 */
class SegmentMatchUseCase @Inject constructor() {

    private companion object {
        const val SAMPLE_COUNT = 20
        const val DEFAULT_TOLERANCE_M = 25.0
    }

    /** Efforts for [segment] across [activities], fastest first. Activities without per-point
     *  timing (e.g. materialised from passive tracking) can't be timed and are skipped. */
    fun effortsFor(segment: WorkoutSegment, activities: List<Activity>): List<SegmentEffort> {
        val path = PolylineEncoder.decode(segment.encodedPolyline)
        if (path.size < 2) return emptyList()
        return activities.mapNotNull { activity ->
            val route = WorkoutStatsCalculator.reconstruct(
                activity.encodedPolyline, activity.encodedTimes, activity.encodedAltitudes, activity.startedAt
            )
            val timeMs = match(path, route) ?: return@mapNotNull null
            SegmentEffort(activityId = activity.id, startedAt = activity.startedAt, timeMs = timeMs)
        }.sortedBy { it.timeMs }
    }

    /** Traversal time (ms) of [segmentPath] within [route], or null if the route doesn't cover it. */
    fun match(
        segmentPath: List<Pair<Double, Double>>,
        route: List<RoutePoint>,
        toleranceM: Double = DEFAULT_TOLERANCE_M,
    ): Long? {
        if (segmentPath.size < 2 || route.size < 2) return null
        val samples = resample(segmentPath, SAMPLE_COUNT)

        var searchStart = 0
        var startTime = -1L
        var endTime = -1L
        for ((k, sample) in samples.withIndex()) {
            var bestIdx = -1
            var bestDist = Double.MAX_VALUE
            for (i in searchStart until route.size) {
                val d = LocationUtils.calculateDistance(sample.first, sample.second, route[i].lat, route[i].lng)
                if (d < bestDist) { bestDist = d; bestIdx = i }
            }
            if (bestIdx < 0 || bestDist > toleranceM) return null
            if (k == 0) startTime = route[bestIdx].timeMs
            if (k == samples.lastIndex) endTime = route[bestIdx].timeMs
            searchStart = bestIdx // enforce monotonic progression along the route
        }
        return if (startTime >= 0 && endTime > startTime) endTime - startTime else null
    }

    /** Evenly resample [path] into [n] points by cumulative distance. */
    private fun resample(path: List<Pair<Double, Double>>, n: Int): List<Pair<Double, Double>> {
        val cum = DoubleArray(path.size)
        for (i in 1 until path.size) {
            cum[i] = cum[i - 1] + LocationUtils.calculateDistance(path[i - 1].first, path[i - 1].second, path[i].first, path[i].second)
        }
        val total = cum.last()
        if (total <= 0.0) return List(n) { path.first() }

        val out = ArrayList<Pair<Double, Double>>(n)
        for (k in 0 until n) {
            val target = total * k / (n - 1)
            var i = 1
            while (i < path.size && cum[i] < target) i++
            if (i >= path.size) { out.add(path.last()); continue }
            val segStart = cum[i - 1]
            val segEnd = cum[i]
            val frac = if (segEnd > segStart) (target - segStart) / (segEnd - segStart) else 0.0
            val lat = path[i - 1].first + (path[i].first - path[i - 1].first) * frac
            val lng = path[i - 1].second + (path[i].second - path[i - 1].second) * frac
            out.add(lat to lng)
        }
        return out
    }
}
