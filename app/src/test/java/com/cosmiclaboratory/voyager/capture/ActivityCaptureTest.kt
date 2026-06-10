package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.capture.ActivityCapture.Companion.AR_STALE_TIMEOUT_MS
import com.cosmiclaboratory.voyager.capture.ActivityCapture.Companion.shouldReRegister
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCaptureTest {

    private val now = 1_700_000_000_000L
    private val session = 42L

    @Test
    fun `not stale within the timeout`() {
        val lastAt = now - (AR_STALE_TIMEOUT_MS - 1)
        assertFalse(shouldReRegister(now, lastAt, session, AR_STALE_TIMEOUT_MS))
    }

    @Test
    fun `stale once silence exceeds the timeout`() {
        val lastAt = now - (AR_STALE_TIMEOUT_MS + 1)
        assertTrue(shouldReRegister(now, lastAt, session, AR_STALE_TIMEOUT_MS))
    }

    @Test
    fun `exactly at the timeout is not yet stale`() {
        val lastAt = now - AR_STALE_TIMEOUT_MS
        assertFalse(shouldReRegister(now, lastAt, session, AR_STALE_TIMEOUT_MS))
    }

    @Test
    fun `never re-registers when no session is active`() {
        // Even arbitrarily stale: no active session means nothing to recover.
        val lastAt = now - 24L * 60 * 60 * 1000
        assertFalse(shouldReRegister(now, lastAt, activeSessionId = 0L, timeoutMs = AR_STALE_TIMEOUT_MS))
    }
}
