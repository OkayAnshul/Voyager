package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.LiveWorkoutStats
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the Athlete-facing computed stats on the workout models — pace, distance, and the
 * title fallback the Activities list relies on.
 */
class WorkoutModelTest {

    private fun activity(distanceM: Double, durationMs: Long, title: String? = null) = Activity(
        id = 1, type = WorkoutType.RUN, startedAt = 0L, endedAt = durationMs,
        distanceMeters = distanceM, durationMs = durationMs, avgSpeedMps = 0f, maxSpeedMps = 0f,
        steps = null, encodedPolyline = "", dayKey = "2026-06-14", title = title, notes = null
    )

    @Test
    fun `average pace is seconds per km over the activity`() {
        // 2 km in 600 s → 300 s/km (a 5:00 min/km run).
        assertEquals(300.0, activity(2_000.0, 600_000L).avgPaceSecPerKm!!, 0.001)
    }

    @Test
    fun `pace is null when no distance was covered (avoids divide-by-zero)`() {
        assertNull(activity(0.0, 600_000L).avgPaceSecPerKm)
    }

    @Test
    fun `distance is reported in kilometres`() {
        assertEquals(2.5, activity(2_500.0, 1L).distanceKm, 0.001)
    }

    @Test
    fun `display title uses the user title, falling back to the workout type`() {
        assertEquals("Morning loop", activity(1.0, 1L, title = "Morning loop").displayTitle)
        assertEquals("Run", activity(1.0, 1L, title = null).displayTitle)
        assertEquals("Run", activity(1.0, 1L, title = "   ").displayTitle) // blank falls back
    }

    @Test
    fun `live pace tracks the running average and is null before moving`() {
        val moving = LiveWorkoutStats(WorkoutType.RUN, distanceMeters = 1_000.0, durationMs = 300_000L, currentSpeedMps = 3f, avgSpeedMps = 3.3f)
        assertEquals(300.0, moving.avgPaceSecPerKm!!, 0.001) // 1 km in 300 s
        val idle = LiveWorkoutStats(WorkoutType.RUN, distanceMeters = 0.0, durationMs = 10_000L, currentSpeedMps = 0f, avgSpeedMps = 0f)
        assertNull(idle.avgPaceSecPerKm)
    }
}
