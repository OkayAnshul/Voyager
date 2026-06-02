package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import javax.inject.Inject

data class PeriodSummary(
    val dayCount: Int,
    val totalDistanceM: Double,
    val totalSteps: Int,
    val totalDwellMs: Long,
    val totalTransitMs: Long,
    val totalWalkMs: Long,
    val totalDriveMs: Long,
    val totalVisitCount: Int,
    /** Distinct days with at least one detected activity (firstActivityAt non-null). */
    val activeDayCount: Int,
    /** Average daily distance across days that had any activity — null when no active days. */
    val avgActiveDailyDistanceM: Double?,
    /** Best single-day distance across the period (and the dayKey it happened on). */
    val bestDayDistanceM: Double,
    val bestDayKey: String?
)

/**
 * Pure aggregator over [DailyRollupEntity] rows. The store of truth is the
 * existing `daily_rollups` table — this use case rolls them up into month-
 * and year-level summaries on demand so the UI can render those views
 * without a new cache layer. If profiling later shows it's worth caching,
 * a `monthly_rollups` table can wrap this without changing the call sites.
 */
class ComputePeriodSummaryUseCase @Inject constructor(
    private val dailyRollupDao: DailyRollupDao
) {

    suspend fun forMonth(year: Int, month1to12: Int): PeriodSummary {
        require(month1to12 in 1..12)
        val start = "%04d-%02d-01".format(year, month1to12)
        val end = "%04d-%02d-31".format(year, month1to12)
        return aggregate(dailyRollupDao.getByRange(start, end))
    }

    suspend fun forYear(year: Int): PeriodSummary {
        val start = "%04d-01-01".format(year)
        val end = "%04d-12-31".format(year)
        return aggregate(dailyRollupDao.getByRange(start, end))
    }

    suspend fun forRange(startDayKey: String, endDayKey: String): PeriodSummary =
        aggregate(dailyRollupDao.getByRange(startDayKey, endDayKey))

    /** Pure variant — exposed for tests and reuse by callers that already hold the rows. */
    fun aggregate(rollups: List<DailyRollupEntity>): PeriodSummary {
        if (rollups.isEmpty()) return EMPTY
        val activeDays = rollups.filter { it.firstActivityAt != null }
        val bestDay = rollups.maxByOrNull { it.totalDistanceM }
        return PeriodSummary(
            dayCount = rollups.size,
            totalDistanceM = rollups.sumOf { it.totalDistanceM },
            totalSteps = rollups.sumOf { it.totalSteps },
            totalDwellMs = rollups.sumOf { it.totalDwellMs },
            totalTransitMs = rollups.sumOf { it.totalTransitMs },
            totalWalkMs = rollups.sumOf { it.totalWalkMs },
            totalDriveMs = rollups.sumOf { it.totalDriveMs },
            totalVisitCount = rollups.sumOf { it.placeVisitCount },
            activeDayCount = activeDays.size,
            avgActiveDailyDistanceM = activeDays.takeIf { it.isNotEmpty() }
                ?.let { it.sumOf { r -> r.totalDistanceM } / it.size },
            bestDayDistanceM = bestDay?.totalDistanceM ?: 0.0,
            bestDayKey = bestDay?.dayKey
        )
    }

    companion object {
        val EMPTY = PeriodSummary(
            dayCount = 0,
            totalDistanceM = 0.0,
            totalSteps = 0,
            totalDwellMs = 0L,
            totalTransitMs = 0L,
            totalWalkMs = 0L,
            totalDriveMs = 0L,
            totalVisitCount = 0,
            activeDayCount = 0,
            avgActiveDailyDistanceM = null,
            bestDayDistanceM = 0.0,
            bestDayKey = null
        )
    }
}
