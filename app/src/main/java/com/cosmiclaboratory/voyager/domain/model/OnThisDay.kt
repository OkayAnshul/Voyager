package com.cosmiclaboratory.voyager.domain.model

/**
 * A day from a previous year, resurfaced as an "on this day" memory.
 *
 * Derived read-only from [com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity]
 * — no new storage. The dashboard card renders it; the full day is reachable by
 * deep-linking the timeline to [dayKey].
 */
data class OnThisDayMemory(
    /** ISO `yyyy-MM-dd` of the past day. */
    val dayKey: String,
    /** How many years ago — 1 means exactly one year ago today. */
    val yearsAgo: Int,
    val distanceM: Double,
    val steps: Int,
    val placeVisitCount: Int,
    val dominantTransportMode: String?
)
