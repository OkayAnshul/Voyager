package com.cosmiclaboratory.voyager.domain.usecase

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generate timeline segments for a specific date
 *
 * Groups consecutive visits by:
 * 1. Same place
 * 2. Time gap < threshold (15/30/60 min configurable)
 *
 * Calculates distance and travel time between segments
 */
@Singleton
class GenerateTimelineSegmentsUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: PreferencesRepository
) {
    companion object {
        private const val TAG = "GenerateTimelineSegments"
    }

    /**
     * Generate timeline segments for a specific date
     *
     * @param date The date to generate timeline for
     * @return List of timeline segments, sorted by start time
     */
    suspend operator fun invoke(date: LocalDate): List<TimelineSegment> {
        Log.d(TAG, "Generating timeline segments for date: $date")

        val preferences = preferencesRepository.getCurrentPreferences()

        // Get visits for the entire day (00:00 to 23:59:59)
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.atTime(23, 59, 59)
        val visits = visitRepository.getVisitsBetween(startOfDay, endOfDay).first()

        if (visits.isEmpty()) {
            Log.d(TAG, "No visits found for date: $date")
            return emptyList()
        }

        Log.d(TAG, "Found ${visits.size} visits for date: $date")

        // Group visits by proximity (place + time)
        val grouped = groupVisitsByProximity(
            visits = visits,
            timeWindowMinutes = preferences.timelineTimeWindowMinutes
        )

        Log.d(TAG, "Grouped ${visits.size} visits into ${grouped.size} segments")

        // Convert to segments with distance calculation
        val segments = grouped.mapIndexedNotNull { index, group ->
            try {
                val place = placeRepository.getPlaceById(group.first().placeId) ?: run {
                    Log.w(TAG, "Place not found for visit: ${group.first().placeId}")
                    return@mapIndexedNotNull null
                }

                val nextGroup = grouped.getOrNull(index + 1)
                val nextPlace = nextGroup?.let {
                    placeRepository.getPlaceById(it.first().placeId)
                }

                // Calculate distance to next place
                val distanceToNext = if (nextPlace != null) {
                    LocationUtils.calculateDistance(
                        place.latitude, place.longitude,
                        nextPlace.latitude, nextPlace.longitude
                    )
                } else {
                    null
                }

                // Calculate travel time to next place
                val travelTimeToNext = if (nextGroup != null) {
                    val lastExitTime = group.last().exitTime ?: group.last().entryTime
                    val nextEntryTime = nextGroup.first().entryTime
                    Duration.between(lastExitTime, nextEntryTime).toMillis()
                } else {
                    null
                }

                TimelineSegment(
                    place = place,
                    timeRange = TimeRange(
                        start = group.first().entryTime,
                        end = group.last().exitTime ?: LocalDateTime.now()
                    ),
                    visits = group,
                    distanceToNext = distanceToNext,
                    travelTimeToNext = travelTimeToNext
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating timeline segment for group", e)
                null
            }
        }.sortedBy { it.timeRange.start }

        Log.d(TAG, "Generated ${segments.size} timeline segments")
        return segments
    }

    /**
     * ISSUE #1 FIX: Group consecutive visits ONLY by place (no time window)
     *
     * Groups visits if:
     * - Same place AND no different place visited in between
     *
     * @param visits List of visits to group
     * @param timeWindowMinutes Kept for backward compatibility but not used
     * @return List of visit groups
     */
    private fun groupVisitsByProximity(
        visits: List<Visit>,
        timeWindowMinutes: Long
    ): List<List<Visit>> {
        if (visits.isEmpty()) return emptyList()

        val sorted = visits.sortedBy { it.entryTime }
        val groups = mutableListOf<MutableList<Visit>>()
        var currentGroup = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val previous = sorted[i - 1]

            val samePlace = current.placeId == previous.placeId

            // ISSUE #1: Group ONLY if same place (removed time window condition)
            // If user leaves and comes back, it creates a separate segment
            if (samePlace) {
                currentGroup.add(current)
                Log.v(TAG, "Grouped visit ${current.id} with previous group (same place)")
            } else {
                // Different place - start new group
                groups.add(currentGroup)
                currentGroup = mutableListOf(current)
                Log.v(TAG, "Started new group for visit ${current.id} (different place)")
            }
        }

        // Add the last group
        groups.add(currentGroup)

        return groups
    }
}
