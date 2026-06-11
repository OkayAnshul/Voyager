package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.pipeline.stage.LocationKalmanFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationKalmanFilterTest {

    private val kf = LocationKalmanFilter()

    @Test
    fun `first sample returns the input position unchanged`() {
        val out = kf.filter(37.0, -122.0, 10f, 1_000)
        assertEquals(37.0, out.lat, 1e-6)
        assertEquals(-122.0, out.lng, 1e-6)
    }

    @Test
    fun `re-anchors past 25km without producing garbage (T9)`() {
        kf.filter(37.0, -122.0, 10f, 1_000)
        // Jump ~300 km (different region): the flat-earth reference must reset and
        // re-init at the new point rather than projecting through a stale reference.
        val out = kf.filter(40.0, -120.0, 10f, 2_000)
        assertEquals(40.0, out.lat, 1e-6)
        assertEquals(-120.0, out.lng, 1e-6)
    }

    @Test
    fun `a huge time gap resets the filter`() {
        kf.filter(37.0, -122.0, 10f, 1_000)
        // >300s gap → reset → re-init returns the new input exactly.
        val out = kf.filter(37.0001, -122.0001, 10f, 1_000 + 400_000)
        assertEquals(37.0001, out.lat, 1e-6)
        assertEquals(-122.0001, out.lng, 1e-6)
    }

    @Test
    fun `smooths a stationary jitter cloud toward the centre`() {
        kf.filter(37.0, -122.0, 10f, 1_000)
        kf.filter(37.00002, -122.00002, 10f, 2_000)
        kf.filter(36.99998, -121.99998, 10f, 3_000)
        val out = kf.filter(37.00001, -122.00001, 10f, 4_000)
        // Jitter is ~2 m; the estimate must stay within a very loose ~100 m of centre.
        assertEquals(37.0, out.lat, 1e-3)
        assertEquals(-122.0, out.lng, 1e-3)
    }
}
