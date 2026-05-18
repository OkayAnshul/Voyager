package com.cosmiclaboratory.voyager.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [PolylineCodec] — the encode/decode round trip used by GPX/GeoJSON
 * geometry and by privacy-stripped exports.
 */
class PolylineCodecTest {

    /** The canonical example from Google's polyline-algorithm documentation. */
    private val googleExample = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private val googlePoints = listOf(
        38.5 to -120.2,
        40.7 to -120.95,
        43.252 to -126.453
    )

    @Test
    fun `decode matches the documented Google example`() {
        val decoded = PolylineCodec.decode(googleExample)

        assertThat(decoded).hasSize(3)
        decoded.forEachIndexed { i, (lat, lng) ->
            assertThat(lat).isWithin(1e-5).of(googlePoints[i].first)
            assertThat(lng).isWithin(1e-5).of(googlePoints[i].second)
        }
    }

    @Test
    fun `encode reproduces the documented Google example`() {
        assertThat(PolylineCodec.encode(googlePoints)).isEqualTo(googleExample)
    }

    @Test
    fun `encode then decode round-trips arbitrary points`() {
        val points = listOf(
            12.97160 to 77.59456,
            12.97200 to 77.59500,
            12.96000 to 77.60000,
            -33.86880 to 151.20930
        )

        val roundTripped = PolylineCodec.decode(PolylineCodec.encode(points))

        assertThat(roundTripped).hasSize(points.size)
        roundTripped.forEachIndexed { i, (lat, lng) ->
            assertThat(lat).isWithin(1e-5).of(points[i].first)
            assertThat(lng).isWithin(1e-5).of(points[i].second)
        }
    }

    @Test
    fun `empty input yields empty output both ways`() {
        assertThat(PolylineCodec.decode("")).isEmpty()
        assertThat(PolylineCodec.encode(emptyList())).isEmpty()
    }

    @Test
    fun `rounding coordinates before encode collapses precision`() {
        // ~2 dp rounding is what privacy-stripped exports apply.
        val precise = listOf(12.971604 to 77.594561)
        val stripped = precise.map { (lat, lng) ->
            Math.round(lat * 100.0) / 100.0 to Math.round(lng * 100.0) / 100.0
        }

        val decoded = PolylineCodec.decode(PolylineCodec.encode(stripped)).single()

        assertThat(decoded.first).isWithin(1e-6).of(12.97)
        assertThat(decoded.second).isWithin(1e-6).of(77.59)
    }
}
