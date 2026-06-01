package com.cosmiclaboratory.voyager.domain.usecase

import kotlin.math.pow

/**
 * Pure confidence-decay calculator for places that haven't been visited recently.
 *
 * A place confirmed years ago still reads its original high confidence even if the
 * user never went back. This biases ranking and UI surfaces toward stale data. We
 * decay confidence after a grace period and floor it — a "once confirmed" place
 * never decays below the floor, but it loses its top-ranking weight if abandoned.
 */
object PlaceConfidenceDecay {

    /** Days a place can sit unvisited before decay starts. */
    const val GRACE_DAYS = 180
    /** Per-day decay multiplier applied beyond the grace period. */
    const val DAILY_MULTIPLIER = 0.99
    /** Decay never carries confidence below this — preserves the "known place" signal. */
    const val FLOOR = 0.4f
    private const val MS_PER_DAY = 86_400_000L

    /**
     * @param currentConfidence the place's current stored confidence.
     * @param lastVisitedAt epoch-millis of the most recent visit; null = never visited.
     * @param now epoch-millis right now (parameterised for testability).
     * @return the decayed confidence, clamped to [FLOOR, currentConfidence].
     */
    fun decay(currentConfidence: Float, lastVisitedAt: Long?, now: Long): Float {
        if (currentConfidence <= FLOOR) return currentConfidence
        if (lastVisitedAt == null) return currentConfidence
        val ageDays = (now - lastVisitedAt) / MS_PER_DAY
        val decayDays = ageDays - GRACE_DAYS
        if (decayDays <= 0) return currentConfidence
        val factor = DAILY_MULTIPLIER.pow(decayDays.toDouble()).toFloat()
        return (currentConfidence * factor).coerceAtLeast(FLOOR)
    }
}
