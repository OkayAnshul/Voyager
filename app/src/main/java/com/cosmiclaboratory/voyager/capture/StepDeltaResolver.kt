package com.cosmiclaboratory.voyager.capture

/**
 * Pure step-counter delta + batching logic, extracted from [StepCapture] so the
 * reboot / baseline / batch-window rules are unit-testable without the Android sensor
 * stack.
 *
 * `TYPE_STEP_COUNTER` reports a cumulative count since device boot. This converts that
 * monotonic stream into batched per-window step deltas, and detects the reboot reset
 * (the count drops) so it never emits a huge spurious spike (see T7).
 *
 * Not thread-safe by design — the caller drives it from the single sensor-callback thread.
 */
class StepDeltaResolver(private val batchWindowMs: Long = DEFAULT_BATCH_WINDOW_MS) {

    private var baseline: Int? = null   // cumulative count at the last emitted batch
    private var latest: Int? = null     // most recent observed cumulative count
    private var windowStart: Long = 0L

    sealed interface Outcome {
        /** First reading after (re)start — baseline set, nothing to emit. */
        data object Baseline : Outcome
        /** Counter went backwards → device reboot; baseline reset, partial dropped. */
        data object Reboot : Outcome
        /** Steps accumulating inside the batch window (or none yet) — keep buffering. */
        data object Buffer : Outcome
        /** A completed batch to persist. */
        data class Emit(val steps: Int, val periodStart: Long, val periodEnd: Long) : Outcome
    }

    /** Reset all state — call on start(), anchoring the first window to [now]. */
    fun reset(now: Long) {
        baseline = null
        latest = null
        windowStart = now
    }

    /** Feed a cumulative counter reading. */
    fun onCount(currentCount: Int, now: Long): Outcome {
        val base = baseline
        if (base == null) {
            baseline = currentCount
            latest = currentCount
            windowStart = now
            return Outcome.Baseline
        }
        if (currentCount < base) {
            // Reboot: the counter reset toward zero. Reset baseline, drop the partial —
            // emitting (current - base) here would be a large negative/garbage value.
            baseline = currentCount
            latest = currentCount
            windowStart = now
            return Outcome.Reboot
        }
        latest = currentCount
        val delta = currentCount - base
        if (delta > 0 && now - windowStart > batchWindowMs) {
            val start = windowStart
            baseline = currentCount
            windowStart = now
            return Outcome.Emit(delta, start, now)
        }
        return Outcome.Buffer
    }

    /**
     * Flush buffered steps without waiting for the batch window — call on stop() so the
     * tail of a walk isn't dropped on each pause/resume. Returns null if nothing pending.
     */
    fun flush(now: Long): Outcome.Emit? {
        val base = baseline ?: return null
        val cur = latest ?: return null
        val delta = cur - base
        if (delta <= 0) return null
        val start = windowStart
        baseline = cur
        windowStart = now
        return Outcome.Emit(delta, start, now)
    }

    companion object {
        const val DEFAULT_BATCH_WINDOW_MS = 5_000L
    }
}
