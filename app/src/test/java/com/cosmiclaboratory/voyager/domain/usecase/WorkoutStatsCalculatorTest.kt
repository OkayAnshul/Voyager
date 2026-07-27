package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
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

    @Test
    fun `elevation gain and loss apply a noise threshold`() {
        val points = listOf(
            RoutePoint(0.0, 0.0, 0L, altitudeM = 100.0),
            RoutePoint(0.0, 0.0, 1_000L, altitudeM = 101.5),  // +1.5 from ref -> within noise, ignored
            RoutePoint(0.0, 0.0, 2_000L, altitudeM = 110.0),  // +10 from ref(100) -> gain 10, ref=110
            RoutePoint(0.0, 0.0, 3_000L, altitudeM = 104.0)   // -6 from ref(110) -> loss 6
        )
        val stats = WorkoutStatsCalculator.summarize(points)
        assertEquals(10.0, stats.elevationGainM, 0.01)
        assertEquals(6.0, stats.elevationLossM, 0.01)
    }

    @Test
    fun `elevation is zero when no altitude is present`() {
        val points = listOf(RoutePoint(0.0, 0.0, 0L), RoutePoint(0.001, 0.0, 1_000L))
        val stats = WorkoutStatsCalculator.summarize(points)
        assertEquals(0.0, stats.elevationGainM, 0.0)
        assertEquals(0.0, stats.elevationLossM, 0.0)
    }

    @Test
    fun `splits are whole kilometres with consistent pace at constant speed`() {
        // ~2.34 km straight north at a constant ~3.3 m/s: 70 legs of 0.0003 deg (~33 m), 10 s each.
        val points = (0..70).map { i ->
            RoutePoint(lat = i * 0.0003, lng = 0.0, timeMs = i * 10_000L)
        }
        val splits = WorkoutStatsCalculator.splits(points, DistanceUnit.KM)
        assertEquals("2.34 km yields two full-km splits", 2, splits.size)
        splits.forEach { assertEquals(1000.0, it.distanceMeters, 0.001) }
        // Constant speed -> the two split paces agree closely.
        assertEquals(splits[0].paceSecPerKm, splits[1].paceSecPerKm, 3.0)
        assertTrue("pace should be ~300 s/km", splits[0].paceSecPerKm in 285.0..315.0)
    }

    @Test
    fun `reconstruct round-trips a route with time and altitude`() {
        val started = 1_000_000L
        val points = listOf(
            RoutePoint(37.0, -122.0, started, altitudeM = 10.0),
            RoutePoint(37.001, -122.0, started + 5_000L, altitudeM = 15.4),
            RoutePoint(37.002, -122.0, started + 9_000L, altitudeM = 12.1)
        )
        val poly = PolylineEncoder.encode(points.map { it.lat to it.lng })
        val times = WorkoutStatsCalculator.encodeTimes(points, started)
        val alts = WorkoutStatsCalculator.encodeAltitudes(points)

        val rebuilt = WorkoutStatsCalculator.reconstruct(poly, times, alts, started)
        assertEquals(3, rebuilt.size)
        rebuilt.forEachIndexed { i, p ->
            assertEquals(points[i].lat, p.lat, 1e-5)
            assertEquals(points[i].timeMs, p.timeMs)
            assertEquals(points[i].altitudeM!!, p.altitudeM!!, 0.05) // decimetre rounding
        }
    }

    @Test
    fun `encodeAltitudes is empty when no point has altitude`() {
        val points = listOf(RoutePoint(0.0, 0.0, 0L), RoutePoint(0.001, 0.0, 1_000L))
        assertEquals("", WorkoutStatsCalculator.encodeAltitudes(points))
    }
}
