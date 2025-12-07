package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.PlaceComparison
import com.cosmiclaboratory.voyager.domain.model.Trend
import com.cosmiclaboratory.voyager.domain.model.WeeklyComparison
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for comparing this week's activity to last week's activity
 *
 * Provides insights into behavioral changes:
 * - Total time at places
 * - Number of visits
 * - Number of unique places
 * - Per-place comparisons
 */
@Singleton
class CompareWeeklyAnalyticsUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository
) {
    /**
     * Compare this week to last week
     *
     * Week is defined as Monday-Sunday
     *
     * @param referenceDate The date to use as "today" (defaults to now, useful for testing)
     * @return WeeklyComparison with all metrics
     */
    suspend operator fun invoke(referenceDate: LocalDate = LocalDate.now()): WeeklyComparison {
        // Calculate week boundaries (Monday to Sunday)
        val thisWeekStart = referenceDate.with(DayOfWeek.MONDAY)
        val thisWeekEnd = thisWeekStart.plusDays(6)  // Sunday

        val lastWeekStart = thisWeekStart.minusWeeks(1)
        val lastWeekEnd = lastWeekStart.plusDays(6)

        // Get visits for both weeks
        val thisWeekVisits = visitRepository.getVisitsBetween(
            startTime = thisWeekStart.atStartOfDay(),
            endTime = thisWeekEnd.atTime(23, 59, 59)
        ).first()

        val lastWeekVisits = visitRepository.getVisitsBetween(
            startTime = lastWeekStart.atStartOfDay(),
            endTime = lastWeekEnd.atTime(23, 59, 59)
        ).first()

        // Calculate overall metrics
        val thisWeekTotalTime = thisWeekVisits.sumOf { it.duration }
        val lastWeekTotalTime = lastWeekVisits.sumOf { it.duration }
        val totalTimeChange = calculatePercentageChange(lastWeekTotalTime, thisWeekTotalTime)

        val thisWeekVisitCount = thisWeekVisits.size
        val lastWeekVisitCount = lastWeekVisits.size
        val visitCountChange = calculatePercentageChange(lastWeekVisitCount.toLong(), thisWeekVisitCount.toLong())

        val thisWeekPlaceCount = thisWeekVisits.map { it.placeId }.distinct().size
        val lastWeekPlaceCount = lastWeekVisits.map { it.placeId }.distinct().size
        val placeCountChange = calculatePercentageChange(lastWeekPlaceCount.toLong(), thisWeekPlaceCount.toLong())

        // Calculate per-place comparisons
        val placeComparisons = calculatePlaceComparisons(thisWeekVisits, lastWeekVisits)

        return WeeklyComparison(
            thisWeekStart = thisWeekStart,
            thisWeekEnd = thisWeekEnd,
            lastWeekStart = lastWeekStart,
            lastWeekEnd = lastWeekEnd,

            thisWeekTotalTime = thisWeekTotalTime,
            lastWeekTotalTime = lastWeekTotalTime,
            totalTimeChange = totalTimeChange,
            totalTimeTrend = Trend.fromPercentage(totalTimeChange),

            thisWeekVisitCount = thisWeekVisitCount,
            lastWeekVisitCount = lastWeekVisitCount,
            visitCountChange = visitCountChange,
            visitCountTrend = Trend.fromPercentage(visitCountChange),

            thisWeekPlaceCount = thisWeekPlaceCount,
            lastWeekPlaceCount = lastWeekPlaceCount,
            placeCountChange = placeCountChange,
            placeCountTrend = Trend.fromPercentage(placeCountChange),

            placeComparisons = placeComparisons
        )
    }

    /**
     * Calculate per-place comparisons
     */
    private suspend fun calculatePlaceComparisons(
        thisWeekVisits: List<com.cosmiclaboratory.voyager.domain.model.Visit>,
        lastWeekVisits: List<com.cosmiclaboratory.voyager.domain.model.Visit>
    ): List<PlaceComparison> {
        // Get all unique place IDs from both weeks
        val allPlaceIds = (thisWeekVisits.map { it.placeId } + lastWeekVisits.map { it.placeId }).distinct()

        // Get all places
        val places = placeRepository.getAllPlaces().first()
        val placeMap = places.associateBy { it.id }

        // Calculate comparison for each place
        val comparisons = allPlaceIds.mapNotNull { placeId ->
            val place = placeMap[placeId] ?: return@mapNotNull null

            val thisWeekPlaceVisits = thisWeekVisits.filter { it.placeId == placeId }
            val lastWeekPlaceVisits = lastWeekVisits.filter { it.placeId == placeId }

            val thisWeekVisitCount = thisWeekPlaceVisits.size
            val lastWeekVisitCount = lastWeekPlaceVisits.size

            val thisWeekTotalTime = thisWeekPlaceVisits.sumOf { it.duration }
            val lastWeekTotalTime = lastWeekPlaceVisits.sumOf { it.duration }

            val visitChange = calculatePercentageChange(lastWeekVisitCount.toLong(), thisWeekVisitCount.toLong())
            val timeChange = calculatePercentageChange(lastWeekTotalTime, thisWeekTotalTime)

            val thisWeekAvgDuration = if (thisWeekVisitCount > 0) thisWeekTotalTime / thisWeekVisitCount else 0L
            val lastWeekAvgDuration = if (lastWeekVisitCount > 0) lastWeekTotalTime / lastWeekVisitCount else 0L

            PlaceComparison(
                place = place,
                thisWeekVisits = thisWeekVisitCount,
                thisWeekTotalTime = thisWeekTotalTime,
                lastWeekVisits = lastWeekVisitCount,
                lastWeekTotalTime = lastWeekTotalTime,
                visitChange = visitChange,
                timeChange = timeChange,
                timeTrend = Trend.fromPercentage(timeChange),
                thisWeekAvgDuration = thisWeekAvgDuration,
                lastWeekAvgDuration = lastWeekAvgDuration
            )
        }

        // Sort by total time this week (descending)
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
