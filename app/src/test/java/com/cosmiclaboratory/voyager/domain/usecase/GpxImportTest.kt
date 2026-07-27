package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.platform.export.ActivityGpxExporter
import com.cosmiclaboratory.voyager.platform.export.GpxImporter
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxImportTest {

    @Test
    fun `parses trackpoints with elevation and time`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Morning run</name><trkseg>
                <trkpt lat="37.0" lon="-122.0"><ele>10.0</ele><time>2020-09-13T12:26:40Z</time></trkpt>
                <trkpt lat="37.001" lon="-122.0"><ele>15.5</ele><time>2020-09-13T12:26:45Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val parsed = GpxImporter.parse(gpx)
        assertEquals("Morning run", parsed.name)
        assertEquals(2, parsed.points.size)
        assertTrue(parsed.hasTimes)
        assertEquals(37.001, parsed.points[1].lat, 1e-6)
        assertEquals(15.5, parsed.points[1].altitudeM!!, 1e-6)
        assertEquals(5_000L, parsed.points[1].timeMs - parsed.points[0].timeMs)
    }

    @Test
    fun `round-trips a Voyager-exported activity`() {
        val started = 1_600_000_000_000L
        val points = listOf(
            RoutePoint(37.0, -122.0, started, altitudeM = 10.0),
            RoutePoint(37.001, -122.0, started + 5_000L, altitudeM = 15.4),
            RoutePoint(37.002, -122.0, started + 9_000L, altitudeM = 12.1),
        )
        val activity = Activity(
            id = 1, type = WorkoutType.RUN, startedAt = started, endedAt = started + 9_000L,
            distanceMeters = 222.0, durationMs = 9_000L, avgSpeedMps = 1f, maxSpeedMps = 2f,
            steps = null, encodedPolyline = PolylineEncoder.encode(points.map { it.lat to it.lng }),
            dayKey = "2020-09-13", title = "Round trip", notes = null,
            encodedTimes = WorkoutStatsCalculator.encodeTimes(points, started),
            encodedAltitudes = WorkoutStatsCalculator.encodeAltitudes(points),
        )

        val parsed = GpxImporter.parse(ActivityGpxExporter.toGpx(activity))
        assertEquals(3, parsed.points.size)
        parsed.points.forEachIndexed { i, p ->
            assertEquals(points[i].lat, p.lat, 1e-5)
            assertEquals(points[i].timeMs, p.timeMs)
            assertEquals(points[i].altitudeM!!, p.altitudeM!!, 0.05)
        }
    }

    @Test
    fun `import persists an activity with the parsed route and title`() = runTest {
        val activityDao = mockk<ActivityDao>()
        val slot = slot<ActivityEntity>()
        coEvery { activityDao.insert(capture(slot)) } returns 55L
        val useCase = ImportGpxUseCase(activityDao)

        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Imported ride</name><trkseg>
                <trkpt lat="0.0" lon="0.0"><time>2020-09-13T12:00:00Z</time></trkpt>
                <trkpt lat="0.001" lon="0.0"><time>2020-09-13T12:00:30Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val id = useCase.import(gpx, WorkoutType.CYCLE)
        assertEquals(55L, id)
        assertEquals("CYCLE", slot.captured.activityType)
        assertEquals("Imported ride", slot.captured.title)
        assertTrue("timed GPX gets a distance", slot.captured.distanceMeters > 100.0)
        assertTrue("timed GPX gets a time stream", slot.captured.encodedTimes.isNotEmpty())
    }

    @Test
    fun `import returns null for a track with too few points`() = runTest {
        val useCase = ImportGpxUseCase(mockk())
        val gpx = """<gpx><trk><trkseg><trkpt lat="0" lon="0"/></trkseg></trk></gpx>"""
        assertNull(useCase.import(gpx, WorkoutType.RUN))
    }
}
