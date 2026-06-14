package com.cosmiclaboratory.voyager.presentation.screen.workout

/**
 * Pure display formatters for workout stats — the Strava-style readouts (pace, duration, etc.).
 * No Android/Compose deps, so the user-facing number formatting is unit-tested.
 */
object WorkoutFormat {

    /** Kilometres to two decimals, e.g. `5.42`. */
    fun distanceKm(meters: Double): String = "%.2f".format(meters / 1000.0)

    /** Elapsed time as `h:mm:ss` once an hour is reached, otherwise `m:ss`. */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Pace as `m:ss` per km, or `—` when there's no valid pace yet (not moving / divide-by-zero). */
    fun pace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm <= 0.0 || secPerKm.isNaN() || secPerKm.isInfinite()) return "—"
        val t = secPerKm.toLong()
        return "%d:%02d".format(t / 60, t % 60)
    }

    /** Speed in km/h to one decimal. */
    fun speedKmh(mps: Float): String = "%.1f".format(mps * 3.6f)
}
