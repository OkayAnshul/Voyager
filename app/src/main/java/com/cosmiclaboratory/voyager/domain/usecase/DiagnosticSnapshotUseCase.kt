package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.platform.battery.BatteryUsageReporter
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import javax.inject.Inject

/**
 * Read-only "is Voyager behaving?" snapshot for the diagnostics screen.
 *
 * Pulls together the signals the user can sanity-check without opening
 * dumpsys: how fast the battery drained while tracking, how many samples
 * arrived in the last day, and how many worker failures (if any) have
 * been logged. All numbers are honest measurements, not estimates.
 *
 * UI rendering is deferred to the Figma pass; this surface is the data
 * contract that the UI will bind to.
 */
data class DiagnosticSnapshot(
    val batteryPctPerDay: Int?,
    val batteryMeasuredOverHours: Int,
    val sampleCountLast24h: Int,
    val workerFailuresLast24h: Int,
    val healthEventsLast24h: Int,
)

class DiagnosticSnapshotUseCase @Inject constructor(
    private val batteryUsageReporter: BatteryUsageReporter,
    private val rawLocationSampleDao: RawLocationSampleDao,
    private val healthLogDao: HealthLogDao,
) {

    suspend fun snapshot(now: Long = System.currentTimeMillis()): DiagnosticSnapshot {
        val estimate = batteryUsageReporter.estimate(now)
        val sampleCount = rawLocationSampleDao
            .getByTimeRange(now - WINDOW_MS, now).size
        val events = healthLogDao.getEventsSince(now - WINDOW_MS)
        val failures = events.count { it.eventType == "WORKER_FAILURE" }
        return DiagnosticSnapshot(
            batteryPctPerDay = estimate.percentPerDay,
            batteryMeasuredOverHours = estimate.measuredOverHours,
            sampleCountLast24h = sampleCount,
            workerFailuresLast24h = failures,
            healthEventsLast24h = events.size,
        )
    }

    companion object {
        private const val WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
