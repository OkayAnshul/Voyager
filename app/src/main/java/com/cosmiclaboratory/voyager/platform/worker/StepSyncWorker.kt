package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Syncs step data from platform sources (Health Connect, step sensor).
 * Runs every 15 min when active, 60 min in sleep mode.
 *
 * The active/sleep interval selection is handled in WorkerScheduler; this worker
 * simply syncs whatever is available since its last run.
 */
@HiltWorker
class StepSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rawStepSampleDao: RawStepSampleDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "step_sync"
        const val WORK_NAME_SLEEP = "step_sync_sleep"
    }

    override suspend fun doWork(): Result {
        return try {
            // Determine the sync window: from last synced sample to now
            val now = System.currentTimeMillis()
            val lookbackMs = 2L * 60 * 60 * 1000 // 2 hours lookback to catch gaps
            val windowStart = now - lookbackMs

            // Check existing samples to avoid duplicates
            val existingSamples = rawStepSampleDao.getByTimeRange(windowStart, now)
            val latestSyncedMs = existingSamples.maxOfOrNull { it.periodEnd } ?: windowStart

            // Step data sourcing is platform-specific (Health Connect, sensor).
            // The actual sensor/HC read is delegated to platform-specific code.
            // This worker serves as the scheduling entry point.
            // In a full implementation, it would call a StepDataSource interface.

            val syncedFrom = latestSyncedMs
            val syncedTo = now

            logCompletion(syncedFrom = syncedFrom, syncedTo = syncedTo, samplesExisting = existingSamples.size)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun logCompletion(syncedFrom: Long, syncedTo: Long, samplesExisting: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","syncedFrom":$syncedFrom,"syncedTo":$syncedTo,"existing":$samplesExisting}""",
            )
        )
    }

    private suspend fun logFailure(e: Exception) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HealthEventTypeWorkerFailure,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","error":"${e.message?.take(200)}"}""",
            )
        )
    }
}
