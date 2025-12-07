package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Use case for computing statistical analytics on location data
 * Provides descriptive statistics, trends, correlations, and predictions
 */
@Singleton
class StatisticalAnalyticsUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository
) {

    /**
     * Compute comprehensive statistical insights
     */
    suspend operator fun invoke(): List<StatisticalInsight> {
        val insights = mutableListOf<StatisticalInsight>()

        // Place-based statistics
        insights.addAll(computePlaceStatistics())

        // Temporal trends
        insights.addAll(computeTemporalTrends())

        // Correlations
        insights.addAll(computeCorrelations())

        // Distributions
        insights.addAll(computeDistributions())

        // Frequency analysis
        insights.addAll(computeFrequencyAnalysis())

        // Predictions
        insights.addAll(computePredictions())

        return insights.sortedByDescending { it.timestamp }
    }

    /**
     * Compute descriptive statistics for each place
     */
    private suspend fun computePlaceStatistics(): List<StatisticalInsight.PlaceStatistics> {
        val places = placeRepository.getAllPlaces().first()
        val insights = mutableListOf<StatisticalInsight.PlaceStatistics>()

        for (place in places) {
            val visits = visitRepository.getVisitsForPlace(place.id).first()
            if (visits.size < 3) continue // Need minimum data

            val durations = visits.map { it.duration }
            val mean = durations.average().toLong()
            val median = durations.sorted()[durations.size / 2]
            val variance = durations.map { (it - mean).toDouble().pow(2) }.average()
            val stdDev = sqrt(variance)
            val percentile90 = durations.sorted()[(durations.size * 0.9).toInt()]

            val weekdayVisits = visits.count { it.entryTime.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
            val weekendVisits = visits.size - weekdayVisits

            val hourCounts = visits.groupBy { it.entryTime.hour }.mapValues { it.value.size }
            val mostCommonHour = hourCounts.maxByOrNull { it.value }?.key ?: 12
            val leastCommonHour = hourCounts.minByOrNull { it.value }?.key ?: 0

            insights.add(
                StatisticalInsight.PlaceStatistics(
                    timestamp = LocalDateTime.now(),
                    category = when (place.category) {
                        PlaceCategory.HOME -> InsightCategory.HOME
                        PlaceCategory.WORK -> InsightCategory.WORK
                        else -> InsightCategory.ROUTINE
                    },
                    title = "Statistics for ${place.name}",
                    summary = "Based on ${visits.size} visits",
                    place = place,
                    visitCount = visits.size,
                    meanDuration = mean,
                    medianDuration = median,
                    stdDeviation = stdDev,
                    percentile90Duration = percentile90,
                    weekdayVisits = weekdayVisits,
                    weekendVisits = weekendVisits,
                    mostCommonHour = mostCommonHour,
                    leastCommonHour = leastCommonHour
                )
            )
        }

        return insights
    }

    /**
     * Compute temporal trends (comparing time periods)
     */
    private suspend fun computeTemporalTrends(): List<StatisticalInsight.TemporalTrend> {
        val insights = mutableListOf<StatisticalInsight.TemporalTrend>()

        // Weekly distance trend
        val currentWeekDistance = computeWeeklyDistance(0)
        val lastWeekDistance = computeWeeklyDistance(1)
        if (currentWeekDistance > 0 && lastWeekDistance > 0) {
            val percentChange = ((currentWeekDistance - lastWeekDistance) / lastWeekDistance) * 100
            insights.add(
                StatisticalInsight.TemporalTrend(
                    timestamp = LocalDateTime.now(),
                    category = InsightCategory.MOVEMENT,
                    title = "Weekly Distance Trend",
                    summary = "${String.format("%.1f", currentWeekDistance)} km this week vs ${String.format("%.1f", lastWeekDistance)} km last week",
                    metric = "Weekly Distance",
                    currentValue = currentWeekDistance,
                    previousValue = lastWeekDistance,
                    percentChange = percentChange,
                    trend = when {
                        percentChange > 5 -> TrendDirection.INCREASING
                        percentChange < -5 -> TrendDirection.DECREASING
                        else -> TrendDirection.STABLE
                    },
                    periodDays = 7
                )
            )
        }

        // Visit count trend
        val currentWeekVisits = computeWeeklyVisits(0)
        val lastWeekVisits = computeWeeklyVisits(1)
        if (currentWeekVisits > 0 && lastWeekVisits > 0) {
            val percentChange = ((currentWeekVisits - lastWeekVisits).toDouble() / lastWeekVisits) * 100
            insights.add(
                StatisticalInsight.TemporalTrend(
                    timestamp = LocalDateTime.now(),
                    category = InsightCategory.ROUTINE,
                    title = "Weekly Visit Trend",
                    summary = "$currentWeekVisits visits this week vs $lastWeekVisits last week",
                    metric = "Visit Count",
                    currentValue = currentWeekVisits.toDouble(),
                    previousValue = lastWeekVisits.toDouble(),
                    percentChange = percentChange,
                    trend = when {
                        percentChange > 10 -> TrendDirection.INCREASING
                        percentChange < -10 -> TrendDirection.DECREASING
                        else -> TrendDirection.STABLE
                    },
                    periodDays = 7
                )
            )
        }

        return insights
    }

    /**
     * Compute correlations between variables
     */
    private suspend fun computeCorrelations(): List<StatisticalInsight.Correlation> {
        val insights = mutableListOf<StatisticalInsight.Correlation>()

        // Day of week vs visit count correlation
        val visits = visitRepository.getAllVisits().first()
        if (visits.size > 20) {
            val dayVisitCounts = visits.groupBy { it.entryTime.dayOfWeek }.mapValues { it.value.size }
            val coefficientVariation = calculateCoefficientOfVariation(dayVisitCounts.values.map { it.toDouble() })

            // High CV means visits vary by day
            if (coefficientVariation > 0.3) {
                insights.add(
                    StatisticalInsight.Correlation(
                        timestamp = LocalDateTime.now(),
                        category = InsightCategory.TIME_PATTERNS,
                        title = "Day-of-Week Pattern",
                        summary = "Your activity varies significantly by day of week",
                        variable1 = "Day of Week",
                        variable2 = "Visit Count",
                        correlationCoefficient = coefficientVariation,
                        strength = CorrelationStrength.MODERATE,
                        sampleSize = visits.size
                    )
                )
            }
        }

        return insights
    }

    /**
     * Compute distribution statistics
     */
    private suspend fun computeDistributions(): List<StatisticalInsight.Distribution> {
        val insights = mutableListOf<StatisticalInsight.Distribution>()

        // Visit duration distribution
        val visits = visitRepository.getAllVisits().first()
        if (visits.size > 10) {
            val durations = visits.map { it.duration.toDouble() / 60.0 } // minutes
            val sorted = durations.sorted()
            val mean = durations.average()
            val median = sorted[sorted.size / 2]
            val mode = durations.groupBy { it }.maxByOrNull { it.value.size }?.key

            val q1 = sorted[(sorted.size * 0.25).toInt()]
            val q2 = median
            val q3 = sorted[(sorted.size * 0.75).toInt()]

            insights.add(
                StatisticalInsight.Distribution(
                    timestamp = LocalDateTime.now(),
                    category = InsightCategory.TIME_PATTERNS,
                    title = "Visit Duration Distribution",
                    summary = "Median: ${String.format("%.0f", median)} min, Mean: ${String.format("%.0f", mean)} min",
                    metric = "Visit Duration (minutes)",
                    mean = mean,
                    median = median,
                    mode = mode,
                    range = Pair(sorted.first(), sorted.last()),
                    quartiles = listOf(q1, q2, q3)
                )
            )
        }

        return insights
    }

    /**
     * Compute frequency analysis for places
     */
    private suspend fun computeFrequencyAnalysis(): List<StatisticalInsight.FrequencyAnalysis> {
        val insights = mutableListOf<StatisticalInsight.FrequencyAnalysis>()

        val visits = visitRepository.getAllVisits().first()
        val placeVisitCounts = visits.groupBy { it.placeId }.mapValues { it.value.size }
        val totalVisits = visits.size

        if (totalVisits > 0) {
            val ranked = placeVisitCounts.entries.sortedByDescending { it.value }
            ranked.take(5).forEachIndexed { index, entry ->
                val place = placeRepository.getAllPlaces().first().find { it.id == entry.key }
                if (place != null) {
                    insights.add(
                        StatisticalInsight.FrequencyAnalysis(
                            timestamp = LocalDateTime.now(),
                            category = InsightCategory.ROUTINE,
                            title = "Top ${index + 1} Most Visited",
                            summary = "${place.name}: ${entry.value} visits (${String.format("%.1f", entry.value.toDouble() / totalVisits * 100)}%)",
                            item = place.name,
                            frequency = entry.value,
                            relativeFrequency = entry.value.toDouble() / totalVisits,
                            rank = index + 1,
                            totalItems = placeVisitCounts.size
                        )
                    )
                }
            }
        }

        return insights
    }

    /**
     * Compute predictions based on patterns
     */
    private suspend fun computePredictions(): List<StatisticalInsight.Prediction> {
        val insights = mutableListOf<StatisticalInsight.Prediction>()

        // Predict next likely place based on current time and day
        val currentHour = LocalDateTime.now().hour
        val currentDay = LocalDateTime.now().dayOfWeek

        val recentVisits = visitRepository.getAllVisits().first()
            .filter {
                it.entryTime.dayOfWeek == currentDay &&
                abs(it.entryTime.hour - currentHour) <= 1
            }

        if (recentVisits.size >= 3) {
            val placeFrequency = recentVisits.groupBy { it.placeId }.mapValues { it.value.size }
            val mostLikely = placeFrequency.maxByOrNull { it.value }
            if (mostLikely != null) {
                val place = placeRepository.getAllPlaces().first().find { it.id == mostLikely.key }
                if (place != null) {
                    val confidence = mostLikely.value.toDouble() / recentVisits.size

                    insights.add(
                        StatisticalInsight.Prediction(
                            timestamp = LocalDateTime.now(),
                            category = InsightCategory.ROUTINE,
                            title = "Likely Next Destination",
                            summary = "Based on ${recentVisits.size} similar occasions",
                            prediction = "You might be heading to ${place.name}",
                            confidence = confidence,
                            basedOnSamples = recentVisits.size,
                            timeframe = "in the next 2 hours"
                        )
                    )
                }
            }
        }

        return insights
    }

    // Helper functions

    private suspend fun computeWeeklyDistance(weeksAgo: Int): Double {
        val startDate = LocalDateTime.now().minusWeeks(weeksAgo.toLong()).with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay()
        val endDate = startDate.plusWeeks(1)

        val locations = locationRepository.getRecentLocations(10000).first()
            .filter { it.timestamp >= startDate && it.timestamp < endDate }
            .sortedBy { it.timestamp }

        var totalDistance = 0.0
        for (i in 1 until locations.size) {
            val dist = LocationUtils.calculateDistance(
                locations[i-1].latitude, locations[i-1].longitude,
                locations[i].latitude, locations[i].longitude
            )
            totalDistance += dist
        }

        return totalDistance / 1000.0 // Convert to km
    }

    private suspend fun computeWeeklyVisits(weeksAgo: Int): Int {
        val startDate = LocalDateTime.now().minusWeeks(weeksAgo.toLong()).with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay()
        val endDate = startDate.plusWeeks(1)

        return visitRepository.getAllVisits().first()
            .count { it.entryTime >= startDate && it.entryTime < endDate }
    }

    private fun calculateCoefficientOfVariation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        if (mean == 0.0) return 0.0
        val variance = values.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)
        return stdDev / mean
    }
}
