package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks [PolylineEncoder] — the route codec used across the map, reconciler, segmenter,
 * workout recorder and GPX export. The canonical-vector test guarantees the output is the
 * standard Google Encoded Polyline format, so external tools decode our routes correctly.
 */
class PolylineEncoderTest {

    @Test
    fun `matches the canonical Google encoded-polyline vector`() {
        // The example from Google's Encoded Polyline Algorithm Format documentation.
        val points = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineEncoder.encode(points))
    }

    @Test
    fun `decodes the canonical vector back to the original points`() {
        val decoded = PolylineEncoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        val expected = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals(expected.size, decoded.size)
        expected.zip(decoded).forEach { (e, d) ->
            assertEquals(e.first, d.first, 1e-5)
            assertEquals(e.second, d.second, 1e-5)
        }
    }

    @Test
    fun `round-trips arbitrary points within 5-decimal precision`() {
        val points = listOf(
            37.77493 to -122.41942,   // San Francisco
            51.50735 to -0.12776,     // London (negative lng)
            -33.86882 to 151.20930,   // Sydney (negative lat)
            -0.00001 to 0.00001       // near the equator/prime meridian, both signs
        )
        val decoded = PolylineEncoder.decode(PolylineEncoder.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (p, d) ->
            assertEquals(p.first, d.first, 1e-5)
            assertEquals(p.second, d.second, 1e-5)
        }
    }

    @Test
    fun `an empty path encodes to the empty string and back`() {
        assertEquals("", PolylineEncoder.encode(emptyList()))
        assertTrue(PolylineEncoder.decode("").isEmpty())
    }

    @Test
    fun `a single point round-trips`() {
        val one = listOf(12.34567 to 76.54321)
        val decoded = PolylineEncoder.decode(PolylineEncoder.encode(one))
        assertEquals(1, decoded.size)
        assertEquals(12.34567, decoded[0].first, 1e-5)
        assertEquals(76.54321, decoded[0].second, 1e-5)
    }

    @Test
    fun `mergePolylines concatenates segments into one decoded path`() {
        val a = PolylineEncoder.encode(listOf(10.0 to 20.0, 11.0 to 21.0))
        val b = PolylineEncoder.encode(listOf(30.0 to 40.0, 31.0 to 41.0))

        val merged = PolylineEncoder.mergePolylines(listOf(a, b))
        val decoded = PolylineEncoder.decode(merged)

        assertEquals(4, decoded.size)
        assertEquals(10.0, decoded[0].first, 1e-5)
        assertEquals(31.0, decoded[3].first, 1e-5)
        assertEquals(41.0, decoded[3].second, 1e-5)
    }

    @Test
    fun `merging an empty list yields the empty string`() {
        assertEquals("", PolylineEncoder.mergePolylines(emptyList()))
        assertEquals("", PolylineEncoder.mergePolylines(listOf("", "")))
    }

    @Test
    fun `an integer series round-trips through the delta codec`() {
        // Time offsets (monotonic) and altitude decimetres (rise + fall, incl. negatives).
        val values = listOf(0, 1000, 2000, 3050, 3050, 2900, 120_000, -450)
        assertEquals(values, PolylineEncoder.decodeInts(PolylineEncoder.encodeInts(values)))
    }

    @Test
    fun `an empty integer series encodes to empty and back`() {
        assertEquals("", PolylineEncoder.encodeInts(emptyList()))
        assertTrue(PolylineEncoder.decodeInts("").isEmpty())
    }
}
