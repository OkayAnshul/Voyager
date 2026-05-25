package com.cosmiclaboratory.voyager.platform.export

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import org.junit.Assert.assertEquals
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
}
