package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * "Where your hours go" over a period: waking time split across Home, Work,
 * other places (Out), time Moving, and whatever's left as Untracked.
 *
 * A far more meaningful answer than the old "variety score" — it uses the dwell
 * already stored on visits (clamped to the period window so an overnight stay
 * isn't over-counted, the same T11 rule the daily rollups use) plus the
 * walk/drive/transit milliseconds from the daily rollups.
 */
data class TimeBudget(
    val homeMs: Long,
    val workMs: Long,
    val outMs: Long,
    val movingMs: Long,
    val untrackedMs: Long,
) {
    val totalMs: Long get() = homeMs + workMs + outMs + movingMs + untrackedMs
    val isEmpty: Boolean get() = homeMs + workMs + outMs + movingMs == 0L
}

class ComputeTimeBudgetUseCase @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val dailyRollupDao: DailyRollupDao,
) {

    suspend fun analyze(range: DateRange, zone: ZoneId = ZoneId.systemDefault()): TimeBudget {
        val start = LocalDate.parse(range.startDay)
        val end = LocalDate.parse(range.endDay)
        val windowStart = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = minOf(
            System.currentTimeMillis(),
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )

        val home = placeDao.getHomePlace()
        val workIds = placeDao.getByCategory("WORK").map { it.placeId }.toSet()

        val visits = mutableListOf<VisitEntity>()
        var day = start
        while (!day.isAfter(end)) {
            visits += visitDao.getByDayKey(day.toString())
            day = day.plusDays(1)
        }

        val movingMs = dailyRollupDao.getByRange(range.startDay, range.endDay)
            .sumOf { it.totalWalkMs + it.totalDriveMs + it.totalTransitMs }

        return compute(visits, home?.placeId, workIds, movingMs, windowStart, windowEnd)
    }

    /** Pure — exposed for tests and callers holding the visit list. */
    fun compute(
        visits: List<VisitEntity>,
        homeId: Long?,
        workIds: Set<Long>,
        movingMs: Long,
        windowStartMs: Long,
        windowEndMs: Long
    ): TimeBudget {
        var home = 0L
        var work = 0L
        var out = 0L
        for (v in visits) {
            if (v.placeId == 0L) continue
            val end = v.departureAt ?: windowEndMs
            val overlap = overlapMs(v.arrivalAt, end, windowStartMs, windowEndMs)
            if (overlap <= 0L) continue
            when {
                v.placeId == homeId -> home += overlap
                v.placeId in workIds -> work += overlap
                else -> out += overlap
            }
        }
        val windowMs = (windowEndMs - windowStartMs).coerceAtLeast(0L)
        val untracked = (windowMs - home - work - out - movingMs).coerceAtLeast(0L)
        return TimeBudget(
            homeMs = home,
            workMs = work,
            outMs = out,
            movingMs = movingMs,
            untrackedMs = untracked
        )
    }

    /** Milliseconds of `[startMs, endMs)` inside `[windowStartMs, windowEndMs)` — never negative (T11). */
    private fun overlapMs(startMs: Long, endMs: Long, windowStartMs: Long, windowEndMs: Long): Long =
        (minOf(endMs, windowEndMs) - maxOf(startMs, windowStartMs)).coerceAtLeast(0L)
}
