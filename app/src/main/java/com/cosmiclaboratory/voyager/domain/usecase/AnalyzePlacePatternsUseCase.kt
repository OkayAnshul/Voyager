package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Use case for analyzing and detecting behavioral patterns for places
 *
 * Analyzes visit history to identify:
 * - Regular days of the week
 * - Typical times of day
 * - Visit frequency patterns
 * - Daily routines
 */
@Singleton
class AnalyzePlacePatternsUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
) {
    // Pattern analysis parameters are now user-configurable via preferences
    // Defaults: minVisits=3, minConfidence=0.3, timeWindow=60min, analysisDays=90
    // User can adjust these in Enhanced Settings > Advanced Settings > Pattern Analysis

    /**
     * Analyze patterns for all places
     *
     * @return List of detected patterns sorted by confidence
     */
    suspend operator fun invoke(): List<PlacePattern> {
        val preferences = preferencesRepository.getCurrentPreferences()
        val places = placeRepository.getAllPlaces().first()
        val cutoffDate = LocalDate.now().minusDays(preferences.patternAnalysisDays.toLong())

        val patterns = places.flatMap { place ->
            analyzePlacePatterns(place, cutoffDate, preferences)
        }

        // Return patterns sorted by confidence (highest first)
        return patterns
            .filter { it.confidence >= preferences.patternMinConfidence }
            .sortedByDescending { it.confidence }
    }

    /**
     * Analyze patterns for a specific place
     */
    private suspend fun analyzePlacePatterns(
        place: Place,
        cutoffDate: LocalDate,
        preferences: UserPreferences
    ): List<PlacePattern> {
        val visits = visitRepository.getVisitsForPlace(place.id).first()
            .filter { it.entryTime.toLocalDate().isAfter(cutoffDate) }

        if (visits.size < preferences.patternMinVisits) {
            return emptyList()
        }

        val patterns = mutableListOf<PlacePattern>()

        // Detect day-of-week pattern
        detectDayOfWeekPattern(place, visits, preferences)?.let { patterns.add(it) }

        // Detect time-of-day pattern
        detectTimeOfDayPattern(place, visits, preferences)?.let { patterns.add(it) }

        // Detect frequency pattern
        detectFrequencyPattern(place, visits, preferences)?.let { patterns.add(it) }

        // Detect daily routine pattern
        detectDailyRoutinePattern(place, visits, preferences)?.let { patterns.add(it) }

        return patterns
    }

    /**
     * Detect if user visits on specific days of the week
     */
    private fun detectDayOfWeekPattern(place: Place, visits: List<Visit>, preferences: UserPreferences): PlacePattern? {
        if (visits.size < preferences.patternMinVisits) return null

        // Count visits per day of week
        val dayVisits = visits.groupBy { it.entryTime.dayOfWeek }
        val totalVisits = visits.size

        // Find days with significant visit frequency
        val significantDays = dayVisits
            .filter { (_, dayVisits) -> dayVisits.size.toFloat() / totalVisits >= 0.15f }
            .keys
            .sortedBy { it.value }

        if (significantDays.isEmpty()) return null

        // Calculate confidence (percentage of visits on these days)
        val visitsOnSignificantDays = significantDays.sumOf { day ->
            dayVisits[day]?.size ?: 0
        }
        val confidence = visitsOnSignificantDays.toFloat() / totalVisits

        // Calculate typical time if there is one
        val typicalTime = visits
            .filter { it.entryTime.dayOfWeek in significantDays }
            .map { it.entryTime.toLocalTime() }
            .takeIf { times -> times.isNotEmpty() }
            ?.let { times ->
                val avgMinutes = times.map { it.hour * 60 + it.minute }.average().toInt()
                LocalTime.of(avgMinutes / 60, avgMinutes % 60)
            }

        val description = buildString {
            append("You usually visit ${place.name} on ${significantDays.formatDays()}")
            typicalTime?.let { append(" at ${it.formatTime()}") }
        }

        return PlacePattern(
            place = place,
            patternType = PlacePatternType.DAY_OF_WEEK,
            description = description,
            confidence = confidence,
            details = PatternDetails.DayOfWeekPattern(
                days = significantDays,
                typicalTime = typicalTime
            )
        )
    }

    /**
     * Detect if user visits at a specific time of day
     */
    private fun detectTimeOfDayPattern(place: Place, visits: List<Visit>, preferences: UserPreferences): PlacePattern? {
        if (visits.size < preferences.patternMinVisits) return null

        // Calculate average visit time
        val avgMinutes = visits
            .map { it.entryTime.hour * 60 + it.entryTime.minute }
            .average()
            .toInt()

        val avgTime = LocalTime.of(avgMinutes / 60, avgMinutes % 60)

        // Count visits within time window (user-configurable: 15-180 minutes)
        val visitsInWindow = visits.count { visit ->
            val visitMinutes = visit.entryTime.hour * 60 + visit.entryTime.minute
            abs(visitMinutes - avgMinutes) <= preferences.patternTimeWindowMinutes
        }

        val confidence = visitsInWindow.toFloat() / visits.size

        if (confidence < preferences.patternMinConfidence) return null

        val description = "You typically visit ${place.name} around ${avgTime.formatTime()}"

        return PlacePattern(
            place = place,
            patternType = PlacePatternType.TIME_OF_DAY,
            description = description,
            confidence = confidence,
            details = PatternDetails.TimeOfDayPattern(
                time = avgTime,
                timeWindow = preferences.patternTimeWindowMinutes
            )
        )
    }

    /**
     * Detect visit frequency pattern
     */
    private fun detectFrequencyPattern(place: Place, visits: List<Visit>, preferences: UserPreferences): PlacePattern? {
        if (visits.size < preferences.patternMinVisits) return null

        // Calculate weeks of data
        val firstVisit = visits.minByOrNull { it.entryTime }?.entryTime ?: return null
        val lastVisit = visits.maxByOrNull { it.entryTime }?.entryTime ?: return null
        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
            firstVisit.toLocalDate(),
            lastVisit.toLocalDate()
        ).toFloat()
        val weeks = daysBetween / 7f

        if (weeks < 1f) return null  // Need at least a week of data

        val visitsPerWeek = visits.size / weeks

        // Check if frequency is regular (visits are evenly distributed)
        val weeksWithVisits = visits
            .groupBy { it.entryTime.toLocalDate().atStartOfDay().toLocalDate() }
            .keys
            .map { it.toEpochDay() / 7 }
            .distinct()
            .size

        val isRegular = weeksWithVisits.toFloat() / weeks >= 0.6f

        val confidence = if (isRegular) 0.7f else 0.5f

        val frequencyText = when {
            visitsPerWeek >= 5 -> "almost daily"
            visitsPerWeek >= 3 -> "${visitsPerWeek.toInt()}x per week"
            visitsPerWeek >= 1 -> "weekly"
            else -> "occasionally"
        }

        val description = "You visit ${place.name} $frequencyText"

        return PlacePattern(
            place = place,
            patternType = PlacePatternType.FREQUENCY,
            description = description,
            confidence = confidence,
            details = PatternDetails.FrequencyPattern(
                visitsPerWeek = visitsPerWeek,
                isRegular = isRegular
            )
        )
    }

    /**
     * Detect daily routine patterns (e.g., at home every night)
     */
    private fun detectDailyRoutinePattern(place: Place, visits: List<Visit>, preferences: UserPreferences): PlacePattern? {
        if (visits.size < preferences.patternMinVisits * 7) return null  // Need more data for daily patterns

        // Check if place is visited every day
        val recentDays = 30
        val daysWithVisits = visits
            .filter {
                it.entryTime.toLocalDate().isAfter(
                    LocalDate.now().minusDays(recentDays.toLong())
                )
            }
            .map { it.entryTime.toLocalDate() }
            .distinct()
            .size

        val dailyConfidence = daysWithVisits.toFloat() / recentDays

        if (dailyConfidence < 0.7f) return null  // Not daily enough

        // Find typical time range
        val times = visits
            .sortedBy { it.entryTime }
            .map { it.entryTime.toLocalTime() }

        if (times.isEmpty()) return null

        val avgMinutes = times.map { it.hour * 60 + it.minute }.average().toInt()
        val avgTime = LocalTime.of(avgMinutes / 60, avgMinutes % 60)

        // Determine time range (e.g., "every night after 10 PM")
        val timeDescription = when {
            avgTime.hour >= 22 || avgTime.hour < 6 -> "every night after ${avgTime.formatTime()}"
            avgTime.hour in 6..9 -> "every morning around ${avgTime.formatTime()}"
            avgTime.hour in 17..21 -> "every evening around ${avgTime.formatTime()}"
            else -> "daily around ${avgTime.formatTime()}"
        }

        val description = "You're at ${place.name} $timeDescription"

        return PlacePattern(
            place = place,
            patternType = PlacePatternType.DAILY_ROUTINE,
            description = description,
            confidence = dailyConfidence,
            details = PatternDetails.DailyRoutinePattern(
                timeRange = avgTime to avgTime.plusHours(1),
                occursDaily = dailyConfidence >= 0.9f
            )
        )
    }
}
