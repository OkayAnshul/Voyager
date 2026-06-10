package com.cosmiclaboratory.voyager.capture

import java.util.concurrent.atomic.AtomicLong

/**
 * Pure, thread-safe admission gate for raw location samples — the dedup + sanity logic
 * that decides whether a fix from FusedLocationProvider should enter the pipeline.
 *
 * Extracted from [LocationCapture] so the decision logic is unit-testable in isolation
 * (no Android dependencies) and the concurrency contract is explicit.
 *
 * Decision order (each FLP callback runs this once per fix):
 *  1. `locTs <= 0`                              -> [Admission.Invalid]
 *  2. `locTs > now + futureSlackMs`             -> [Admission.RejectedFuture] (manual time-travel / corrupt fix)
 *  3. `last > 0 && locTs < last - pastSlackMs`  -> [Admission.RejectedPast] (stale cached fix teleporting back)
 *  4. CAS accept only **strictly-increasing** timestamps -> [Admission.Accepted], else [Admission.Duplicate]
 *
 * Step 4 uses strictly-greater (not `>=`): two deliveries of the *same* fix — e.g. the active
 * and passive FLP callbacks both reporting one location — share `location.time`, so the second
 * is a duplicate and must be dropped. The CAS also makes concurrent callbacks safe: for any set
 * of calls with the same timestamp, exactly one is accepted.
 */
class SampleAdmissionGate(
    private val futureSlackMs: Long = ONE_HOUR_MS,
    private val pastSlackMs: Long = TEN_MIN_MS,
) {
    private val lastAcceptedTs = AtomicLong(0L)

    /** Timestamp of the most recent accepted fix, or 0 if none yet. */
    val lastAccepted: Long get() = lastAcceptedTs.get()

    /**
     * Seed the watermark from persisted state so stale cached fixes that arrive right after a
     * process restart are rejected by the past gate. Never moves the watermark backwards.
     */
    fun seed(persistedTs: Long) {
        if (persistedTs > 0) lastAcceptedTs.updateAndGet { current -> maxOf(current, persistedTs) }
    }

    /**
     * @param locTs the fix's wall-clock time (`location.time`), in epoch millis.
     * @param nowMs current wall-clock time, in epoch millis.
     */
    fun admit(locTs: Long, nowMs: Long): Admission {
        if (locTs <= 0L) return Admission.Invalid
        if (locTs > nowMs + futureSlackMs) return Admission.RejectedFuture

        val prev = lastAcceptedTs.get()
        if (prev > 0 && locTs < prev - pastSlackMs) return Admission.RejectedPast

        // Atomic strictly-increasing watermark. getAndUpdate returns the prior value, so we
        // "won" (and therefore advanced it) iff locTs was strictly greater than that prior value.
        val before = lastAcceptedTs.getAndUpdate { current -> if (locTs > current) locTs else current }
        return if (locTs > before) Admission.Accepted else Admission.Duplicate
    }

    enum class Admission { Accepted, Duplicate, Invalid, RejectedFuture, RejectedPast }

    companion object {
        const val ONE_HOUR_MS = 60L * 60L * 1000L
        const val TEN_MIN_MS = 10L * 60L * 1000L
    }
}
