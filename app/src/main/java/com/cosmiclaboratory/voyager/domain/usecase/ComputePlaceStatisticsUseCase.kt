package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

/**
 * Aggregated statistics for a single place, derived from its visit history.
 *
 * Surfaced on the place detail screen so the user can see the *distribution*
 * of their visits ("median dwell 11h, range 4–15h, 87 visits") instead of a
 * single hand-wavy summary. Strong trust signal — the data is in the table,
 * the math is in the use case, the UI just renders it.
 *
 * Histograms are 0-indexed:
 * - hourHistogram[0..23] = arrivals per hour-of-day bucket
 * - weekdayHistogram[1..7] = arrivals per Calendar day-of-week bucket
 *   (SUNDAY=1 … SATURDAY=7 to match `Calendar.DAY_OF_WEEK`)
 */
data class PlaceStatistics(
    val visitCount: Int,
    val closedVisitCount: Int,
    val medianDwellMs: Long,
    val p25DwellMs: Long,
    val p75DwellMs: Long,
    val totalDwellMs: Long,
    val firstVisitAt: Long?,
    val lastVisitAt: Long?,
    val hourHistogram: IntArray,    // size 24
    val weekdayHistogram: IntArray  // size 8; index 0 unused
) {
    companion object {
        val EMPTY = PlaceStatistics(
            visitCount = 0,
            closedVisitCount = 0,
            medianDwellMs = 0L,
            p25DwellMs = 0L,
            p75DwellMs = 0L,
            totalDwellMs = 0L,
            firstVisitAt = null,
            lastVisitAt = null,
            hourHistogram = IntArray(24),
            weekdayHistogram = IntArray(8)
        )
    }
}

class ComputePlaceStatisticsUseCase @Inject constructor(
    private val visitDao: VisitDao
) {

    suspend fun forPlace(
        placeId: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PlaceStatistics {
        val visits = visitDao.getByPlaceId(placeId)
        return compute(visits, timeZone)
    }

    /** Pure variant — exposed for use cases that already hold the visit list. */
    fun compute(
        visits: List<VisitEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PlaceStatistics {
        if (visits.isEmpty()) return PlaceStatistics.EMPTY

        val closed = visits.filter { it.departureAt != null && (it.dwellMs ?: 0) > 0 }
        val dwells = closed.mapNotNull { it.dwellMs }
        val hourHist = IntArray(24)
        val weekdayHist = IntArray(8)
        val cal = Calendar.getInstance(timeZone)
        for (visit in visits) {
            cal.timeInMillis = visit.arrivalAt
            hourHist[cal.get(Calendar.HOUR_OF_DAY)]++
            weekdayHist[cal.get(Calendar.DAY_OF_WEEK)]++
        }

        return PlaceStatistics(
            visitCount = visits.size,
            closedVisitCount = closed.size,
            medianDwellMs = percentile(dwells, 0.5),
            p25DwellMs = percentile(dwells, 0.25),
            p75DwellMs = percentile(dwells, 0.75),
            totalDwellMs = dwells.sum(),
            firstVisitAt = visits.minOf { it.arrivalAt },
            lastVisitAt = visits.maxOf { it.arrivalAt },
            hourHistogram = hourHist,
            weekdayHistogram = weekdayHist
        )
    }

    /**
     * Linear-interpolated percentile. Returns 0 for an empty list so callers
     * don't have to null-check; check [PlaceStatistics.closedVisitCount] when
     * the difference matters.
     */
    private fun percentile(values: List<Long>, p: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        if (sorted.size == 1) return sorted[0]
        val rank = p * (sorted.size - 1)
        val lo = rank.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.size - 1)
        val frac = rank - lo
        return (sorted[lo] + (sorted[hi] - sorted[lo]) * frac).toLong()
    }
}
