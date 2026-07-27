package com.cosmiclaboratory.voyager.platform.battery

import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estimates how fast the device battery drains while Voyager is tracking.
 *
 * This is an *honest device-discharge* figure measured from the `batteryPct`
 * already recorded on every raw sample — it is the phone's overall drain during
 * tracking hours, not an isolated "Voyager cost" (per-app attribution is not
 * reliably available from userspace). It lets users sanity-check that running
 * Voyager isn't unusual.
 *
 * Contract: [Estimate.percentPerDay] is deliberately **withheld (null) until the
 * measurement is trustworthy**. Battery level is reported only in whole percent,
 * so extrapolating a "/day" rate from a few minutes of tracking turns a single
 * 1% tick into a wild over-estimate. We publish a number only once enough
 * discharge time AND enough total drop have been observed that quantisation and
 * extrapolation error are small — otherwise we under-claim (show nothing) rather
 * than over-claim. Consumers rely on this: a null estimate means "don't act / don't
 * show" — [com.cosmiclaboratory.voyager.platform.worker.BatteryBudgetWorker] will
 * not downgrade the tracking tier on a null, and the dashboard card stays hidden.
 */
@Singleton
class BatteryUsageReporter @Inject constructor(
    private val rawLocationSampleDao: RawLocationSampleDao
) {
    companion object {
        private const val WINDOW_MS = 24L * 60 * 60 * 1000
        private const val MS_PER_HOUR = 60.0 * 60 * 1000
        private const val MS_PER_DAY = 24.0 * MS_PER_HOUR

        /**
         * A single interval implying a faster drain than this is treated as a glitch
         * (calibration jump, sensor hiccup) and ignored. Filtering by *rate* — not by
         * absolute drop — means a big-but-slow drop over a long gap (e.g. 30% over 6h
         * idle) is correctly counted instead of discarded. 25%/h would flatten a full
         * battery in four hours, well beyond any sustained background load.
         */
        private const val MAX_PLAUSIBLE_RATE_PER_HOUR = 25.0

        /** Below this much measured discharge time the extrapolation to a whole day is not trustworthy. */
        private const val MIN_MEASURED_MS = 6L * 60 * 60 * 1000

        /** Below this much total observed drop, integer-percent quantisation dominates the rate. */
        private const val MIN_OBSERVED_DROP_PCT = 5.0

        /** A published /day figure above this means the inputs were bad, not that the phone dies daily. */
        private const val MAX_PLAUSIBLE_PER_DAY = 60
    }

    data class Estimate(
        /** Whole-device discharge per day during tracking; null until measured with confidence. */
        val percentPerDay: Int?,
        /** Hours of genuine discharge observed in the window — a progress/confidence proxy, always reported. */
        val measuredOverHours: Int
    )

    suspend fun estimate(now: Long = System.currentTimeMillis()): Estimate {
        val samples = rawLocationSampleDao.getByTimeRange(now - WINDOW_MS, now)
            .filter { it.batteryPct != null }
        if (samples.size < 2) return Estimate(percentPerDay = null, measuredOverHours = 0)

        var dischargePct = 0.0
        var dischargeMs = 0L
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val cur = samples[i]
            // Only count stretches where the phone was discharging, not charging.
            if (prev.isCharging || cur.isCharging) continue
            val dtMs = cur.capturedAt - prev.capturedAt
            if (dtMs <= 0L) continue
            val drop = (prev.batteryPct ?: continue) - (cur.batteryPct ?: continue)
            if (drop < 0) continue // level rose while "not charging" — glitch
            // Reject glitchy spikes by rate, not absolute drop.
            val ratePerHour = drop / (dtMs / MS_PER_HOUR)
            if (ratePerHour > MAX_PLAUSIBLE_RATE_PER_HOUR) continue
            dischargePct += drop
            dischargeMs += dtMs
        }

        val measuredHours = (dischargeMs / MS_PER_HOUR).toInt()
        // Withhold the number until there is enough signal to trust it.
        if (dischargeMs < MIN_MEASURED_MS || dischargePct < MIN_OBSERVED_DROP_PCT) {
            return Estimate(percentPerDay = null, measuredOverHours = measuredHours)
        }
        val perDay = (dischargePct / dischargeMs * MS_PER_DAY).toInt()
        // An implausible result means the inputs were bad — say nothing rather than lie.
        if (perDay <= 0 || perDay > MAX_PLAUSIBLE_PER_DAY) {
            return Estimate(percentPerDay = null, measuredOverHours = measuredHours)
        }
        return Estimate(percentPerDay = perDay, measuredOverHours = measuredHours)
    }
}
