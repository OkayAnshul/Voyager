package com.cosmiclaboratory.voyager.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapWatchdogPolicyTest {

    private val interval = 90_000L // typical STILL cadence
    private val minGap = GapWatchdogPolicy.MIN_GAP_DURATION_MS
    // Comfortably past both 5× interval (450s) and the 10-min floor.
    private val longSilence = minGap + 60_000L

    @Test
    fun `creates a gap once silence passes both the cadence multiple and the floor`() {
        assertTrue(
            GapWatchdogPolicy.shouldCreateGap(
                silenceMs = longSilence, expectedIntervalMs = interval,
                inDormantGrace = false, lastAcceptedAt = 1_000L, lastGapCreatedAt = 0L
            )
        )
    }

    @Test
    fun `no gap below the absolute minimum even past the cadence multiple`() {
        // 6× a tiny interval clears the multiplier but not the 10-min floor.
        val tinyInterval = 10_000L
        val silence = tinyInterval * (GapWatchdogPolicy.SILENCE_MULTIPLIER + 1) // 60s
        assertFalse(
            GapWatchdogPolicy.shouldCreateGap(
                silenceMs = silence, expectedIntervalMs = tinyInterval,
                inDormantGrace = false, lastAcceptedAt = 1_000L, lastGapCreatedAt = 0L
            )
        )
    }

    @Test
    fun `no gap within the cadence multiple`() {
        assertFalse(
            GapWatchdogPolicy.shouldCreateGap(
                silenceMs = interval * 2, expectedIntervalMs = interval,
                inDormantGrace = false, lastAcceptedAt = 1_000L, lastGapCreatedAt = 0L
            )
        )
    }

    @Test
    fun `no gap while GPS is off (interval 0)`() {
        assertFalse(
            GapWatchdogPolicy.shouldCreateGap(
                silenceMs = longSilence, expectedIntervalMs = 0L,
                inDormantGrace = false, lastAcceptedAt = 1_000L, lastGapCreatedAt = 0L
            )
        )
    }

    @Test
    fun `no gap during the post-dormant grace window`() {
        assertFalse(
            GapWatchdogPolicy.shouldCreateGap(
                silenceMs = longSilence, expectedIntervalMs = interval,
                inDormantGrace = true, lastAcceptedAt = 1_000L, lastGapCreatedAt = 0L
            )
        )
    }

    @Test
    fun `no duplicate gap for the same last sample`() {
        assertFalse(
            GapWatchdogPolicy.shouldCreateGap(
                silenceMs = longSilence, expectedIntervalMs = interval,
                inDormantGrace = false, lastAcceptedAt = 1_000L, lastGapCreatedAt = 1_000L
            )
        )
    }

    @Test
    fun `gap reason distinguishes intentional dormancy from GPS loss`() {
        assertEquals("DORMANT", GapWatchdogPolicy.gapReason(isDormant = true))
        assertEquals("GPS_LOSS", GapWatchdogPolicy.gapReason(isDormant = false))
    }
}
