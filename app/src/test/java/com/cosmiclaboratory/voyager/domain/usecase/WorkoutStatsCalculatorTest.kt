package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the workout summary maths: distance, duration, average + peak speed. */
class WorkoutStatsCalculatorTest {

    @Test
    fun `fewer than two points yields zero`() {
        assertEquals(0.0, WorkoutStatsCalculator.summarize(emptyList()).distanceMeters, 0.0)
        val one = listOf(RoutePoint(0.0, 0.0, 0L))
        assertEquals(0L, WorkoutStatsCalculator.summarize(one).durationMs)
    }

    @Test
    fun `computes distance duration and average speed`() {
        // 0.001 deg latitude ~= 111 m. Over 100 s -> ~1.11 m/s.
        val points = listOf(
            RoutePoint(0.0, 0.0, 0L),
            RoutePoint(0.001, 0.0, 100_000L)
        )
        val stats = WorkoutStatsCalculator.summarize(points)
        assertEquals(111.0, stats.distanceMeters, 3.0)
        assertEquals(100_000L, stats.durationMs)
        assertEquals(1.11f, stats.avgSpeedMps, 0.05f)
    }

    @Test
    fun `peak speed ignores an implausible GPS jump`() {
        val points = listOf(
            RoutePoint(0.0, 0.0, 0L),
            RoutePoint(0.001, 0.0, 10_000L),     // ~11 m/s over 10 s — plausible
            RoutePoint(0.01, 0.0, 10_100L)        // ~1 km in 100 ms — a glitch
        )
        val stats = WorkoutStatsCalculator.summarize(points)
        // Max speed is taken from the plausible leg, not the teleport.
        assertTrue("maxSpeed should be the plausible ~11 m/s, was ${stats.maxSpeedMps}",
            stats.maxSpeedMps in 9f..13f)
    }

    @Test
    fun `an implausible GPS jump is excluded from total distance, not just peak speed`() {
        val withGlitch = listOf(
            RoutePoint(0.0, 0.0, 0L),
            RoutePoint(0.001, 0.0, 10_000L),     // ~111 m, plausible
            RoutePoint(0.01, 0.0, 10_100L)        // ~1 km in 100 ms — glitch leg
        )
        val cleanOnly = listOf(
            RoutePoint(0.0, 0.0, 0L),
            RoutePoint(0.001, 0.0, 10_000L)
        )
        // The glitch ~1 km leg must not inflate the total — distance matches the clean route.
        assertEquals(
            WorkoutStatsCalculator.summarize(cleanOnly).distanceMeters,
            WorkoutStatsCalculator.summarize(withGlitch).distanceMeters,
            0.001
        )
    }

    @Test
    fun `an out-of-order leg contributes no distance`() {
        val points = listOf(
            RoutePoint(0.0, 0.0, 0L),
            RoutePoint(0.001, 0.0, 10_000L),     // ~111 m forward in time
            RoutePoint(0.05, 0.0, 5_000L)         // timestamp goes backwards — can't validate
        )
        // Only the first valid leg counts; the backwards leg is skipped.
        assertEquals(111.0, WorkoutStatsCalculator.summarize(points).distanceMeters, 3.0)
    }
}
