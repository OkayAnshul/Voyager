package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.pipeline.stage.SampleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SampleNormalizerTest {

    private val normalizer = SampleNormalizer()

    private fun sample(lat: Double = 1.0, lng: Double = 2.0, speed: Float? = null, bearing: Float? = null) =
        RawSample(
            capturedAt = 0L, lat = lat, lng = lng, accuracyM = 10f, speedMps = speed, bearingDeg = bearing,
            provider = "test", permissionSnapshot = "fine", trackingSessionId = 1, localTimeZone = "UTC", geohash = ""
        )

    @Test
    fun `rounds coordinates to 7 decimals`() {
        val out = normalizer.normalize(sample(lat = 12.123456789, lng = -98.987654321))
        assertEquals(12.1234568, out.lat, 1e-9)
        assertEquals(-98.9876543, out.lng, 1e-9)
    }

    @Test
    fun `clamps negative speed to zero`() {
        assertEquals(0f, normalizer.normalize(sample(speed = -3f)).speedMps!!, 1e-6f)
    }

    @Test
    fun `preserves null speed and bearing`() {
        val out = normalizer.normalize(sample(speed = null, bearing = null))
        assertNull(out.speedMps)
        assertNull(out.bearingDeg)
    }

    @Test
    fun `normalizes bearing into 0 until 360 including negatives`() {
        assertEquals(10f, normalizer.normalize(sample(bearing = 370f)).bearingDeg!!, 1e-4f)
        assertEquals(350f, normalizer.normalize(sample(bearing = -10f)).bearingDeg!!, 1e-4f)
    }
}
