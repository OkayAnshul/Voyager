package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Use case for detecting behavioral anomalies
 *
 * Compares recent behavior to established patterns to identify:
 * - Missed visits to regular places
 * - Unusual visit durations
 * - Visits at unusual times
 * - Visits on unusual days
 */
@Singleton
class DetectAnomaliesUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository,
    private val analyzePlacePatternsUseCase: AnalyzePlacePatternsUseCase,
    private val preferencesRepository: com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
) {
    // Anomaly detection parameters are now user-configurable via preferences
    // Defaults: recentDays=14, lookbackDays=90, durationThreshold=0.5, timeThreshold=3h
    // User can adjust these in Enhanced Settings > Advanced Settings > Anomaly Detection

    /**
     * Detect all anomalies in recent behavior
     *
     * @return List of detected anomalies sorted by severity
     */
    suspend operator fun invoke(): List<Anomaly> {
        val preferences = preferencesRepository.getCurrentPreferences()
        val patterns = analyzePlacePatternsUseCase()
        val anomalies = mutableListOf<Anomaly>()

        // Detect missed places
        anomalies.addAll(detectMissedPlaces(patterns, preferences))

        // Detect unusual durations in recent visits
        anomalies.addAll(detectUnusualDurations(preferences))

        // Detect unusual times
        anomalies.addAll(detectUnusualTimes(patterns, preferences))

        // Detect unusual days
        anomalies.addAll(detectUnusualDays(patterns, preferences))

        // Sort by severity (HIGH first, then by most recent)
        return anomalies.sortedWith(
            compareByDescending<Anomaly> { it.severity }
                .thenByDescending { it.detectedAt }
        )
    }

    /**
     * Detect places the user normally visits but hasn't recently
     */
    private suspend fun detectMissedPlaces(patterns: List<PlacePattern>, preferences: UserPreferences): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val now = LocalDateTime.now()

        // Look at frequency patterns
        val frequencyPatterns = patterns.filter { it.patternType == PlacePatternType.FREQUENCY }

        for (pattern in frequencyPatterns) {
            val place = pattern.place
            val details = pattern.details as? PatternDetails.FrequencyPattern ?: continue

            if (details.visitsPerWeek < 1f) continue  // Skip infrequent places

            // Get last visit
            val visits = visitRepository.getVisitsForPlace(place.id).first()
            val lastVisit = visits.maxByOrNull { it.entryTime }

            if (lastVisit == null) continue

            val daysSinceLastVisit = ChronoUnit.DAYS.between(
                lastVisit.entryTime.toLocalDate(),
                LocalDate.now()
            ).toInt()

            // Calculate expected interval
            val expectedDaysBetweenVisits = (7f / details.visitsPerWeek).toInt()
            val threshold = expectedDaysBetweenVisits * 2  // 2x expected is anomalous

            if (daysSinceLastVisit > threshold && daysSinceLastVisit >= 7) {
                val severity = when {
                    daysSinceLastVisit >= 21 -> AnomalySeverity.HIGH
                    daysSinceLastVisit >= 14 -> AnomalySeverity.MEDIUM
                    else -> AnomalySeverity.LOW
                }

                anomalies.add(
                    Anomaly.MissedPlace(
                        place = place,
                        daysSinceLastVisit = daysSinceLastVisit,
                        expectedFrequency = details.visitsPerWeek,
                        message = buildString {
                            append("You haven't been to ${place.name} in $daysSinceLastVisit days")
                            when {
                                details.visitsPerWeek >= 3 -> append(" (normally 3x/week)")
                                details.visitsPerWeek >= 1 -> append(" (normally weekly)")
                            }
                        },
                        severity = severity,
                        detectedAt = now
                    )
                )
            }
        }

        return anomalies
    }

    /**
     * Detect visits with unusual durations
     */
    private suspend fun detectUnusualDurations(preferences: UserPreferences): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val recentCutoff = LocalDate.now().minusDays(preferences.anomalyRecentDays.toLong())
        val patternCutoff = LocalDate.now().minusDays(preferences.anomalyLookbackDays.toLong())

        val places = placeRepository.getAllPlaces().first()

        for (place in places) {
            val allVisits = visitRepository.getVisitsForPlace(place.id).first()

            // Get historical visits to establish baseline
            val historicalVisits = allVisits.filter {
                it.entryTime.toLocalDate().isAfter(patternCutoff) &&
                it.entryTime.toLocalDate().isBefore(recentCutoff)
            }

            if (historicalVisits.size < 3) continue  // Need baseline data

            val avgDuration = historicalVisits.map { it.duration }.average().toLong()
            if (avgDuration == 0L) continue

            // Check recent visits
            val recentVisits = allVisits.filter {
                it.entryTime.toLocalDate().isAfter(recentCutoff)
            }

            for (visit in recentVisits) {
                if (visit.duration == 0L) continue

                val percentDiff = abs(visit.duration - avgDuration).toFloat() / avgDuration

                // Use configurable threshold (user can set 0.3-1.0, default 0.5 = 50% deviation)
                if (percentDiff > preferences.anomalyDurationThreshold) {
                    val isLonger = visit.duration > avgDuration

                    anomalies.add(
                        Anomaly.UnusualDuration(
                            place = place,
                            actualDuration = visit.duration,
                            expectedDuration = avgDuration,
                            percentageDifference = percentDiff * 100,
                            message = buildString {
                                append("You spent ${visit.duration.formatDuration()} at ${place.name}")
                                append(" (usually ${avgDuration.formatDuration()})")
                            },
                            severity = if (percentDiff > 2.0f) AnomalySeverity.MEDIUM else AnomalySeverity.LOW,
                            detectedAt = visit.entryTime
                        )
                    )
                }
            }
        }

        return anomalies
    }

    /**
     * Detect visits at unusual times
     */
    private suspend fun detectUnusualTimes(patterns: List<PlacePattern>, preferences: UserPreferences): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val recentCutoff = LocalDate.now().minusDays(preferences.anomalyRecentDays.toLong())

        // Look at time-of-day patterns
        val timePatterns = patterns.filter { it.patternType == PlacePatternType.TIME_OF_DAY }

        for (pattern in timePatterns) {
            val place = pattern.place
            val details = pattern.details as? PatternDetails.TimeOfDayPattern ?: continue

            // Get recent visits
            val recentVisits = visitRepository.getVisitsForPlace(place.id).first()
                .filter { it.entryTime.toLocalDate().isAfter(recentCutoff) }

            for (visit in recentVisits) {
                val visitTime = visit.entryTime.toLocalTime()
                val expectedTime = details.time

                val hoursDiff = abs(
                    (visitTime.hour * 60 + visitTime.minute) -
                    (expectedTime.hour * 60 + expectedTime.minute)
                ) / 60

                // Use configurable threshold (user can set 1-6 hours, default 3)
                if (hoursDiff > preferences.anomalyTimeThresholdHours) {
                    anomalies.add(
                        Anomaly.UnusualTime(
                            place = place,
                            visitTime = visitTime,
                            expectedTime = expectedTime,
                            hoursDifference = hoursDiff,
                            message = "You visited ${place.name} at ${visitTime.formatTime()} (usually ${expectedTime.formatTime()})",
                            severity = AnomalySeverity.LOW,
                            detectedAt = visit.entryTime
                        )
                    )
                }
            }
        }

        return anomalies
    }

    /**
     * Detect visits on unusual days
     */
    private suspend fun detectUnusualDays(patterns: List<PlacePattern>, preferences: UserPreferences): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val recentCutoff = LocalDate.now().minusDays(preferences.anomalyRecentDays.toLong())

        // Look at day-of-week patterns
        val dayPatterns = patterns.filter { it.patternType == PlacePatternType.DAY_OF_WEEK }

        for (pattern in dayPatterns) {
            val place = pattern.place
            val details = pattern.details as? PatternDetails.DayOfWeekPattern ?: continue

            if (details.days.size >= 5) continue  // If visits most days, any day is normal

            // Get recent visits
            val recentVisits = visitRepository.getVisitsForPlace(place.id).first()
                .filter { it.entryTime.toLocalDate().isAfter(recentCutoff) }

            for (visit in recentVisits) {
                val visitDay = visit.entryTime.dayOfWeek

                if (visitDay !in details.days) {
                    anomalies.add(
                        Anomaly.UnusualDay(
                            place = place,
                            visitDay = visitDay,
                            expectedDays = details.days,
                            message = "You visited ${place.name} on ${visitDay.name.lowercase().replaceFirstChar { it.uppercase() }} (usually ${details.days.formatDays()})",
                            severity = AnomalySeverity.LOW,
                            detectedAt = visit.entryTime
                        )
                    )
                }
            }
        }

        return anomalies
    }
}
