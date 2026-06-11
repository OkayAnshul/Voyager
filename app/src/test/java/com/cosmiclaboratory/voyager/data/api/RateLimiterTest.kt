package com.cosmiclaboratory.voyager.data.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    @Test
    fun `the first acquire never waits`() = runTest {
        val limiter = RateLimiter(minIntervalMs = 10_000)
        val before = testScheduler.currentTime
        limiter.acquire()
        assertEquals("first call has no prior request to wait behind", before, testScheduler.currentTime)
    }

    @Test
    fun `a second back-to-back acquire is throttled to the minimum interval`() = runTest {
        val limiter = RateLimiter(minIntervalMs = 10_000)
        limiter.acquire()
        val before = testScheduler.currentTime
        limiter.acquire() // immediately after — must wait out the window
        // delay() advances runTest's virtual clock; real elapsed between the two calls is ~0,
        // so the enforced wait is ~minIntervalMs.
        assertTrue(
            "expected ~10s throttle, virtual elapsed was ${testScheduler.currentTime - before}ms",
            testScheduler.currentTime - before >= 9_000
        )
    }
}
