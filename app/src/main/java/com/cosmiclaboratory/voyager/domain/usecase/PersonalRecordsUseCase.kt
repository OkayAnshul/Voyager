package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Achievement
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.BestEffortDistance
import com.cosmiclaboratory.voyager.domain.model.PersonalRecords
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import java.time.LocalDate
import javax.inject.Inject

/**
 * Derives all-time personal records from recorded activities and detects when a single activity
 * sets a new PR. Pure/deterministic (no DAO) so it's unit-tested directly; the ViewModels load the
 * activities and hand them in. This is the private, on-device answer to Strava's leaderboards.
 */
class PersonalRecordsUseCase @Inject constructor() {

    /** All-time records across [activities]. */
    fun compute(activities: List<Activity>): PersonalRecords {
        if (activities.isEmpty()) return PersonalRecords()

        val longest = activities.maxOf { it.distanceMeters }
        val biggestClimb = activities.maxOf { it.elevationGainM }

        val perType = activities
            .groupBy { it.type }
            .mapValues { (_, list) -> list.maxOf { it.distanceMeters } }

        val bestEfforts = HashMap<BestEffortDistance, Long>()
        for (activity in activities) {
            val points = pointsOf(activity)
            for (dist in BestEffortDistance.entries) {
                val effort = fastestEffortMs(points, dist.meters) ?: continue
                val prior = bestEfforts[dist]
                if (prior == null || effort < prior) bestEfforts[dist] = effort
            }
        }

        return PersonalRecords(
            longestDistanceM = longest,
            biggestClimbM = biggestClimb,
            longestStreakDays = longestStreak(activities.map { it.dayKey }),
            bestEfforts = bestEfforts,
            perTypeLongestM = perType,
        )
    }

    /**
     * The records [activity] set relative to every *other* activity in [all] — i.e. the "New PR!"
     * list to celebrate on save (and, for a historical activity, the records it still holds).
     * Empty when there's nothing to compare against (the very first activity) so a single data
     * point isn't over-celebrated.
     */
    fun newAchievements(activity: Activity, all: List<Activity>): List<Achievement> {
        val others = all.filter { it.id != activity.id }
        if (others.isEmpty()) return emptyList()

        val out = mutableListOf<Achievement>()

        // Longest for this activity's mode (e.g. "Longest Run").
        val longestSameType = others.filter { it.type == activity.type }.maxOfOrNull { it.distanceMeters } ?: 0.0
        if (activity.distanceMeters > longestSameType) {
            out += Achievement("Longest ${activity.type.displayName}")
        }
        if (activity.elevationGainM > 0 && activity.elevationGainM > others.maxOf { it.elevationGainM }) {
            out += Achievement("Biggest climb")
        }

        val myPoints = pointsOf(activity)
        for (dist in BestEffortDistance.entries) {
            val mine = fastestEffortMs(myPoints, dist.meters) ?: continue
            val bestOther = others
                .mapNotNull { fastestEffortMs(pointsOf(it), dist.meters) }
                .minOrNull()
            if (bestOther == null || mine < bestOther) out += Achievement("Fastest ${dist.label}")
        }
        return out
    }

    /**
     * Fastest time (ms) to cover [distanceM] anywhere within [points] — a monotonic two-pointer
     * over cumulative distance, taking the tightest window that still spans the distance. Null when
     * the route is shorter than the distance or carries no usable timing.
     */
    fun fastestEffortMs(points: List<RoutePoint>, distanceM: Double): Long? {
        if (points.size < 2) return null
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cum[i] = cum[i - 1] + LocationUtils.calculateDistance(points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng)
        }
        if (cum.last() < distanceM) return null

        var best = Long.MAX_VALUE
        var i = 0
        for (j in points.indices) {
            // Advance the left edge as far as possible while the window still spans the distance.
            while (i + 1 <= j && cum[j] - cum[i + 1] >= distanceM) i++
            if (cum[j] - cum[i] >= distanceM) {
                val dt = points[j].timeMs - points[i].timeMs
                if (dt in 1 until best) best = dt
            }
        }
        return best.takeIf { it != Long.MAX_VALUE }
    }

    /** Longest run of consecutive calendar days that have at least one activity. */
    fun longestStreak(dayKeys: List<String>): Int {
        val days = dayKeys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .distinct()
            .sorted()
        if (days.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (k in 1 until days.size) {
            current = if (days[k - 1].plusDays(1) == days[k]) current + 1 else 1
            if (current > longest) longest = current
        }
        return longest
    }

    private fun pointsOf(activity: Activity): List<RoutePoint> = WorkoutStatsCalculator.reconstruct(
        activity.encodedPolyline, activity.encodedTimes, activity.encodedAltitudes, activity.startedAt
    )
}
