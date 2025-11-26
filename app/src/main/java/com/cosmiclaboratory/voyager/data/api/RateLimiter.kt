package com.cosmiclaboratory.voyager.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate limiter for API requests
 * Ensures compliance with API usage policies by enforcing minimum time between requests
 * Thread-safe and coroutine-friendly implementation using Mutex
 */
class RateLimiter(private val minIntervalMs: Long) {
    private var lastRequestTime = 0L
    private val mutex = Mutex()

    /**
     * Suspends until enough time has passed since last request
     * Ensures compliance with rate limiting requirements
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime

            if (elapsed < minIntervalMs && lastRequestTime > 0) {
                val waitTime = minIntervalMs - elapsed
                delay(waitTime)
            }

            lastRequestTime = System.currentTimeMillis()
        }
    }
}
