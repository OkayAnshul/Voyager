package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.capture.AdaptiveSamplingPolicy
import com.cosmiclaboratory.voyager.capture.SamplingPolicy
import com.cosmiclaboratory.voyager.pipeline.stage.QualityScorer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityScorerTest {

    private val policy = mockk<AdaptiveSamplingPolicy>()
    private val scorer = QualityScorer(policy)

    init {
        every { policy.getCurrentPolicy() } returns SamplingPolicy(10_000L, 10f, 100)
        every { policy.getCurrentMotionState() } returns AdaptiveSamplingPolicy.MotionState.WALKING
    }

    private fun sample(accuracyM: Float, isMock: Boolean = false, ageMs: Long = 0L) =
        RawSample(
            capturedAt = System.currentTimeMillis() - ageMs, lat = 1.0, lng = 2.0, accuracyM = accuracyM,
            isMock = isMock, provider = "test", permissionSnapshot = "fine", trackingSessionId = 1,
            localTimeZone = "UTC", geohash = ""
        )

    @Test
    fun `mock locations are discarded`() {
        val r = scorer.score(sample(accuracyM = 5f, isMock = true))
        assertTrue(r.shouldDiscard)
        assertEquals(0f, r.qualityScore, 1e-6f)
    }

    @Test
    fun `very inaccurate samples are discarded`() {
        assertTrue(scorer.score(sample(accuracyM = 250f)).shouldDiscard)
    }

    @Test
    fun `a fresh, accurate sample scores high and is kept`() {
        val r = scorer.score(sample(accuracyM = 8f))
        assertFalse(r.shouldDiscard)
        assertEquals(1.0f, r.qualityScore, 1e-6f)
    }

    @Test
    fun `stale samples are discarded`() {
        // WALKING threshold = max(interval*3, 10min) = 10min; 11min old → stale.
        assertTrue(scorer.score(sample(accuracyM = 8f, ageMs = 11L * 60 * 1000)).shouldDiscard)
    }
}
