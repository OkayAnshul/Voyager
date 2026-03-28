package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.GapReason
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.platform.coordinator.TrackingRuntimeCoordinator
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Periodic health check that detects when the tracking service has silently died
 * (common on aggressive OEMs like Samsung, Xiaomi, OPPO) and restarts it.
 *
 * Runs every 15 minutes via WorkManager — survives process death because
 * WorkManager scheduling is managed by the OS, not our process.
 *
 * No battery constraint: this is critical infrastructure for tracking reliability.
 */
@HiltWorker
class TrackingHealthCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val stateStore: TimelineStateStore,
    private val coordinator: TrackingRuntimeCoordinator,
    private val healthLogDao: HealthLogDao,
    private val movementSegmentDao: MovementSegmentDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "tracking_health_check"
        /** If no sample for this long while tracking is active, consider the service dead */
        private const val MAX_SILENCE_MS = 180_000L // 3 minutes
    }

    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override suspend fun doWork(): Result {
        val state = stateStore.getState()

        // Only act if tracking is supposed to be active
        if (state.activeSessionId == null) return Result.success()

        val lastAt = state.lastAcceptedAt
        val now = System.currentTimeMillis()

        if (lastAt == null || (now - lastAt) > MAX_SILENCE_MS) {
            // Service appears dead — restart it.
            // Don't create a GAP here — restoreFromCrash() handles gap creation
            // with proper minimum-duration checks. Creating one here would duplicate.
            coordinator.restoreFromCrash()

            val silenceSeconds = (now - (lastAt ?: (now - MAX_SILENCE_MS))) / 1000
            logHealth("HEALTH_CHECK_RESTART",
                "Service silent for ${silenceSeconds}s, triggered restore")
        }

        return Result.success()
    }

    private suspend fun logHealth(eventType: String, details: String) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = eventType,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"message":"$details"}"""
            )
        )
    }
}
