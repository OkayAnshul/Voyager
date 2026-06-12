package com.cosmiclaboratory.voyager.domain.usecase

/**
 * Pure data-retention policy for [com.cosmiclaboratory.voyager.platform.worker.DataRetentionWorker].
 *
 * A retention window is a number of days; a **negative** value means "keep forever". The worker
 * deletes rows older than [cutoffMs] — and a null cutoff means *delete nothing*. Routing the
 * "forever" case through null is also a safety guard: a naive `now - days * MS_PER_DAY` on a
 * negative value would put the cutoff in the *future*, and `deleteOlderThan(future)` would wipe
 * the whole table. Centralising the rule here keeps every tier safe and testable.
 */
object RetentionPolicy {

    const val MS_PER_DAY = 24L * 60 * 60 * 1000

    /** True when [days] means keep-forever (no deletion). */
    fun keepsForever(days: Int): Boolean = days < 0

    /**
     * Epoch-ms cutoff for a retention window: rows strictly older than this may be deleted.
     * Returns null when [days] is negative ("keep forever") — the caller must then delete nothing.
     */
    fun cutoffMs(nowMs: Long, days: Int): Long? =
        if (days < 0) null else nowMs - days * MS_PER_DAY

    /**
     * Size-adaptive raw retention: shorten the window when the database is large, but never make
     * it *longer* than the user-configured [configuredDays], and never override a forever
     * (negative) setting — the user's explicit "keep forever" wins over the size trim.
     */
    fun effectiveRawRetentionDays(configuredDays: Int, dbSizeMb: Long): Int {
        if (configuredDays < 0) return configuredDays
        return when {
            dbSizeMb > 1024 -> minOf(30, configuredDays) // > 1 GB: aggressive trim
            dbSizeMb > 500 -> minOf(60, configuredDays)  // > 500 MB: moderate trim
            else -> configuredDays
        }
    }
}
