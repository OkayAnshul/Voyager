package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Represents a statistical insight derived from user's location data
 * Provides quantitative analysis of behavior patterns
 */
sealed class StatisticalInsight {
    abstract val timestamp: LocalDateTime
    abstract val category: InsightCategory
    abstract val title: String
    abstract val summary: String

    /**
     * Descriptive statistics for a place
     */
    data class PlaceStatistics(
        override val timestamp: LocalDateTime,
        override val category: InsightCategory,
        override val title: String,
        override val summary: String,
        val place: Place,
        val visitCount: Int,
        val meanDuration: Long,
        val medianDuration: Long,
        val stdDeviation: Double,
        val percentile90Duration: Long,
        val weekdayVisits: Int,
        val weekendVisits: Int,
        val mostCommonHour: Int,
        val leastCommonHour: Int
    ) : StatisticalInsight()

    /**
     * Time-based behavioral trends
     */
    data class TemporalTrend(
        override val timestamp: LocalDateTime,
        override val category: InsightCategory,
        override val title: String,
        override val summary: String,
        val metric: String, // e.g., "Daily Distance", "Visit Count"
        val currentValue: Double,
        val previousValue: Double,
        val percentChange: Double,
        val trend: TrendDirection,
        val periodDays: Int
    ) : StatisticalInsight()

    /**
     * Correlation between different variables
     */
    data class Correlation(
        override val timestamp: LocalDateTime,
        override val category: InsightCategory,
        override val title: String,
        override val summary: String,
        val variable1: String,
        val variable2: String,
        val correlationCoefficient: Double,
        val strength: CorrelationStrength,
        val sampleSize: Int
    ) : StatisticalInsight()

    /**
     * Distribution analysis
     */
    data class Distribution(
        override val timestamp: LocalDateTime,
        override val category: InsightCategory,
        override val title: String,
        override val summary: String,
        val metric: String,
        val mean: Double,
        val median: Double,
        val mode: Double?,
        val range: Pair<Double, Double>,
        val quartiles: List<Double> // Q1, Q2 (median), Q3
    ) : StatisticalInsight()

    /**
     * Frequency analysis
     */
    data class FrequencyAnalysis(
        override val timestamp: LocalDateTime,
        override val category: InsightCategory,
        override val title: String,
        override val summary: String,
        val item: String,
        val frequency: Int,
        val relativeFrequency: Double,
        val rank: Int,
        val totalItems: Int
    ) : StatisticalInsight()

    /**
     * Predictive insight based on historical patterns
     */
    data class Prediction(
        override val timestamp: LocalDateTime,
        override val category: InsightCategory,
        override val title: String,
        override val summary: String,
        val prediction: String,
        val confidence: Double,
        val basedOnSamples: Int,
        val timeframe: String // e.g., "next 2 hours", "tomorrow"
    ) : StatisticalInsight()
}

/**
 * Categories for organizing insights
 */
enum class InsightCategory {
    HOME,           // Home-related insights
    WORK,           // Work-related insights
    MOVEMENT,       // Distance, speed, transportation
    TIME_PATTERNS,  // Temporal behavior patterns
    SOCIAL,         // Social places, co-location
    HEALTH,         // Active time, sedentary behavior
    ROUTINE,        // Daily routines, consistency
    EXPLORATION,    // New places, diversity
    ANOMALY         // Unusual behavior
}

/**
 * Trend direction
 */
enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE
}

/**
 * Correlation strength classification
 */
enum class CorrelationStrength {
    VERY_WEAK,      // |r| < 0.2
    WEAK,           // 0.2 <= |r| < 0.4
    MODERATE,       // 0.4 <= |r| < 0.6
    STRONG,         // 0.6 <= |r| < 0.8
    VERY_STRONG     // |r| >= 0.8
}

/**
 * Personalized message derived from statistical insights
 * User-friendly interpretation of data
 */
data class PersonalizedMessage(
    val id: String,
    val category: InsightCategory,
    val message: String,
    val details: List<String>,
    val relatedInsight: StatisticalInsight?,
    val priority: MessagePriority,
    val timestamp: LocalDateTime,
    val actionable: Boolean = false,
    val actionText: String? = null
)

/**
 * Message priority for sorting
 */
enum class MessagePriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Weekly summary statistics
 */
data class WeeklySummary(
    val weekStart: LocalDateTime,
    val weekEnd: LocalDateTime,
    val totalDistance: Double,          // km
    val totalPlacesVisited: Int,
    val totalVisits: Int,
    val averageDailyDistance: Double,   // km
    val mostVisitedPlace: Place?,
    val mostVisitedPlaceCount: Int,
    val newPlacesDiscovered: Int,
    val timeByCategory: Map<PlaceCategory, Long>, // minutes
    val mostActiveDay: String,
    val leastActiveDay: String,
    val routineScore: Double,           // 0-100, consistency measure
    val comparedToLastWeek: WeekComparison
)

/**
 * Comparison with previous week
 */
data class WeekComparison(
    val distanceChange: Double,         // % change
    val placesChange: Int,              // absolute change
    val visitsChange: Int,              // absolute change
    val routineScoreChange: Double      // change in score
)

/**
 * Monthly summary statistics
 */
data class MonthlySummary(
    val monthStart: LocalDateTime,
    val monthEnd: LocalDateTime,
    val totalDistance: Double,
    val totalPlacesVisited: Int,
    val totalVisits: Int,
    val averageDailyDistance: Double,
    val topPlaces: List<Pair<Place, Int>>, // Place and visit count
    val newPlacesDiscovered: Int,
    val timeByCategory: Map<PlaceCategory, Long>,
    val mostActiveWeek: Int,
    val explorationScore: Double,        // 0-100, new places vs routine
    val comparedToLastMonth: MonthComparison
)

/**
 * Comparison with previous month
 */
data class MonthComparison(
    val distanceChange: Double,
    val placesChange: Int,
    val visitsChange: Int,
    val explorationScoreChange: Double
)
