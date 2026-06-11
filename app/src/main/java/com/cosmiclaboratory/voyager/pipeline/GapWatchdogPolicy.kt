package com.cosmiclaboratory.voyager.pipeline

/**
 * Pure decision for the gap watchdog — extracted from [PipelineConsumer] so the thresholds
 * are unit-testable without the coroutine loop and channel.
 *
 * A GAP segment marks a stretch where tracking was expected but no sample arrived. We only
 * raise one when active GPS is actually running (interval > 0), the silence is both well
 * past the expected cadence and beyond an absolute floor, we're not in the post-dormant
 * settle window, and we haven't already recorded a gap for this same last-sample.
 */
object GapWatchdogPolicy {
    /** Silence must exceed this multiple of the expected sampling interval. */
    const val SILENCE_MULTIPLIER = 5
    /** How often the watchdog wakes to check. */
    const val CHECK_INTERVAL_MS = 60_000L
    /** Absolute floor on gap duration — shorter silences aren't worth a GAP row. */
    const val MIN_GAP_DURATION_MS = 600_000L

    /**
     * @param silenceMs time since the last accepted sample.
     * @param expectedIntervalMs current sampling interval (0 when GPS is off — OFF/PASSIVE/DORMANT).
     * @param inDormantGrace true during the post-dormant-exit settle window (GPS still warming up).
     * @param lastAcceptedAt timestamp of the last accepted sample.
     * @param lastGapCreatedAt the [lastAcceptedAt] a gap was already created for (dedup).
     */
    fun shouldCreateGap(
        silenceMs: Long,
        expectedIntervalMs: Long,
        inDormantGrace: Boolean,
        lastAcceptedAt: Long,
        lastGapCreatedAt: Long,
    ): Boolean =
        expectedIntervalMs > 0 &&
            !inDormantGrace &&
            silenceMs > expectedIntervalMs * SILENCE_MULTIPLIER &&
            silenceMs >= MIN_GAP_DURATION_MS &&
            lastAcceptedAt != lastGapCreatedAt

    /** A gap during DORMANT is intentional (GPS deliberately off); otherwise it's unexpected loss. */
    fun gapReason(isDormant: Boolean): String = if (isDormant) "DORMANT" else "GPS_LOSS"
}
