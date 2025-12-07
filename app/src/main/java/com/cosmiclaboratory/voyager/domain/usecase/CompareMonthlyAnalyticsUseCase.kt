package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.PlaceComparison
import com.cosmiclaboratory.voyager.domain.model.Trend
import com.cosmiclaboratory.voyager.domain.model.WeeklyComparison
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for comparing this month's activity to last month's activity
 *
 * Provides insights into behavioral changes:
 * - Total time at places
 * - Number of visits
 * - Number of unique places
 * - Per-place comparisons
 */
@Singleton
class CompareMonthlyAnalyticsUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository
) {
    /**
     * Compare this month to last month
     *
     * Month is defined as 1st to last day of the month
     *
     * @param referenceDate The date to use as "today" (defaults to now, useful for testing)
     * @return WeeklyComparison (reused data class) with all metrics for monthly data
     */
    suspend operator fun invoke(referenceDate: LocalDate = LocalDate.now()): WeeklyComparison {
        // Calculate month boundaries
        val thisMonth = YearMonth.from(referenceDate)
        val thisMonthStart = thisMonth.atDay(1)
        val thisMonthEnd = thisMonth.atEndOfMonth()

        val lastMonth = thisMonth.minusMonths(1)
        val lastMonthStart = lastMonth.atDay(1)
        val lastMonthEnd = lastMonth.atEndOfMonth()

        // Get visits for both months
        val thisMonthVisits = visitRepository.getVisitsBetween(
            startTime = thisMonthStart.atStartOfDay(),
            endTime = thisMonthEnd.atTime(23, 59, 59)
        ).first()

        val lastMonthVisits = visitRepository.getVisitsBetween(
            startTime = lastMonthStart.atStartOfDay(),
            endTime = lastMonthEnd.atTime(23, 59, 59)
        ).first()

        // Calculate overall metrics
        val thisMonthTotalTime = thisMonthVisits.sumOf { it.duration }
        val lastMonthTotalTime = lastMonthVisits.sumOf { it.duration }
        val totalTimeChange = calculatePercentageChange(lastMonthTotalTime, thisMonthTotalTime)

        val thisMonthVisitCount = thisMonthVisits.size
        val lastMonthVisitCount = lastMonthVisits.size
        val visitCountChange = calculatePercentageChange(lastMonthVisitCount.toLong(), thisMonthVisitCount.toLong())

        val thisMonthPlaceCount = thisMonthVisits.map { it.placeId }.distinct().size
        val lastMonthPlaceCount = lastMonthVisits.map { it.placeId }.distinct().size
        val placeCountChange = calculatePercentageChange(lastMonthPlaceCount.toLong(), thisMonthPlaceCount.toLong())

        // Calculate per-place comparisons
        val placeComparisons = calculatePlaceComparisons(thisMonthVisits, lastMonthVisits)

        return WeeklyComparison(
            thisWeekStart = thisMonthStart,
            thisWeekEnd = thisMonthEnd,
            lastWeekStart = lastMonthStart,
            lastWeekEnd = lastMonthEnd,

            thisWeekTotalTime = thisMonthTotalTime,
            lastWeekTotalTime = lastMonthTotalTime,
            totalTimeChange = totalTimeChange,
            totalTimeTrend = Trend.fromPercentage(totalTimeChange),

            thisWeekVisitCount = thisMonthVisitCount,
            lastWeekVisitCount = lastMonthVisitCount,
            visitCountChange = visitCountChange,
            visitCountTrend = Trend.fromPercentage(visitCountChange),

            thisWeekPlaceCount = thisMonthPlaceCount,
            lastWeekPlaceCount = lastMonthPlaceCount,
            placeCountChange = placeCountChange,
            placeCountTrend = Trend.fromPercentage(placeCountChange),

            placeComparisons = placeComparisons
        )
    }

    /**
     * Calculate per-place comparisons
     */
    private suspend fun calculatePlaceComparisons(
        thisMonthVisits: List<com.cosmiclaboratory.voyager.domain.model.Visit>,
        lastMonthVisits: List<com.cosmiclaboratory.voyager.domain.model.Visit>
    ): List<PlaceComparison> {
        // Get all unique place IDs from both months
        val allPlaceIds = (thisMonthVisits.map { it.placeId } + lastMonthVisits.map { it.placeId }).distinct()

        // Get all places
        val places = placeRepository.getAllPlaces().first()
        val placeMap = places.associateBy { it.id }

        // Calculate comparison for each place
        val comparisons = allPlaceIds.mapNotNull { placeId ->
            val place = placeMap[placeId] ?: return@mapNotNull null

            val thisMonthPlaceVisits = thisMonthVisits.filter { it.placeId == placeId }
            val lastMonthPlaceVisits = lastMonthVisits.filter { it.placeId == placeId }

            val thisMonthVisitCount = thisMonthPlaceVisits.size
            val lastMonthVisitCount = lastMonthPlaceVisits.size

            val thisMonthTotalTime = thisMonthPlaceVisits.sumOf { it.duration }
            val lastMonthTotalTime = lastMonthPlaceVisits.sumOf { it.duration }

            val visitChange = calculatePercentageChange(lastMonthVisitCount.toLong(), thisMonthVisitCount.toLong())
            val timeChange = calculatePercentageChange(lastMonthTotalTime, thisMonthTotalTime)

            val thisMonthAvgDuration = if (thisMonthVisitCount > 0) thisMonthTotalTime / thisMonthVisitCount else 0L
            val lastMonthAvgDuration = if (lastMonthVisitCount > 0) lastMonthTotalTime / lastMonthVisitCount else 0L

            PlaceComparison(
                place = place,
                thisWeekVisits = thisMonthVisitCount,
                thisWeekTotalTime = thisMonthTotalTime,
                lastWeekVisits = lastMonthVisitCount,
                lastWeekTotalTime = lastMonthTotalTime,
                visitChange = visitChange,
                timeChange = timeChange,
                timeTrend = Trend.fromPercentage(timeChange),
                thisWeekAvgDuration = thisMonthAvgDuration,
                lastWeekAvgDuration = lastMonthAvgDuration
            )
        }

        // Sort by total time this month (descending)
        return comparisons.sortedByDescending { it.thisWeekTotalTime }
    }

    /**
     * Calculate percentage change from old to new value
     *
     * @param oldValue The previous value
     * @param newValue The current value
     * @return Percentage change (-100 to +infinity)
     *         Returns 0 if both values are 0
     *         Returns 100 if old is 0 but new is non-zero (new activity)
     */
    private fun calculatePercentageChange(oldValue: Long, newValue: Long): Float {
        if (oldValue == 0L && newValue == 0L) return 0f
        if (oldValue == 0L) return 100f  // New activity

        val change = newValue - oldValue
        return (change.toFloat() / oldValue.toFloat()) * 100f
    }
}
