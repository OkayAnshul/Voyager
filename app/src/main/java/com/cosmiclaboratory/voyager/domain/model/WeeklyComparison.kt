package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDate

/**
 * Represents a comparison between this week and last week's activity
 *
 * Used for analytics insights showing how user's behavior has changed
 *
 * Note: Despite the name, this can also represent monthly comparisons.
 * The naming is kept for backward compatibility.
 */
data class WeeklyComparison(
    val thisWeekStart: LocalDate,
    val thisWeekEnd: LocalDate,
    val lastWeekStart: LocalDate,
    val lastWeekEnd: LocalDate,

    // Overall metrics
    val thisWeekTotalTime: Long,  // milliseconds
    val lastWeekTotalTime: Long,  // milliseconds
    val totalTimeChange: Float,  // percentage (-100 to +infinity)
    val totalTimeTrend: Trend,

    val thisWeekVisitCount: Int,
    val lastWeekVisitCount: Int,
    val visitCountChange: Float,  // percentage
    val visitCountTrend: Trend,

    val thisWeekPlaceCount: Int,
    val lastWeekPlaceCount: Int,
    val placeCountChange: Float,  // percentage
    val placeCountTrend: Trend,

    // Per-place comparisons
    val placeComparisons: List<PlaceComparison>
)

/**
 * Comparison for a specific place
 */
data class PlaceComparison(
    val place: Place,

    // This week
    val thisWeekVisits: Int,
    val thisWeekTotalTime: Long,  // milliseconds

    // Last week
    val lastWeekVisits: Int,
    val lastWeekTotalTime: Long,  // milliseconds

    // Changes
    val visitChange: Float,  // percentage
    val timeChange: Float,  // percentage
    val timeTrend: Trend,

    // Averages
    val thisWeekAvgDuration: Long,  // milliseconds per visit
    val lastWeekAvgDuration: Long   // milliseconds per visit
)

/**
 * Trend indicator for comparisons
 */
enum class Trend {
    UP,      // Increase (>5%)
    DOWN,    // Decrease (<-5%)
    STABLE;  // Within ±5%

    companion object {
        /**
         * Determine trend from percentage change
         * @param percentChange The percentage change (-100 to +infinity)
         * @return Trend enum
         */
        fun fromPercentage(percentChange: Float): Trend {
            return when {
                percentChange > 5f -> UP
                percentChange < -5f -> DOWN
                else -> STABLE
            }
        }
    }
}

/**
 * Helper to format time duration for display
 */
fun Long.formatDuration(): String {
    val hours = this / (1000 * 60 * 60)
    val minutes = (this / (1000 * 60)) % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/**
 * Helper to format percentage change for display
 */
fun Float.formatPercentage(): String {
    val sign = if (this > 0) "+" else ""
    return "$sign${String.format("%.1f", this)}%"
}

/**
 * Helper to get emoji for trend
 */
fun Trend.toEmoji(): String {
    return when (this) {
        Trend.UP -> "⬆️"
        Trend.DOWN -> "⬇️"
        Trend.STABLE -> "→"
    }
}
