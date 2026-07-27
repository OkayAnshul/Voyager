package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.WorkoutSuggestion
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestWorkoutUseCaseTest {

    private val segmentDao = mockk<MovementSegmentDao>()
    private val routeDao = mockk<RouteDao>()
    private val activityDao = mockk<ActivityDao>()
    private val useCase = SuggestWorkoutUseCase(segmentDao, routeDao, activityDao)

    private fun segment(id: Long, type: String, distanceM: Double, durationMin: Long) =
        MovementSegmentEntity(
            segmentId = id, segmentType = type, startAt = 0L, endAt = durationMin * 60_000L,
            distanceM = distanceM, routeId = id, dayKey = "2026-07-23"
        )

    private fun route(segmentId: Long, mode: String) = RouteEntity(
        segmentId = segmentId, encodedPolyline = "abc", totalDistanceM = 2_000.0,
        totalDurationMs = 1_200_000L, avgSpeedMps = 3f, maxSpeedMps = 5f, transportMode = mode, sampleCount = 100
    )

    @Test
    fun `suggests a qualifying run, skipping short, and already-converted segments`() = runTest {
        val segments = listOf(
            segment(10, "RUN", distanceM = 2_000.0, durationMin = 20),   // qualifies
            segment(11, "WALK", distanceM = 300.0, durationMin = 20),    // too short (distance)
            segment(12, "RUN", distanceM = 2_000.0, durationMin = 5),    // too short (duration)
            segment(13, "CYCLE", distanceM = 5_000.0, durationMin = 30), // qualifies but already saved
        )
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns segments
        coEvery { activityDao.getConvertedSegmentIds() } returns listOf(13L)
        coEvery { routeDao.getBySegmentId(10) } returns route(10, "RUN")

        val suggestions = useCase.suggestions(nowMs = 1_753_000_000_000L)

        assertEquals(1, suggestions.size)
        assertEquals(10L, suggestions[0].segmentId)
        assertEquals(WorkoutType.RUN, suggestions[0].type)
        assertEquals(2_000.0, suggestions[0].distanceMeters, 0.001)
    }

    @Test
    fun `materialize inserts an activity tagged with its source segment`() = runTest {
        val suggestion = WorkoutSuggestion(
            segmentId = 10L, type = WorkoutType.RUN, distanceMeters = 2_000.0, durationMs = 1_200_000L,
            startedAt = 1_000L, dayKey = "2026-07-23", encodedPolyline = "abc", avgSpeedMps = 3f, maxSpeedMps = 5f
        )
        val slot = slot<ActivityEntity>()
        coEvery { activityDao.insert(capture(slot)) } returns 99L

        val id = useCase.materialize(suggestion)

        assertEquals(99L, id)
        assertEquals("RUN", slot.captured.activityType)
        assertEquals(10L, slot.captured.sourceSegmentId)
        assertEquals("abc", slot.captured.encodedPolyline)
        assertEquals(1_000L + 1_200_000L, slot.captured.endedAt)
    }
}
