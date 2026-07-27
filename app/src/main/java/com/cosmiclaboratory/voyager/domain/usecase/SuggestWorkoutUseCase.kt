package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.WorkoutSuggestion
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Finds recent passively-tracked run/ride/walk segments that look like intentional workouts and
 * offers to save them as [com.cosmiclaboratory.voyager.domain.model.Activity]s — bridging the two
 * subsystems using data Voyager already captured (no new tracking). A converted segment is never
 * re-suggested (tracked via [ActivityEntity.sourceSegmentId]).
 */
class SuggestWorkoutUseCase @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao,
    private val routeDao: RouteDao,
    private val activityDao: ActivityDao,
) {
    private companion object {
        val WORKOUT_SEGMENT_TYPES = listOf("WALK", "RUN", "CYCLE")
        const val MIN_DISTANCE_M = 800.0        // shorter than this isn't really "a workout"
        const val MIN_DURATION_MS = 8 * 60_000L // …nor shorter than ~8 minutes
        const val LOOKBACK_DAYS = 3L
        const val MAX_SUGGESTIONS = 5
        val DAY_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    /** Qualifying, not-yet-saved suggestions from the last few days, newest first. */
    suspend fun suggestions(nowMs: Long = System.currentTimeMillis()): List<WorkoutSuggestion> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val startDay = today.minusDays(LOOKBACK_DAYS).format(DAY_KEY)
        val endDay = today.format(DAY_KEY)

        val converted = activityDao.getConvertedSegmentIds().toSet()
        val segments = movementSegmentDao.getByTypesBetween(WORKOUT_SEGMENT_TYPES, startDay, endDay)

        // Filter first (no suspend), then look up routes (suspend) on the trimmed candidate list —
        // Iterable.mapNotNull is inline so the suspend route lookups are allowed inside it.
        val candidates = segments
            .filter { it.segmentId !in converted }
            .filter { it.distanceM >= MIN_DISTANCE_M }
            .filter { (it.endAt - it.startAt) >= MIN_DURATION_MS }
            .sortedByDescending { it.startAt }
            .take(MAX_SUGGESTIONS)

        return candidates.mapNotNull { segment ->
            val route = routeDao.getBySegmentId(segment.segmentId) ?: return@mapNotNull null
            if (route.encodedPolyline.isBlank()) return@mapNotNull null
            // Honour a user reclassification of the segment's mode.
            val effectiveType = segment.userOverrideType ?: segment.segmentType
            WorkoutSuggestion(
                segmentId = segment.segmentId,
                type = toWorkoutType(effectiveType),
                distanceMeters = route.totalDistanceM,
                durationMs = route.totalDurationMs,
                startedAt = segment.startAt,
                dayKey = segment.dayKey,
                encodedPolyline = route.encodedPolyline,
                avgSpeedMps = route.avgSpeedMps,
                maxSpeedMps = route.maxSpeedMps,
            )
        }
    }

    /** Saves a suggestion as a real activity, tagging its source segment so it won't re-suggest. */
    suspend fun materialize(suggestion: WorkoutSuggestion): Long {
        val entity = ActivityEntity(
            activityType = suggestion.type.name,
            startedAt = suggestion.startedAt,
            endedAt = suggestion.startedAt + suggestion.durationMs,
            distanceMeters = suggestion.distanceMeters,
            durationMs = suggestion.durationMs,
            avgSpeedMps = suggestion.avgSpeedMps,
            maxSpeedMps = suggestion.maxSpeedMps,
            encodedPolyline = suggestion.encodedPolyline,
            dayKey = suggestion.dayKey,
            sourceSegmentId = suggestion.segmentId,
        )
        return activityDao.insert(entity)
    }

    private fun toWorkoutType(segmentType: String): WorkoutType = when (segmentType) {
        "RUN" -> WorkoutType.RUN
        "WALK" -> WorkoutType.WALK
        "CYCLE" -> WorkoutType.CYCLE
        else -> WorkoutType.OTHER
    }
}
