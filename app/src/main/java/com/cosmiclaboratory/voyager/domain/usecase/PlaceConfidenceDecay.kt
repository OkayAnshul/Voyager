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
    /** A fully-recurring place ([repeatability]=1) earns up to this many extra grace days. */
    const val REPEAT_GRACE_BONUS_DAYS = 365
    /** …and a raised floor, so a regular haunt never decays toward "maybe real". */
    const val REPEAT_FLOOR_MAX = 0.7f
    private const val MS_PER_DAY = 86_400_000L

    /**
     * @param currentConfidence the place's current stored confidence.
     * @param lastVisitedAt epoch-millis of the most recent visit; null = never visited.
     * @param now epoch-millis right now (parameterised for testability).
     * @param repeatability 0–1 recurring-haunt score ([PlaceRepeatability]); a higher score
     *        extends the grace period and raises the floor so regular places resist decay (C1).
     *        Defaults to 0 — identical to the original behaviour.
     * @return the decayed confidence, clamped to [effective floor, currentConfidence].
     */
    fun decay(currentConfidence: Float, lastVisitedAt: Long?, now: Long, repeatability: Float = 0f): Float {
        val repeat = repeatability.coerceIn(0f, 1f)
        val floor = FLOOR + repeat * (REPEAT_FLOOR_MAX - FLOOR)
        if (currentConfidence <= floor) return currentConfidence
        if (lastVisitedAt == null) return currentConfidence
        val ageDays = (now - lastVisitedAt) / MS_PER_DAY
        val grace = GRACE_DAYS + (repeat * REPEAT_GRACE_BONUS_DAYS).toInt()
        val decayDays = ageDays - grace
        if (decayDays <= 0) return currentConfidence
        val factor = DAILY_MULTIPLIER.pow(decayDays.toDouble()).toFloat()
        return (currentConfidence * factor).coerceAtLeast(floor)
    }
}
