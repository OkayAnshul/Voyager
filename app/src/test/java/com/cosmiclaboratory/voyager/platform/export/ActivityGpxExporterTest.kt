package com.cosmiclaboratory.voyager.platform.export

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.usecase.WorkoutStatsCalculator
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies a recorded activity serialises to valid-shaped GPX with its route + title. */
class ActivityGpxExporterTest {

    @Test
    fun `gpx contains a track point per route point and the title`() {
        val polyline = PolylineEncoder.encode(listOf(0.0 to 0.0, 0.001 to 0.0, 0.002 to 0.0))
        val activity = Activity(
            id = 1, type = WorkoutType.RUN, startedAt = 0L, endedAt = 1000L,
            distanceMeters = 222.0, durationMs = 1000L, avgSpeedMps = 1f, maxSpeedMps = 2f,
            steps = null, encodedPolyline = polyline, dayKey = "2026-05-25",
            title = "Morning run", notes = null
        )

        val gpx = ActivityGpxExporter.toGpx(activity)

        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("Morning run"))
        assertEquals(3, Regex("<trkpt").findAll(gpx).count())
    }

    @Test
    fun `gpx escapes special characters in the title`() {
        val activity = Activity(
            id = 1, type = WorkoutType.OTHER, startedAt = 0L, endedAt = 1L,
            distanceMeters = 0.0, durationMs = 1L, avgSpeedMps = 0f, maxSpeedMps = 0f,
            steps = null, encodedPolyline = PolylineEncoder.encode(listOf(0.0 to 0.0)),
            dayKey = "2026-05-25", title = "Tom & Jerry <run>", notes = null
        )
        val gpx = ActivityGpxExporter.toGpx(activity)
        assertTrue(gpx.contains("Tom &amp; Jerry &lt;run&gt;"))
    }

    @Test
    fun `gpx emits elevation and timestamps when the per-point streams are present`() {
        val started = 1_600_000_000_000L
        val points = listOf(
            RoutePoint(0.0, 0.0, started, altitudeM = 100.0),
            RoutePoint(0.001, 0.0, started + 5_000L, altitudeM = 112.5),
            RoutePoint(0.002, 0.0, started + 9_000L, altitudeM = 108.0)
        )
        val activity = Activity(
            id = 1, type = WorkoutType.HIKE, startedAt = started, endedAt = started + 9_000L,
            distanceMeters = 222.0, durationMs = 9_000L, avgSpeedMps = 1f, maxSpeedMps = 2f,
            steps = null, encodedPolyline = PolylineEncoder.encode(points.map { it.lat to it.lng }),
            dayKey = "2026-07-23", title = "Hill repeats", notes = null,
            elevationGainM = 12.5, elevationLossM = 4.5,
            encodedTimes = WorkoutStatsCalculator.encodeTimes(points, started),
            encodedAltitudes = WorkoutStatsCalculator.encodeAltitudes(points)
        )

        val gpx = ActivityGpxExporter.toGpx(activity)
        assertEquals(3, Regex("<trkpt").findAll(gpx).count())
        assertEquals(3, Regex("<ele>").findAll(gpx).count())
        assertEquals(3, Regex("<time>").findAll(gpx).count())
        assertTrue("elevation is emitted with a dot decimal", gpx.contains("<ele>112.5</ele>"))
        assertTrue("time is ISO-8601 UTC", gpx.contains("<time>2020-09-13T12:26:40Z</time>"))
    }

    @Test
    fun `gpx omits ele and time for a legacy row without streams`() {
        val activity = Activity(
            id = 1, type = WorkoutType.RUN, startedAt = 0L, endedAt = 1000L,
            distanceMeters = 111.0, durationMs = 1000L, avgSpeedMps = 1f, maxSpeedMps = 2f,
            steps = null, encodedPolyline = PolylineEncoder.encode(listOf(0.0 to 0.0, 0.001 to 0.0)),
            dayKey = "2026-05-25", title = "Legacy", notes = null
        )
        val gpx = ActivityGpxExporter.toGpx(activity)
        assertEquals(2, Regex("<trkpt").findAll(gpx).count())
        assertFalse(gpx.contains("<ele>"))
        assertFalse(gpx.contains("<time>"))
    }
}
