package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.pipeline.stage.IndoorOutdoorClassifier
import org.junit.Assert.*
import org.junit.Test

class IndoorOutdoorClassifierTest {

    private val classifier = IndoorOutdoorClassifier()

    private fun sample(accuracyM: Float, speedMps: Float? = 0f): RawSample = RawSample(
        sampleId = 0L,
        capturedAt = 0L,
        lat = 0.0,
        lng = 0.0,
        accuracyM = accuracyM,
        speedMps = speedMps,
        provider = "gps",
        permissionSnapshot = "FINE",
        trackingSessionId = 1L,
        localTimeZone = "UTC",
        geohash = "x"
    )

    @Test
    fun `empty window returns 0_5 uncertain`() {
        val p = classifier.probabilityIndoor(samples = emptyList(), stepRatePerMinute = null)
        assertEquals(0.5f, p, 0.0001f)
    }

    @Test
    fun `good GPS + fast movement = clearly outdoor`() {
        val samples = (1..5).map { sample(accuracyM = 6f, speedMps = 4.0f) }
        val p = classifier.probabilityIndoor(samples, stepRatePerMinute = 120f)
        assertTrue("expected outdoor probability < 0.3, got $p", p < 0.3f)
    }

    @Test
    fun `poor GPS + low speed + steps = strong indoor`() {
        val samples = (1..5).map { sample(accuracyM = 60f, speedMps = 0.1f) }
        val p = classifier.probabilityIndoor(samples, stepRatePerMinute = 90f)
        assertTrue("expected indoor probability > 0.7, got $p", p > 0.7f)
    }

    @Test
    fun `moderate accuracy stationary = mid-range probability`() {
        val samples = (1..5).map { sample(accuracyM = 25f, speedMps = 0.0f) }
        val p = classifier.probabilityIndoor(samples, stepRatePerMinute = null)
        assertTrue(p in 0.3f..0.9f)
    }
}
