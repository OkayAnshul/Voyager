package com.cosmiclaboratory.voyager.domain.usecase

/**
 * Pure 0–1 score of how much a place is a recurring haunt (C1). A place you return to is real
 * and stays real, so this feeds [PlaceConfidenceDecay] to make regular places resist decay —
 * "court/IRS-grade" trust shouldn't fade for the café you visit every week.
 *
 * Blends **recent frequency** (visits in the last 30 days) with **established history** (lifetime
 * visits); both saturate, so a daily regular and a weekly regular both read as highly repeatable.
 */
object PlaceRepeatability {

    /** Last-30-day visits that fully saturate the recency component (≈ twice a week). */
    const val REGULAR_30D_VISITS = 8f

    /** Lifetime visits that fully saturate the established-history component. */
    const val ESTABLISHED_TOTAL_VISITS = 20f

    private const val RECENCY_WEIGHT = 0.7f
    private const val ESTABLISHED_WEIGHT = 0.3f

    fun score(totalVisitCount: Int, visitCountLast30d: Int): Float {
        val recency = (visitCountLast30d / REGULAR_30D_VISITS).coerceIn(0f, 1f)
        val established = (totalVisitCount / ESTABLISHED_TOTAL_VISITS).coerceIn(0f, 1f)
        return (recency * RECENCY_WEIGHT + established * ESTABLISHED_WEIGHT).coerceIn(0f, 1f)
    }
}
