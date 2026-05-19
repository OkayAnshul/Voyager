package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.OnThisDayMemory
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Resurfaces past-year days as "on this day" memories — the same calendar day
 * one to [MAX_YEARS_BACK] years ago, for each year that has a non-empty daily
 * rollup. Purely derived (read-only over `daily_rollups`); no new storage.
 *
 * Headless — returns the data; the dashboard card renders it. Newest memory
 * first (one year ago before five years ago).
 */
class BuildOnThisDayUseCase @Inject constructor(
    private val dailyRollupDao: DailyRollupDao
) {
    companion object {
        const val MAX_YEARS_BACK = 5
        private val DAY_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    suspend fun build(today: LocalDate = LocalDate.now()): List<OnThisDayMemory> {
        val memories = mutableListOf<OnThisDayMemory>()
        for (yearsAgo in 1..MAX_YEARS_BACK) {
            // minusYears clamps Feb 29 → Feb 28 in non-leap years — fine for a memory.
            val day = today.minusYears(yearsAgo.toLong())
            val rollup = dailyRollupDao.getByDayKey(day.format(DAY_KEY)) ?: continue
            // An empty day (no places, no movement) is not a memory worth surfacing.
            if (rollup.placeVisitCount == 0 && rollup.totalDistanceM <= 0.0) continue
            memories += OnThisDayMemory(
                dayKey = rollup.dayKey,
                yearsAgo = yearsAgo,
                distanceM = rollup.totalDistanceM,
                steps = rollup.totalSteps,
                placeVisitCount = rollup.placeVisitCount,
                dominantTransportMode = rollup.dominantTransportMode
            )
        }
        return memories
    }
}
