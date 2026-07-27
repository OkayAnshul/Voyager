package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * "Your typical day" — for each hour of the day, where the user usually is,
 * split into a weekday and a weekend profile. The signature timeline read: a
 * 24-slot band that shows Home overnight, Work through the day, Out in the
 * evening, and gaps where they're on the move.
 *
 * Each hour's state is the most common one across the lookback, derived from the
 * visit that covers that hour's midpoint. AWAY = no visit covers it (moving or
 * untracked) — shown honestly rather than guessed.
 */
enum class DayState { HOME, WORK, OUT, AWAY }

data class HourSlot(val hour: Int, val state: DayState, val confidence: Float)

data class DayRhythm(
    val weekday: List<HourSlot>,   // 24 slots, hour 0..23
    val weekend: List<HourSlot>,   // 24 slots, hour 0..23
    val daysObserved: Int
)

class ComputeDayRhythmUseCase @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
) {
    companion object {
        const val LOOKBACK_DAYS = 28
        /** Need at least this many days with visits before drawing a "typical day". */
        const val MIN_DAYS = 7
    }

    suspend fun analyze(zone: ZoneId = ZoneId.systemDefault()): DayRhythm? {
        val home = placeDao.getHomePlace()
        val workIds = placeDao.getByCategory("WORK").map { it.placeId }.toSet()
        val end = LocalDate.now(zone)
        val start = end.minusDays((LOOKBACK_DAYS - 1).toLong())
        val nowMs = System.currentTimeMillis()

        // Include the day before the window so overnight stays cover early hours.
        val visits = mutableListOf<VisitEntity>()
        var day = start.minusDays(1)
        while (!day.isAfter(end)) {
            visits += visitDao.getByDayKey(day.toString())
            day = day.plusDays(1)
        }
        if (visits.map { it.dayKey }.distinct().size < MIN_DAYS) return null

        return compute(visits, home?.placeId, workIds, start, end, nowMs, zone)
    }

    /** Pure — exposed for tests and callers holding the visit list. */
    fun compute(
        visits: List<VisitEntity>,
        homeId: Long?,
        workIds: Set<Long>,
        start: LocalDate,
        end: LocalDate,
        nowMs: Long,
        zone: ZoneId
    ): DayRhythm {
        // counts[isWeekend][hour][stateOrdinal]
        val counts = Array(2) { Array(24) { IntArray(4) } }
        val observedDates = HashSet<LocalDate>()

        var date = start
        while (!date.isAfter(end)) {
            val weekendIdx = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) 1 else 0
            for (hour in 0..23) {
                val instant = date.atTime(hour, 30).atZone(zone).toInstant().toEpochMilli()
                if (instant > nowMs) continue // don't judge hours that haven't happened
                val state = stateAt(instant, visits, homeId, workIds, nowMs)
                counts[weekendIdx][hour][state.ordinal]++
                observedDates += date
            }
            date = date.plusDays(1)
        }

        return DayRhythm(
            weekday = buildBand(counts[0]),
            weekend = buildBand(counts[1]),
            daysObserved = observedDates.size
        )
    }

    private fun stateAt(
        instant: Long,
        visits: List<VisitEntity>,
        homeId: Long?,
        workIds: Set<Long>,
        nowMs: Long
    ): DayState {
        val covering = visits.firstOrNull { v ->
            v.placeId != 0L && v.arrivalAt <= instant && instant < (v.departureAt ?: nowMs)
        } ?: return DayState.AWAY
        return when {
            covering.placeId == homeId -> DayState.HOME
            covering.placeId in workIds -> DayState.WORK
            else -> DayState.OUT
        }
    }

    private fun buildBand(hourCounts: Array<IntArray>): List<HourSlot> = (0..23).map { hour ->
        val bucket = hourCounts[hour]
        val total = bucket.sum()
        if (total == 0) {
            HourSlot(hour, DayState.AWAY, 0f)
        } else {
            val topOrdinal = bucket.indices.maxByOrNull { bucket[it] }!!
            HourSlot(hour, DayState.values()[topOrdinal], bucket[topOrdinal].toFloat() / total)
        }
    }
}
