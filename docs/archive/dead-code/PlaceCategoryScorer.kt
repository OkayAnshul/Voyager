package com.cosmiclaboratory.voyager.domain.usecase

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.SemanticContext
import com.cosmiclaboratory.voyager.domain.model.UserActivity
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceCategoryScorer @Inject constructor() {

    companion object {
        private const val TAG = "PlaceCategoryScorer"
    }

    /**
     * Phase 2: Infer place category using activity data + time patterns
     * Much more accurate than time patterns alone
     */
    fun categorizePlaceFromActivityAndTime(
        locations: List<Location>,
        dominantActivity: UserActivity?,
        dominantContext: SemanticContext?,
        activityDistribution: Map<UserActivity, Float>,
        preferences: UserPreferences
    ): PlaceCategory {
        if (locations.isEmpty()) return PlaceCategory.UNKNOWN

        // Priority 1: Use semantic context if available (most reliable)
        dominantContext?.let { context ->
            return when (context) {
                SemanticContext.WORKING -> PlaceCategory.WORK
                SemanticContext.WORKING_OUT -> PlaceCategory.GYM
                SemanticContext.OUTDOOR_EXERCISE -> PlaceCategory.OUTDOOR
                SemanticContext.EATING -> PlaceCategory.RESTAURANT
                SemanticContext.SHOPPING,
                SemanticContext.RUNNING_ERRANDS -> PlaceCategory.SHOPPING
                SemanticContext.SOCIALIZING -> PlaceCategory.SOCIAL
                SemanticContext.ENTERTAINMENT -> PlaceCategory.ENTERTAINMENT
                SemanticContext.RELAXING_HOME -> PlaceCategory.HOME
                else -> PlaceCategory.UNKNOWN
            }
        }

        // Priority 2: Use activity patterns
        val stationaryPercent = activityDistribution[UserActivity.STATIONARY] ?: 0f
        val walkingPercent = activityDistribution[UserActivity.WALKING] ?: 0f

        // High stationary + work hours = likely WORK or HOME
        if (stationaryPercent > 0.8f) {
            val nightHours = locations.count { it.timestamp.hour in 22..23 || it.timestamp.hour in 0..6 }
            val nightPercent = nightHours.toFloat() / locations.size

            if (nightPercent > 0.6f) return PlaceCategory.HOME

            val workHours = locations.count {
                it.timestamp.hour in 9..17 &&
                it.timestamp.dayOfWeek.value in 1..5
            }
            val workPercent = workHours.toFloat() / locations.size

            if (workPercent > 0.5f) return PlaceCategory.WORK
        }

        // Moderate walking + stationary = SHOPPING or RESTAURANT
        if (walkingPercent in 0.2f..0.6f && stationaryPercent in 0.3f..0.7f) {
            val avgDurationMinutes = calculateAverageDuration(locations)

            return when {
                avgDurationMinutes in 30..120 -> PlaceCategory.SHOPPING
                avgDurationMinutes in 45..90 -> PlaceCategory.RESTAURANT
                else -> PlaceCategory.UNKNOWN
            }
        }

        // Fallback to time-based inference
        return PlaceCategory.UNKNOWN
    }

    fun calculateAverageDuration(locations: List<Location>): Int {
        if (locations.size < 2) return 0

        val sorted = locations.sortedBy { it.timestamp }
        val duration = java.time.Duration.between(
            sorted.first().timestamp,
            sorted.last().timestamp
        )

        return duration.toMinutes().toInt()
    }

    suspend fun categorizePlace(locations: List<Location>, preferences: UserPreferences): PlaceCategory {
        if (locations.isEmpty()) return PlaceCategory.UNKNOWN

        // FIX: Score-based categorization instead of order-dependent
        val scores = mutableMapOf<PlaceCategory, Float>()

        scores[PlaceCategory.HOME] = calculateHomeScore(locations, preferences)
        scores[PlaceCategory.WORK] = calculateWorkScore(locations, preferences)
        scores[PlaceCategory.EDUCATION] = calculateEducationScore(locations, preferences)
        scores[PlaceCategory.GYM] = calculateGymScore(locations, preferences)
        scores[PlaceCategory.SHOPPING] = calculateShoppingScore(locations, preferences)
        scores[PlaceCategory.RESTAURANT] = calculateRestaurantScore(locations, preferences)

        // Find highest scoring category
        val bestMatch = scores.maxByOrNull { it.value }

        // Only accept if score > threshold, otherwise UNKNOWN
        return if (bestMatch != null && bestMatch.value >= 0.5f) {
            Log.d(TAG, "Category: ${bestMatch.key} (score: ${String.format("%.2f", bestMatch.value)})")
            bestMatch.key
        } else {
            Log.d(TAG, "Category: UNKNOWN (best score: ${String.format("%.2f", bestMatch?.value ?: 0f)})")
            PlaceCategory.UNKNOWN
        }
    }

    fun calculateHomeScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val nightHours = hourCounts.filterKeys { it >= 22 || it <= 6 }.values.sum()
        val eveningHours = hourCounts.filterKeys { it in 18..21 }.values.sum()
        val totalCount = locations.size

        val nightEveningRatio = (nightHours + eveningHours).toFloat() / totalCount

        // Home gets high score for night/evening activity
        return when {
            nightEveningRatio > preferences.homeNightActivityThreshold -> nightEveningRatio
            nightEveningRatio > 0.4f -> nightEveningRatio * 0.7f  // Possible home
            else -> nightEveningRatio * 0.3f  // Unlikely
        }
    }

    fun calculateWorkScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val dayOfWeekCounts = locations.groupBy { it.timestamp.dayOfWeek }.mapValues { it.value.size }

        val workHours = hourCounts.filterKeys { it in 9..17 }.values.sum()
        val weekdayCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        }.values.sum()
        val weekendCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }.values.sum()

        val totalCount = locations.size
        val workHoursRatio = workHours.toFloat() / totalCount
        val weekdayRatio = weekdayCount.toFloat() / maxOf(1, weekdayCount + weekendCount)

        // Work requires both work hours AND weekday predominance
        return if (workHoursRatio > preferences.workHoursActivityThreshold && weekdayRatio > 0.7f) {
            (workHoursRatio + weekdayRatio) / 2f
        } else {
            (workHoursRatio * weekdayRatio) * 0.5f
        }
    }

    fun calculateEducationScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val dayOfWeekCounts = locations.groupBy { it.timestamp.dayOfWeek }.mapValues { it.value.size }

        // Education hours: Morning classes (8-12) and afternoon (13-17)
        val morningClassHours = hourCounts.filterKeys { it in 8..12 }.values.sum()
        val afternoonClassHours = hourCounts.filterKeys { it in 13..17 }.values.sum()
        val classHoursTotal = morningClassHours + afternoonClassHours

        // Strong weekday pattern but can have some weekend activity (study, labs)
        val weekdayCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        }.values.sum()
        val weekendCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }.values.sum()

        val totalCount = locations.size
        val classHoursRatio = classHoursTotal.toFloat() / totalCount
        val weekdayRatio = weekdayCount.toFloat() / maxOf(1, weekdayCount + weekendCount)

        // Education differs from work by:
        // 1. Less strict on continuous presence (classes have gaps)
        // 2. Strong morning concentration (8-12)
        // 3. Moderate afternoon presence (not as late as work)
        // 4. Weekday dominant but more flexible (60%+ instead of 70%+)

        val morningRatio = morningClassHours.toFloat() / totalCount
        val afternoonRatio = afternoonClassHours.toFloat() / totalCount

        // Bonus for balanced morning/afternoon pattern (indicates multiple classes)
        val balanceBonus = if (morningRatio > 0.2f && afternoonRatio > 0.2f) 0.1f else 0f

        // Education requires class hours AND weekday pattern (more lenient than work)
        return if (classHoursRatio > 0.4f && weekdayRatio > 0.6f) {
            val baseScore = (classHoursRatio + weekdayRatio) / 2f
            (baseScore + balanceBonus).coerceAtMost(1.0f)
        } else {
            (classHoursRatio * weekdayRatio * 0.6f)  // Lower than work multiplier
        }
    }

    suspend fun calculateGymScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.size < 5) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val morningWorkout = hourCounts.filterKeys { it in 6..9 }.values.sum()
        val eveningWorkout = hourCounts.filterKeys { it in 17..20 }.values.sum()
        val totalCount = locations.size

        // Requirement 1: Workout time pattern
        val workoutTimeFactor = (morningWorkout + eveningWorkout).toFloat() / totalCount

        // Requirement 2: 2-3x per week frequency (not daily like work)
        val distinctDays = locations.map { it.timestamp.toLocalDate() }.distinct().size
        val daysSpan = if (locations.size > 1) {
            java.time.Duration.between(
                locations.minOf { it.timestamp },
                locations.maxOf { it.timestamp }
            ).toDays()
        } else 1L

        val weeksSpan = maxOf(1, daysSpan / 7)
        val visitsPerWeek = distinctDays.toFloat() / weeksSpan
        val frequencyFactor = when {
            visitsPerWeek in 2f..4f -> 1.0f  // Perfect gym frequency
            visitsPerWeek in 1f..5f -> 0.5f  // Possible
            else -> 0.1f  // Too frequent (work) or too rare
        }

        // Requirement 3: Short-medium duration (30min-2hr typical)
        val avgDuration = calculateAverageStayDuration(locations, preferences)
        val durationFactor = when {
            avgDuration in 30L..120L -> 1.0f  // Perfect gym duration
            avgDuration in 20L..180L -> 0.5f  // Possible
            else -> 0.1f  // Too short or too long
        }

        // Combined score (all factors must be reasonable)
        return workoutTimeFactor * frequencyFactor * durationFactor
    }

    suspend fun calculateShoppingScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val avgDuration = calculateAverageStayDuration(locations, preferences)

        // Shopping has medium duration
        val durationScore = when {
            avgDuration in preferences.shoppingMinDurationMinutes..preferences.shoppingMaxDurationMinutes -> 1.0f
            avgDuration in (preferences.shoppingMinDurationMinutes - 10)..(preferences.shoppingMaxDurationMinutes + 30) -> 0.5f
            else -> 0.1f
        }

        return durationScore * 0.7f  // Lower baseline confidence for shopping
    }

    suspend fun calculateRestaurantScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val mealTimes = hourCounts.filterKeys { it in 11..14 || it in 18..21 }.values.sum()
        val totalCount = locations.size

        // Requirement 1: Meal time pattern
        val mealTimeFactor = mealTimes.toFloat() / totalCount

        // Requirement 2: Medium duration (30min-2hr)
        val avgDuration = calculateAverageStayDuration(locations, preferences)
        val durationFactor = when {
            avgDuration in 30L..120L -> 1.0f
            avgDuration in 20L..180L -> 0.5f
            else -> 0.1f
        }

        // Requirement 3: Irregular visits (not daily routine)
        val distinctDays = locations.map { it.timestamp.toLocalDate() }.distinct().size
        val daysSpan = if (locations.size > 1) {
            java.time.Duration.between(
                locations.minOf { it.timestamp },
                locations.maxOf { it.timestamp }
            ).toDays()
        } else 1L

        val weeksSpan = maxOf(1, daysSpan / 7)
        val visitsPerWeek = distinctDays.toFloat() / weeksSpan
        val irregularityFactor = when {
            visitsPerWeek < 2f -> 1.0f  // Irregular visits - good for restaurant
            visitsPerWeek < 4f -> 0.5f  // Semi-regular
            else -> 0.2f  // Too regular (likely work/home during meal times)
        }

        // Combined score
        return mealTimeFactor * durationFactor * irregularityFactor
    }

    suspend fun calculateAverageStayDuration(locations: List<Location>, preferences: UserPreferences): Long {
        if (locations.size < 2) return 0

        val sortedLocations = locations.sortedBy { it.timestamp }
        val durations = mutableListOf<Long>()

        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()

        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()

            if (timeDiff > preferences.sessionBreakTimeMinutes) { // Session break based on user preference
                val sessionDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
                if (sessionDuration > preferences.minVisitDurationMinutes) { // Only count sessions longer than minimum duration
                    durations.add(sessionDuration)
                }
                sessionStart = current.timestamp
            }
            lastLocation = current
        }

        // Add final session
        val finalDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
        if (finalDuration > preferences.minVisitDurationMinutes) {
            durations.add(finalDuration)
        }

        return if (durations.isNotEmpty()) durations.average().toLong() else 0
    }
}
