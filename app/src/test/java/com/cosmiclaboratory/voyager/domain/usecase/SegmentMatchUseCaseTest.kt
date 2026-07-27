package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutSegment
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentMatchUseCaseTest {

    private val useCase = SegmentMatchUseCase()

    /** Dense northbound route (0.0002°≈22 m legs) from 0 to [toLat], [secPerLeg] apart. */
    private fun route(toLat: Double, secPerLeg: Long): List<RoutePoint> {
        val legs = Math.round(toLat / 0.0002).toInt()
        return (0..legs).map { i -> RoutePoint(lat = i * 0.0002, lng = 0.0, timeMs = i * secPerLeg * 1000) }
    }

    private fun activityOf(id: Long, points: List<RoutePoint>): Activity = Activity(
        id = id, type = WorkoutType.RUN, startedAt = points.first().timeMs, endedAt = points.last().timeMs,
        distanceMeters = 0.0, durationMs = 0L, avgSpeedMps = 0f, maxSpeedMps = 0f, steps = null,
        encodedPolyline = PolylineEncoder.encode(points.map { it.lat to it.lng }),
        dayKey = "2026-07-23", title = null, notes = null,
        encodedTimes = WorkoutStatsCalculator.encodeTimes(points, points.first().timeMs),
        encodedAltitudes = "",
    )

    @Test
    fun `match times the segment portion within a longer route`() {
        // Segment: the first ~0.005° of latitude. Route runs 0 → 0.01° at 4 s per 22 m leg.
        val segmentPath = listOf(0.0 to 0.0, 0.005 to 0.0)
        val fullRoute = route(toLat = 0.01, secPerLeg = 4)  // 25 legs cover the segment → 100 s
        val effort = useCase.match(segmentPath, fullRoute)!!
        assertTrue("segment traversal should be ~100 s, was $effort ms", effort in 96_000..104_000)
    }

    @Test
    fun `a route that doesn't cover the segment does not match`() {
        val segmentPath = listOf(0.0 to 0.0, 0.005 to 0.0)
        val elsewhere = (0..20).map { RoutePoint(lat = 1.0 + it * 0.0002, lng = 5.0, timeMs = it * 4_000L) }
        assertNull(useCase.match(segmentPath, elsewhere))
    }

    @Test
    fun `effortsFor keeps only covering activities, fastest first`() {
        val segment = WorkoutSegment(
            id = 1, name = "Hill", createdAt = 0L, distanceMeters = 556.0,
            encodedPolyline = PolylineEncoder.encode(listOf(0.0 to 0.0, 0.005 to 0.0)),
        )
        val fast = activityOf(10, route(toLat = 0.01, secPerLeg = 3))     // segment in ~75 s
        val slow = activityOf(11, route(toLat = 0.01, secPerLeg = 6))     // segment in ~150 s
        val elsewhere = activityOf(12, (0..20).map { RoutePoint(1.0 + it * 0.0002, 5.0, it * 4_000L) })

        val efforts = useCase.effortsFor(segment, listOf(slow, fast, elsewhere))
        assertEquals(2, efforts.size)
        assertEquals("fastest first", 10L, efforts.first().activityId)
        assertTrue(efforts.first().timeMs < efforts[1].timeMs)
    }
}
