package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.pipeline.StepBucket

/**
 * Derives a steps-per-minute cadence from recent pedometer buckets, for motion fusion
 * ([com.cosmiclaboratory.voyager.domain.usecase.FuseActivityStateUseCase]).
 *
 * Why this exists (T7): the cadence used to be `totalSteps / bucketSpan`, where `bucketSpan`
 * was the buckets' *own* covered period. A brief desk shuffle — e.g. 5 steps inside a 2 s
 * bucket — then extrapolated to `(5 / 2 s) × 60 = 150 spm`, which fusion reads as RUNNING.
 * That defeated the deliberately-high 100/140 spm fusion thresholds, because the rate itself
 * was inflated by extrapolating a tiny burst over a tiny span.
 *
 * The fix floors the denominator at [MIN_RELIABLE_SPAN_MS]: a real sustained walk easily fills
 * that window (≈100 steps/min), while a short burst is divided by the floor instead of its own
 * span, so 5 steps map to ~10 spm — correctly *not* walking/running. Genuine stillness (0–few
 * steps over the window) still yields a near-zero rate, preserving fusion's "<5 spm ⇒ STILL"
 * correction of a drifting Activity-Recognition reading.
 */
object StepRateCalculator {

    /** Trailing window the pipeline collects step buckets over. */
    const val WINDOW_MS = 60_000L

    /** Cadence measured over a span shorter than this is unreliable — a brief burst would
     *  extrapolate to a phantom high cadence — so the denominator is floored here. */
    const val MIN_RELIABLE_SPAN_MS = 30_000L

    /**
     * Steps per minute over [buckets], or null when there is no pedometer signal at all
     * (empty, or a degenerate zero-length span). A near-zero rate is returned (not null) when
     * buckets exist but hold few/no steps, so stillness remains a usable signal.
     */
    fun stepsPerMinute(buckets: List<StepBucket>): Float? {
        if (buckets.isEmpty()) return null
        val observedSpanMs = buckets.maxOf { it.periodEnd } - buckets.minOf { it.periodStart }
        if (observedSpanMs <= 0) return null
        val totalSteps = buckets.sumOf { it.stepCount }
        val denomMs = maxOf(observedSpanMs, MIN_RELIABLE_SPAN_MS)
        return totalSteps.toFloat() / denomMs * 60_000f
    }
}
