package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import java.time.LocalDate
import javax.inject.Inject

/** Current and longest run of consecutive days with tracked activity. */
data class TrackingStreak(
    val currentDays: Int,
    val longestDays: Int
)

/**
 * Consecutive tracking-day streak, derived from the active-day keys in
 * `daily_rollups` (days with at least one place visit). The current streak counts
 * back from today — or yesterday, if today hasn't been active yet — so an unfinished
 * "today" never breaks the streak. Bounded by [DailyRollupDao.getActiveDayKeys]'s
 * 60-day window, which is plenty for a "current streak" surface.
 */
class ComputeTrackingStreakUseCase @Inject constructor(
    private val dailyRollupDao: DailyRollupDao
) {
    suspend fun compute(today: LocalDate = LocalDate.now()): TrackingStreak {
        val days = dailyRollupDao.getActiveDayKeys()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        return TrackingStreak(
            currentDays = currentStreak(days, today),
            longestDays = longestStreak(days)
        )
    }

    /** Pure: length of the streak ending at [today] (or yesterday). Testable. */
    fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        var anchor = when {
            days.contains(today) -> today
            days.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (days.contains(anchor)) {
            count++
            anchor = anchor.minusDays(1)
        }
        return count
    }

    /** Pure: the longest run of consecutive days anywhere in [days]. Testable. */
    fun longestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.toList().sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }
        return longest
    }
}
