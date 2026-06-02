package com.cosmiclaboratory.voyager.domain.usecase

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure multi-vehicle attribution engine (Wave 10 M6).
 *
 * A user with a car and a bike (or a personal vs work car) registers a
 * small set of rules — speed bands, time-of-day windows, days-of-week —
 * and this engine picks the best vehicleId for a closed DRIVE segment.
 *
 * Rules carry a priority; the highest-priority *matching* rule wins.
 * When nothing matches the engine returns null and the caller falls back
 * to the global default vehicle (set by [VehicleDao.getDefault]).
 *
 * Lives in `domain/` — no DAOs, no entities, fully testable in isolation.
 */
object VehicleAutoAssignmentEngine {

    sealed class Predicate {
        /** Match when segment max speed (m/s) ≥ threshold. */
        data class SpeedMin(val mps: Float) : Predicate()
        /** Match when segment max speed (m/s) ≤ threshold. */
        data class SpeedMax(val mps: Float) : Predicate()
        /**
         * Match when segment start hour-of-day is within [startHourInclusive, endHourExclusive).
         * Wraps midnight cleanly: TimeWindow(22, 6) matches 22:00..05:59.
         */
        data class TimeWindow(val startHourInclusive: Int, val endHourExclusive: Int) : Predicate()
        /** Match when segment start day-of-week is in [days] (Calendar.SUNDAY..SATURDAY). */
        data class DayOfWeek(val days: Set<Int>) : Predicate()
    }

    data class Rule(
        val vehicleId: Long,
        val priority: Int,
        val predicates: List<Predicate>,
    )

    /** Feature vector for a single segment. The caller projects from the entity. */
    data class SegmentFeatures(
        val maxSpeedMps: Float?,
        val startAtMs: Long,
    )

    /**
     * Picks the highest-priority rule whose predicates all match. Predicates
     * with insufficient input (e.g. SpeedMin when maxSpeedMps is null) are
     * treated as "non-matching" rather than "match-anything" — a missing
     * signal cannot promote a rule.
     */
    fun pickVehicle(
        features: SegmentFeatures,
        rules: List<Rule>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = features.startAtMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return rules
            .filter { matchesAll(it.predicates, features, hour, dow) }
            .maxByOrNull { it.priority }
            ?.vehicleId
    }

    private fun matchesAll(
        predicates: List<Predicate>,
        features: SegmentFeatures,
        hour: Int,
        dow: Int,
    ): Boolean {
        for (predicate in predicates) {
            if (!matches(predicate, features, hour, dow)) return false
        }
        return true
    }

    private fun matches(
        predicate: Predicate,
        features: SegmentFeatures,
        hour: Int,
        dow: Int,
    ): Boolean = when (predicate) {
        is Predicate.SpeedMin -> features.maxSpeedMps != null && features.maxSpeedMps >= predicate.mps
        is Predicate.SpeedMax -> features.maxSpeedMps != null && features.maxSpeedMps <= predicate.mps
        is Predicate.TimeWindow -> hourIn(hour, predicate.startHourInclusive, predicate.endHourExclusive)
        is Predicate.DayOfWeek -> dow in predicate.days
    }

    /** Wrap-aware hour-window match: window(22,6) matches 22..23 OR 0..5. */
    private fun hourIn(hour: Int, startInclusive: Int, endExclusive: Int): Boolean =
        if (startInclusive < endExclusive) {
            hour in startInclusive until endExclusive
        } else {
            hour >= startInclusive || hour < endExclusive
        }
}
