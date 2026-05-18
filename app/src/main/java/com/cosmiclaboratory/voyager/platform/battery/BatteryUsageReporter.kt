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
 */
@Singleton
class BatteryUsageReporter @Inject constructor(
    private val rawLocationSampleDao: RawLocationSampleDao
) {
    companion object {
        private const val WINDOW_MS = 24L * 60 * 60 * 1000
        private const val MS_PER_DAY = 24.0 * 60 * 60 * 1000
        /** Ignore implausible jumps (charging spikes, sensor glitches). */
        private const val MAX_PLAUSIBLE_DROP = 40
    }

    data class Estimate(
        /** Whole-device discharge per day during tracking, null if not enough data. */
        val percentPerDay: Int?,
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
            if (prev.isCharging == true || cur.isCharging == true) continue
            val drop = (prev.batteryPct ?: 0) - (cur.batteryPct ?: 0)
            if (drop in 0..MAX_PLAUSIBLE_DROP) {
                dischargePct += drop
                dischargeMs += cur.capturedAt - prev.capturedAt
            }
        }
        if (dischargeMs <= 0L) return Estimate(percentPerDay = null, measuredOverHours = 0)
        val perDay = (dischargePct / dischargeMs * MS_PER_DAY).toInt()
        return Estimate(
            percentPerDay = perDay.coerceIn(0, 100),
            measuredOverHours = (dischargeMs / (60L * 60 * 1000)).toInt()
        )
    }
}
