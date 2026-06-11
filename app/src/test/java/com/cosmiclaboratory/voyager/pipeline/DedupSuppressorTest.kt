package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.pipeline.stage.DedupSuppressor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupSuppressorTest {

    private val dedup = DedupSuppressor()

    // At the equator ~1e-5 deg ≈ 1.1 m. JITTER ≈ 2.2 m (< 10 m noise floor); MOVE ≈ 55 m.
    private val jitter = 0.00002
    private val move = 0.0005

    private fun sample(lat: Double, lng: Double, accuracyM: Float, capturedAt: Long) =
        RawSample(
            capturedAt = capturedAt, lat = lat, lng = lng, accuracyM = accuracyM,
            provider = "test", permissionSnapshot = "fine", trackingSessionId = 1, localTimeZone = "UTC", geohash = ""
        )

    @Test
    fun `first sample is accepted`() {
        assertFalse(dedup.shouldSuppress(sample(0.0, 0.0, 10f, 1_000)))
    }

    @Test
    fun `out-of-order sample is suppressed`() {
        dedup.shouldSuppress(sample(0.0, 0.0, 10f, 5_000))
        assertTrue(dedup.shouldSuppress(sample(move, move, 10f, 4_000))) // timeDelta < 0
    }

    @Test
    fun `jitter within the noise floor and recent is suppressed`() {
        dedup.shouldSuppress(sample(0.0, 0.0, 10f, 1_000))
        assertTrue(dedup.shouldSuppress(sample(jitter, 0.0, 10f, 6_000))) // ~2m, +5s
    }

    @Test
    fun `real movement beyond the noise floor is accepted`() {
        dedup.shouldSuppress(sample(0.0, 0.0, 10f, 1_000))
        assertFalse(dedup.shouldSuppress(sample(move, 0.0, 10f, 6_000))) // ~55m, +5s
    }

    @Test
    fun `small move is accepted once enough time has passed`() {
        dedup.shouldSuppress(sample(0.0, 0.0, 10f, 1_000))
        assertFalse(dedup.shouldSuppress(sample(jitter, 0.0, 10f, 32_000))) // ~2m but +31s
    }

    @Test
    fun `reset clears the reference`() {
        dedup.shouldSuppress(sample(0.0, 0.0, 10f, 1_000))
        dedup.reset()
        assertFalse(dedup.shouldSuppress(sample(jitter, 0.0, 10f, 2_000))) // first after reset
    }
}
