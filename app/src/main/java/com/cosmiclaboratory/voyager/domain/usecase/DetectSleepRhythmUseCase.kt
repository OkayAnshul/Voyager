package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * "Home overnight" rhythm — a privacy-respecting proxy for sleep, derived purely
 * from time spent at the HOME place across the night.
 *
 * This is deliberately *not* medical sleep tracking: all we can observe is when
 * the phone was home, so every label says "home overnight", never "asleep". A
 * night counts only when a single home stay spans the core of the night (it
 * contains local 03:00), which filters out evening visits that end before bed.
 */
data class SleepRhythm(
    val nightsAnalyzed: Int,
    /** Median duration of the overnight home stay, in milliseconds. */
    val medianOvernightMs: Long,
    /** Median evening "settle in for the night" clock time, minute-of-day 0..1439, or null. */
    val settleMinuteOfDay: Int?,
    /** Median morning "up and out" clock time, minute-of-day 0..1439, or null. */
    val wakeMinuteOfDay: Int?,
    val consistency: Consistency
) {
    enum class Consistency { CONSISTENT, VARIABLE, UNKNOWN }
}

class DetectSleepRhythmUseCase @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
) {
    companion object {
        const val LOOKBACK_DAYS = 30
        /** Fewer nights than this and we don't claim a rhythm at all. */
        const val MIN_NIGHTS = 5
        /** Wake-time std dev (minutes) at or below which the rhythm reads as consistent. */
        const val CONSISTENT_WAKE_STDDEV_MIN = 60.0
        /** The instant a stay must contain to count as "overnight". */
        private const val NIGHT_ANCHOR_HOUR = 3
        /** Anchor for the circular median of settle time (18:00), so 23:50 and 00:10 average sanely. */
        private const val EVENING_ANCHOR_MIN = 18 * 60
    }

    suspend fun forHome(zone: ZoneId = ZoneId.systemDefault()): SleepRhythm? {
        val home = placeDao.getHomePlace() ?: return null
        val cutoff = System.currentTimeMillis() - LOOKBACK_DAYS.toLong() * 86_400_000L
        val visits = visitDao.getByPlaceId(home.placeId)
            .filter { it.departureAt != null && it.arrivalAt >= cutoff }
        return detect(visits, zone)
    }

    /** Pure — exposed for tests and callers already holding the home visit list. */
    fun detect(homeVisits: List<VisitEntity>, zone: ZoneId = ZoneId.systemDefault()): SleepRhythm? {
        val overnight = homeVisits.filter { v ->
            val dep = v.departureAt ?: return@filter false
            containsNightAnchor(v.arrivalAt, dep, zone)
        }
        if (overnight.size < MIN_NIGHTS) return null

        val durations = overnight.map { it.departureAt!! - it.arrivalAt }.sorted()
        val medianDuration = durations[durations.size / 2]

        // Settle time — evening arrival, circular-median around 18:00 so times
        // either side of midnight don't average to noon.
        val settleOffsets = overnight.mapNotNull { v ->
            val t = Instant.ofEpochMilli(v.arrivalAt).atZone(zone).toLocalTime()
            if (t.hour in 18..23 || t.hour in 0..3) {
                ((t.toSecondOfDay() / 60) - EVENING_ANCHOR_MIN + 1440) % 1440
            } else null
        }
        val settleMinute = median(settleOffsets)?.let { (it + EVENING_ANCHOR_MIN) % 1440 }

        // Wake time — morning departure, straightforward minute-of-day.
        val wakeMinutes = overnight.mapNotNull { v ->
            val t = Instant.ofEpochMilli(v.departureAt!!).atZone(zone).toLocalTime()
            if (t.hour in 3..12) t.toSecondOfDay() / 60 else null
        }
        val wakeMinute = median(wakeMinutes)
        val consistency = when {
            wakeMinutes.size < MIN_NIGHTS -> SleepRhythm.Consistency.UNKNOWN
            stdDev(wakeMinutes) <= CONSISTENT_WAKE_STDDEV_MIN -> SleepRhythm.Consistency.CONSISTENT
            else -> SleepRhythm.Consistency.VARIABLE
        }

        return SleepRhythm(
            nightsAnalyzed = overnight.size,
            medianOvernightMs = medianDuration,
            settleMinuteOfDay = settleMinute,
            wakeMinuteOfDay = wakeMinute,
            consistency = consistency
        )
    }

    private fun containsNightAnchor(arrival: Long, departure: Long, zone: ZoneId): Boolean {
        val startDate = Instant.ofEpochMilli(arrival).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(departure).atZone(zone).toLocalDate()
        var d = startDate
        while (!d.isAfter(endDate)) {
            val anchor = d.atTime(NIGHT_ANCHOR_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            if (anchor in arrival..departure) return true
            d = d.plusDays(1)
        }
        return false
    }

    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        return s[s.size / 2]
    }

    private fun stdDev(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
