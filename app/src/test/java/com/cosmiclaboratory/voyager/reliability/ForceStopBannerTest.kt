package com.cosmiclaboratory.voyager.reliability

import com.cosmiclaboratory.voyager.presentation.screen.reliability.FORCE_STOP_GAP_THRESHOLD_MS
import com.cosmiclaboratory.voyager.presentation.screen.reliability.shouldShowForceStopBanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForceStopBannerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `no banner when there is no last sample`() {
        assertFalse(shouldShowForceStopBanner(lastSampleAt = null, nowMs = now))
    }

    @Test
    fun `no banner for a zero or negative timestamp`() {
        assertFalse(shouldShowForceStopBanner(lastSampleAt = 0L, nowMs = now))
        assertFalse(shouldShowForceStopBanner(lastSampleAt = -1L, nowMs = now))
    }

    @Test
    fun `no banner when gap is under 24h`() {
        val twentyThreeHoursAgo = now - (23L * 60L * 60L * 1000L)
        assertFalse(shouldShowForceStopBanner(twentyThreeHoursAgo, now))
    }

    @Test
    fun `banner shows when gap exceeds 24h`() {
        val twentyFiveHoursAgo = now - (25L * 60L * 60L * 1000L)
        assertTrue(shouldShowForceStopBanner(twentyFiveHoursAgo, now))
    }

    @Test
    fun `exactly at the threshold does not trigger`() {
        assertFalse(shouldShowForceStopBanner(now - FORCE_STOP_GAP_THRESHOLD_MS, now))
    }
}
