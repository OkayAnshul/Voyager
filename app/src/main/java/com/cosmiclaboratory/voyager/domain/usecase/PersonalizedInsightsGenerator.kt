package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Generates user-friendly personalized messages from statistical insights
 * Translates data into actionable, conversational insights
 */
@Singleton
class PersonalizedInsightsGenerator @Inject constructor(
    private val statisticalAnalyticsUseCase: StatisticalAnalyticsUseCase,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository
) {

    /**
     * Generate personalized messages from insights
     */
    suspend fun generateMessages(): List<PersonalizedMessage> {
        val insights = statisticalAnalyticsUseCase()
        val messages = mutableListOf<PersonalizedMessage>()

        // Generate messages from statistical insights
        messages.addAll(insights.map { generateMessageFromInsight(it) })

        // Generate contextual messages
        messages.addAll(generateHomeInsights())
        messages.addAll(generateWorkInsights())
        messages.addAll(generateMovementInsights())
        messages.addAll(generateRoutineInsights())
        messages.addAll(generateExplorationInsights())

        return messages.sortedByDescending { it.priority }
    }

    /**
     * Convert statistical insight to personalized message
     */
    private fun generateMessageFromInsight(insight: StatisticalInsight): PersonalizedMessage {
        return when (insight) {
            is StatisticalInsight.PlaceStatistics -> {
                val weekdayPercent = (insight.weekdayVisits.toDouble() / insight.visitCount * 100).toInt()
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = insight.category,
                    message = buildString {
                        append("You've visited ${insight.place.name} ${insight.visitCount} times. ")
                        append("Typical visit: ${formatDuration(insight.medianDuration)}. ")
                        if (weekdayPercent > 70) {
                            append("You visit mostly on weekdays ($weekdayPercent%).")
                        } else if (weekdayPercent < 30) {
                            append("You visit mostly on weekends (${100 - weekdayPercent}%).")
                        }
                    },
                    details = listOf(
                        "Average duration: ${formatDuration(insight.meanDuration)}",
                        "90th percentile: ${formatDuration(insight.percentile90Duration)}",
                        "Most common hour: ${insight.mostCommonHour}:00",
                        "Standard deviation: ${String.format("%.1f", insight.stdDeviation / 60.0)} minutes"
                    ),
                    relatedInsight = insight,
                    priority = MessagePriority.MEDIUM,
                    timestamp = insight.timestamp
                )
            }

            is StatisticalInsight.TemporalTrend -> {
                val emoji = when (insight.trend) {
                    TrendDirection.INCREASING -> "📈"
                    TrendDirection.DECREASING -> "📉"
                    TrendDirection.STABLE -> "➡️"
                }
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = insight.category,
                    message = buildString {
                        append("$emoji ${insight.metric}: ")
                        append(String.format("%.1f", insight.currentValue))
                        when {
                            insight.percentChange > 5 -> append(" (up ${String.format("%.0f", insight.percentChange)}% from last week)")
                            insight.percentChange < -5 -> append(" (down ${String.format("%.0f", abs(insight.percentChange))}% from last week)")
                            else -> append(" (similar to last week)")
                        }
                    },
                    details = listOf(
                        "Previous value: ${String.format("%.1f", insight.previousValue)}",
                        "Change: ${String.format("%.1f", insight.percentChange)}%",
                        "Period: ${insight.periodDays} days"
                    ),
                    relatedInsight = insight,
                    priority = if (abs(insight.percentChange) > 20) MessagePriority.HIGH else MessagePriority.MEDIUM,
                    timestamp = insight.timestamp
                )
            }

            is StatisticalInsight.Correlation -> {
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = insight.category,
                    message = insight.summary,
                    details = listOf(
                        "Variables: ${insight.variable1} vs ${insight.variable2}",
                        "Strength: ${insight.strength.name.lowercase().replace('_', ' ')}",
                        "Sample size: ${insight.sampleSize}"
                    ),
                    relatedInsight = insight,
                    priority = MessagePriority.LOW,
                    timestamp = insight.timestamp
                )
            }

            is StatisticalInsight.Distribution -> {
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = insight.category,
                    message = buildString {
                        append("Your ${insight.metric.lowercase()}: ")
                        append("median ${String.format("%.0f", insight.median)}, ")
                        append("average ${String.format("%.0f", insight.mean)}")
                    },
                    details = listOf(
                        "Range: ${String.format("%.0f", insight.range.first)} - ${String.format("%.0f", insight.range.second)}",
                        "25th percentile: ${String.format("%.0f", insight.quartiles[0])}",
                        "75th percentile: ${String.format("%.0f", insight.quartiles[2])}"
                    ),
                    relatedInsight = insight,
                    priority = MessagePriority.LOW,
                    timestamp = insight.timestamp
                )
            }

            is StatisticalInsight.FrequencyAnalysis -> {
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = insight.category,
                    message = "Your #${insight.rank} most visited place: ${insight.item} (${insight.frequency} visits)",
                    details = listOf(
                        "Represents ${String.format("%.1f", insight.relativeFrequency * 100)}% of all visits",
                        "Rank: ${insight.rank} of ${insight.totalItems} places"
                    ),
                    relatedInsight = insight,
                    priority = if (insight.rank == 1) MessagePriority.MEDIUM else MessagePriority.LOW,
                    timestamp = insight.timestamp
                )
            }

            is StatisticalInsight.Prediction -> {
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = insight.category,
                    message = insight.prediction,
                    details = listOf(
                        "Confidence: ${String.format("%.0f", insight.confidence * 100)}%",
                        "Based on ${insight.basedOnSamples} similar occasions",
                        "Timeframe: ${insight.timeframe}"
                    ),
                    relatedInsight = insight,
                    priority = if (insight.confidence > 0.7) MessagePriority.HIGH else MessagePriority.MEDIUM,
                    timestamp = insight.timestamp,
                    actionable = true,
                    actionText = "View similar visits"
                )
            }
        }
    }

    /**
     * Generate home-specific insights
     */
    private suspend fun generateHomeInsights(): List<PersonalizedMessage> {
        val messages = mutableListOf<PersonalizedMessage>()
        val home = placeRepository.getAllPlaces().first().find { it.category == PlaceCategory.HOME }

        if (home != null) {
            val visits = visitRepository.getVisitsForPlace(home.id).first()
            if (visits.size > 7) {
                val nightVisits = visits.filter { it.entryTime.hour >= 22 || it.entryTime.hour <= 6 }
                val avgHomeHours = visits.map { it.duration / 3600.0 }.average()
                val weekdayAvg = visits.filter { it.entryTime.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
                    .map { it.duration / 3600.0 }.average()
                val weekendAvg = visits.filter { it.entryTime.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
                    .map { it.duration / 3600.0 }.average()

                messages.add(
                    PersonalizedMessage(
                        id = UUID.randomUUID().toString(),
                        category = InsightCategory.HOME,
                        message = buildString {
                            append("You spend an average of ${String.format("%.1f", avgHomeHours)} hours at home. ")
                            if (weekendAvg > weekdayAvg + 2) {
                                append("You're home ${String.format("%.1f", weekendAvg - weekdayAvg)} hours more on weekends.")
                            }
                            val sleepConsistency = (nightVisits.size.toDouble() / visits.size * 100).toInt()
                            if (sleepConsistency > 80) {
                                append(" Sleep pattern is consistent (${sleepConsistency}% of nights).")
                            }
                        },
                        details = listOf(
                            "Weekday average: ${String.format("%.1f", weekdayAvg)} hours",
                            "Weekend average: ${String.format("%.1f", weekendAvg)} hours",
                            "Night visits: ${nightVisits.size} of ${visits.size}"
                        ),
                        relatedInsight = null,
                        priority = MessagePriority.MEDIUM,
                        timestamp = LocalDateTime.now()
                    )
                )
            }
        }

        return messages
    }

    /**
     * Generate work-specific insights
     */
    private suspend fun generateWorkInsights(): List<PersonalizedMessage> {
        val messages = mutableListOf<PersonalizedMessage>()
        val work = placeRepository.getAllPlaces().first().find { it.category == PlaceCategory.WORK }

        if (work != null) {
            val visits = visitRepository.getVisitsForPlace(work.id).first()
            if (visits.size > 5) {
                val avgWorkHours = visits.map { it.duration / 3600.0 }.average()
                val weeklyVisits = visits.filter {
                    ChronoUnit.DAYS.between(it.entryTime.toLocalDate(), LocalDateTime.now().toLocalDate()) <= 7
                }.size

                val arrivalTimes = visits.map { it.entryTime.hour * 60 + it.entryTime.minute }
                val avgArrival = arrivalTimes.average().toInt()
                val avgArrivalHour = avgArrival / 60
                val avgArrivalMinute = avgArrival % 60

                messages.add(
                    PersonalizedMessage(
                        id = UUID.randomUUID().toString(),
                        category = InsightCategory.WORK,
                        message = buildString {
                            append("Your typical work week: ${String.format("%.1f", avgWorkHours * weeklyVisits)} hours across $weeklyVisits days. ")
                            append("You usually arrive at ${String.format("%02d:%02d", avgArrivalHour, avgArrivalMinute)}.")
                        },
                        details = listOf(
                            "Average daily hours: ${String.format("%.1f", avgWorkHours)}",
                            "Days per week: $weeklyVisits",
                            "Total recent visits: ${visits.size}"
                        ),
                        relatedInsight = null,
                        priority = MessagePriority.MEDIUM,
                        timestamp = LocalDateTime.now()
                    )
                )
            }
        }

        return messages
    }

    /**
     * Generate movement insights
     */
    private suspend fun generateMovementInsights(): List<PersonalizedMessage> {
        val messages = mutableListOf<PersonalizedMessage>()
        // This would use location data to compute distance
        // For now, placeholder based on visits
        val visits = visitRepository.getAllVisits().first()
        val recentVisits = visits.filter {
            ChronoUnit.DAYS.between(it.entryTime.toLocalDate(), LocalDateTime.now().toLocalDate()) <= 7
        }

        val uniquePlaces = recentVisits.map { it.placeId }.distinct().size

        if (recentVisits.isNotEmpty()) {
            messages.add(
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = InsightCategory.MOVEMENT,
                    message = "This week: ${recentVisits.size} visits to $uniquePlaces different places",
                    details = listOf(
                        "Daily average: ${String.format("%.1f", recentVisits.size / 7.0)} visits",
                        "Place diversity: $uniquePlaces locations"
                    ),
                    relatedInsight = null,
                    priority = MessagePriority.LOW,
                    timestamp = LocalDateTime.now()
                )
            )
        }

        return messages
    }

    /**
     * Generate routine insights
     */
    private suspend fun generateRoutineInsights(): List<PersonalizedMessage> {
        val messages = mutableListOf<PersonalizedMessage>()
        val visits = visitRepository.getAllVisits().first()

        if (visits.size > 20) {
            // Calculate routine score based on visit regularity
            val dayVisitCounts = visits.groupBy { it.entryTime.dayOfWeek }.mapValues { it.value.size }
            val avgVisitsPerDay = dayVisitCounts.values.average()
            val variance = dayVisitCounts.values.map { (it - avgVisitsPerDay) * (it - avgVisitsPerDay) }.average()
            val stdDev = kotlin.math.sqrt(variance)
            val coefficientOfVariation = stdDev / avgVisitsPerDay

            val routineScore = ((1 - coefficientOfVariation.coerceIn(0.0, 1.0)) * 100).toInt()

            messages.add(
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = InsightCategory.ROUTINE,
                    message = buildString {
                        append("Routine consistency score: $routineScore/100. ")
                        when {
                            routineScore > 70 -> append("You have a very consistent weekly routine!")
                            routineScore > 40 -> append("You have a moderately consistent routine.")
                            else -> append("Your schedule varies significantly week to week.")
                        }
                    },
                    details = listOf(
                        "Based on ${visits.size} visits",
                        "Average visits per day: ${String.format("%.1f", avgVisitsPerDay)}",
                        "Variation: ${String.format("%.1f", coefficientOfVariation * 100)}%"
                    ),
                    relatedInsight = null,
                    priority = MessagePriority.MEDIUM,
                    timestamp = LocalDateTime.now()
                )
            )
        }

        return messages
    }

    /**
     * Generate exploration insights
     */
    private suspend fun generateExplorationInsights(): List<PersonalizedMessage> {
        val messages = mutableListOf<PersonalizedMessage>()
        val places = placeRepository.getAllPlaces().first()

        val recentPlaces = places.filter {
            it.lastVisit != null &&
            ChronoUnit.DAYS.between(it.lastVisit, LocalDateTime.now()) <= 30
        }

        if (recentPlaces.isNotEmpty()) {
            messages.add(
                PersonalizedMessage(
                    id = UUID.randomUUID().toString(),
                    category = InsightCategory.EXPLORATION,
                    message = "You discovered ${recentPlaces.size} new places in the last 30 days",
                    details = recentPlaces.take(5).map { it.name },
                    relatedInsight = null,
                    priority = MessagePriority.LOW,
                    timestamp = LocalDateTime.now(),
                    actionable = true,
                    actionText = "View new places"
                )
            )
        }

        return messages
    }

    // Helper functions

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
