package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.BestEffortDistance
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsUseCaseTest {

    private val useCase = PersonalRecordsUseCase()

    /** A straight northbound route: each leg ≈ 100 m (0.0009° lat), [secPerLeg] apart. */
    private fun route(legs: Int, startMs: Long, secPerLeg: Long, climbPerLeg: Double = 0.0): List<RoutePoint> =
        (0..legs).map { i ->
            RoutePoint(lat = i * 0.0009, lng = 0.0, timeMs = startMs + i * secPerLeg * 1000, altitudeM = 100.0 + i * climbPerLeg)
        }

    private fun activityOf(id: Long, type: WorkoutType, points: List<RoutePoint>, dayKey: String): Activity {
        val stats = WorkoutStatsCalculator.summarize(points)
        return Activity(
            id = id, type = type, startedAt = points.first().timeMs, endedAt = points.last().timeMs,
            distanceMeters = stats.distanceMeters, durationMs = stats.durationMs,
            avgSpeedMps = stats.avgSpeedMps, maxSpeedMps = stats.maxSpeedMps, steps = null,
            encodedPolyline = PolylineEncoder.encode(points.map { it.lat to it.lng }),
            dayKey = dayKey, title = null, notes = null,
            elevationGainM = stats.elevationGainM, elevationLossM = stats.elevationLossM,
            encodedTimes = WorkoutStatsCalculator.encodeTimes(points, points.first().timeMs),
            encodedAltitudes = WorkoutStatsCalculator.encodeAltitudes(points),
        )
    }

    @Test
    fun `fastest effort picks the quickest window covering the distance`() {
        // One continuous northbound route: first km slow (40 s/leg), second km fast (30 s/leg).
        val points = ArrayList<RoutePoint>()
        var t = 0L
        for (i in 0..20) {
            points.add(RoutePoint(lat = i * 0.0009, lng = 0.0, timeMs = t, altitudeM = 100.0))
            t += if (i < 10) 40_000L else 30_000L
        }
        val effort = useCase.fastestEffortMs(points, 1_000.0)!!
        assertTrue("fastest 1 km should be ~300 s, was $effort ms", effort in 295_000..315_000)
    }

    @Test
    fun `fastest effort is null when the route is shorter than the distance`() {
        assertNull(useCase.fastestEffortMs(route(2, 0L, 10), 1_000.0))
    }

    @Test
    fun `compute aggregates longest, climb, streak, per-type and best efforts`() {
        val run = activityOf(1, WorkoutType.RUN, route(20, 0L, secPerLeg = 30, climbPerLeg = 5.0), "2026-07-20")
        val walk = activityOf(2, WorkoutType.WALK, route(50, 1_000_000L, secPerLeg = 60), "2026-07-21")

        val pr = useCase.compute(listOf(run, walk))

        assertTrue("longest is the ~5 km walk", pr.longestDistanceM > 4_900)
        assertEquals("biggest climb is the run's ~100 m", 100.0, pr.biggestClimbM, 2.0)
        assertEquals("consecutive days → streak 2", 2, pr.longestStreakDays)
        assertTrue(pr.perTypeLongestM[WorkoutType.RUN]!! in 1_900.0..2_100.0)
        assertTrue(pr.perTypeLongestM[WorkoutType.WALK]!! > 4_900.0)
        assertTrue("1 km best effort exists", pr.bestEfforts.containsKey(BestEffortDistance.ONE_K))
        assertTrue("5 km best effort exists (the walk)", pr.bestEfforts.containsKey(BestEffortDistance.FIVE_K))
        // The fast run holds the 1 km best effort (~300 s), not the slow walk.
        assertTrue(pr.bestEfforts[BestEffortDistance.ONE_K]!! in 295_000..320_000)
    }

    @Test
    fun `new achievements celebrate a longer and faster activity`() {
        val a1 = activityOf(1, WorkoutType.RUN, route(10, 0L, secPerLeg = 40), "2026-07-20")
        val a2 = activityOf(2, WorkoutType.RUN, route(20, 1_000_000L, secPerLeg = 30), "2026-07-21")

        val labels = useCase.newAchievements(a2, listOf(a1, a2)).map { it.label }
        assertTrue("longer run is a PR", labels.contains("Longest Run"))
        assertTrue("faster 1 km is a PR", labels.contains("Fastest 1 km"))
    }

    @Test
    fun `the very first activity earns no PR chips`() {
        val a1 = activityOf(1, WorkoutType.RUN, route(10, 0L, secPerLeg = 40), "2026-07-20")
        assertTrue(useCase.newAchievements(a1, listOf(a1)).isEmpty())
    }

    @Test
    fun `longest streak counts consecutive calendar days`() {
        val streak = useCase.longestStreak(listOf("2026-07-01", "2026-07-02", "2026-07-02", "2026-07-04"))
        assertEquals(2, streak)
    }
}
