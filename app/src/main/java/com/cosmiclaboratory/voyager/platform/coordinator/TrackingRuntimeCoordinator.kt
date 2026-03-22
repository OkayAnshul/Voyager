package com.cosmiclaboratory.voyager.platform.coordinator

import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.capture.AdaptiveSamplingPolicy
import com.cosmiclaboratory.voyager.domain.model.EmergencySensor
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import com.cosmiclaboratory.voyager.domain.model.enums.GapReason
import com.cosmiclaboratory.voyager.domain.model.enums.PauseReason
import com.cosmiclaboratory.voyager.domain.model.enums.ResumeReason
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.model.enums.StartReason
import com.cosmiclaboratory.voyager.domain.model.enums.StopReason
import com.cosmiclaboratory.voyager.pipeline.PipelineConsumer
import com.cosmiclaboratory.voyager.pipeline.stage.Segmenter
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.TrackingSessionDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TrackingSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateStore: TimelineStateStore,
    private val trackingSessionDao: TrackingSessionDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val healthLogDao: HealthLogDao,
    private val permissionMonitor: PermissionMonitor,
    private val segmenter: Segmenter,
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val pipelineConsumer: PipelineConsumer
) {
    // v2+ emergency hook — null in v1
    var emergencySensorSlot: EmergencySensor? = null

    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        /** Minimum gap duration to create a GAP segment on restore */
        private const val MIN_RESTORE_GAP_MS = 600_000L // 10 minutes
    }

    val runtimeState: StateFlow<TrackingRuntimeState> get() = stateStore.state

    /** Compute dayKey using the device's current timezone, consistent with PipelineConsumer. */
    private fun currentDayKey(): String {
        val zone = ZoneId.of(java.util.TimeZone.getDefault().id)
        return Instant.now().atZone(zone).toLocalDate().format(dayKeyFormatter)
    }

    suspend fun start(reason: StartReason): Result<Long> {
        val currentState = stateStore.getState()
        if (currentState.activeSessionId != null) {
            return Result.failure(IllegalStateException("Session already active"))
        }

        val now = System.currentTimeMillis()
        val session = TrackingSessionEntity(
            startedAt = now,
            startedBy = reason.name,
            localTimeZone = java.util.TimeZone.getDefault().id
        )
        val sessionId = trackingSessionDao.insert(session)
        stateStore.setActiveSession(sessionId)
        // Seed lastAcceptedAt so the gap watchdog doesn't fire before the first sample arrives
        stateStore.update { it.copy(lastAcceptedAt = now) }

        // Log battery optimization status — critical for diagnosing data gaps
        val batteryExempt = permissionMonitor.snapshot.value.isBatteryOptimizationExempt
        if (!batteryExempt) {
            logHealth("BATTERY_WARNING", "Battery optimization NOT exempt — tracking may be killed by OS")
        }

        startForegroundService()

        return Result.success(sessionId)
    }

    suspend fun stop(reason: StopReason): Result<Unit> {
        val currentState = stateStore.getState()
        val sessionId = currentState.activeSessionId
            ?: return Result.success(Unit) // Already stopped — idempotent

        // Flush in-progress segment before stopping so the final movement is persisted
        val dayKey = currentDayKey()
        segmenter.closeCurrentSegment(dayKey)

        val now = System.currentTimeMillis()

        // Close any active visit so it gets proper departure time
        val activeVisit = visitDao.getActiveVisit()
        if (activeVisit != null) {
            visitDao.endVisit(activeVisit.visitId, now, now - activeVisit.arrivalAt)
        }

        val session = trackingSessionDao.getById(sessionId)
        if (session != null) {
            trackingSessionDao.update(session.copy(endedAt = now, endedBy = reason.name))
        }

        // Reset pipeline in-memory state before clearing store state
        pipelineConsumer.resetSessionState()

        stateStore.setActiveSession(null)
        stateStore.setCurrentSegment(null)
        stateStore.setPendingVisitCandidate(null)
        stateStore.setLastConfirmedVisitId(null)
        stateStore.setInProgressSegment(null)

        stopForegroundService()

        return Result.success(Unit)
    }

    suspend fun pause(reason: PauseReason): Result<Unit> {
        val currentState = stateStore.getState()
        if (currentState.activeSessionId == null) {
            return Result.success(Unit) // Nothing to pause — idempotent
        }

        // Flush in-progress segment before pausing
        val dayKey = currentDayKey()
        segmenter.closeCurrentSegment(dayKey)

        logHealth("MANUAL_PAUSE", "Paused: ${reason.name}")
        stopForegroundService()
        return Result.success(Unit)
    }

    suspend fun resume(reason: ResumeReason): Result<Unit> {
        val currentState = stateStore.getState()
        if (currentState.activeSessionId == null) {
            return Result.success(Unit) // Nothing to resume — idempotent
        }

        logHealth("RESUME", "Resumed: ${reason.name}")
        startForegroundService()
        return Result.success(Unit)
    }

    suspend fun restore() {
        val currentState = stateStore.getState()
        val sessionId = currentState.activeSessionId ?: return
        recoverSession(sessionId, "APP_OPEN_RESTORE")
    }

    suspend fun restoreFromCrash() {
        val currentState = stateStore.getState()
        val sessionId = currentState.activeSessionId ?: return
        recoverSession(sessionId, "CRASH_RESTORE")
    }

    private suspend fun recoverSession(sessionId: Long, eventType: String) {
        val session = trackingSessionDao.getById(sessionId)
        if (session != null && session.endedAt == null) {
            val lastAt = stateStore.getState().lastAcceptedAt ?: session.startedAt
            val now = System.currentTimeMillis()
            val silenceMs = now - lastAt

            if (silenceMs > MIN_RESTORE_GAP_MS) {
                val dayKey = currentDayKey()

                // Deduplicate gap segments: the gap watchdog may have already created a
                // GPS_LOSS gap for this period. Upgrade it to PROCESS_DEAD instead of
                // inserting a duplicate.
                val existingGaps = movementSegmentDao.getOverlapping(dayKey, lastAt, now)
                    .filter { it.segmentType == SegmentType.GAP.name }
                if (existingGaps.isNotEmpty()) {
                    val existing = existingGaps.first()
                    movementSegmentDao.update(existing.copy(
                        endAt = maxOf(existing.endAt, now),
                        gapReason = GapReason.PROCESS_DEAD.name
                    ))
                } else {
                    val gapSegment = MovementSegmentEntity(
                        segmentType = SegmentType.GAP.name,
                        startAt = lastAt,
                        endAt = now,
                        gapReason = GapReason.PROCESS_DEAD.name,
                        dayKey = dayKey,
                        confidence = 1.0f
                    )
                    movementSegmentDao.insert(gapSegment)
                }

                // Only close orphaned visits and clear visit state for long gaps (>1 hour).
                // For shorter gaps, preserve departure memory so DetectVisitUseCase can
                // continue the visit naturally on the next sample (prevents fragmentation
                // at home where process kills create 15-30 min gaps).
                if (silenceMs > 3_600_000L) {
                    val activeVisit = visitDao.getActiveVisit()
                    if (activeVisit != null && activeVisit.arrivalAt < lastAt) {
                        visitDao.endVisit(activeVisit.visitId, lastAt, lastAt - activeVisit.arrivalAt)
                    }
                    stateStore.setPendingVisitCandidate(null)
                    stateStore.setLastConfirmedVisitId(null)
                }

                // Reset lastAcceptedAt so the gap watchdog doesn't immediately fire again
                stateStore.update { it.copy(lastAcceptedAt = now) }

                // Restore last known motion state so sampling resumes at the correct rate.
                // Falls back to STILL if no persisted state (e.g., first crash ever).
                val lastMotion = stateStore.getState().lastMotionState?.let {
                    try { AdaptiveSamplingPolicy.MotionState.valueOf(it) } catch (_: Exception) { null }
                } ?: AdaptiveSamplingPolicy.MotionState.STILL
                adaptiveSamplingPolicy.forceMotionState(lastMotion)

                val batteryExempt = permissionMonitor.snapshot.value.isBatteryOptimizationExempt
                logHealth(eventType, "Gap of ${silenceMs / 1000}s detected, session $sessionId, batteryExempt=$batteryExempt")
            }

            startForegroundService()
        } else {
            stateStore.setActiveSession(null)
        }
    }

    private fun startForegroundService() {
        if (!permissionMonitor.hasLocationPermission()) {
            android.util.Log.w("TrackingCoordinator", "Skipping service start — no location permission")
            return
        }
        try {
            val intent = Intent(context, Class.forName("com.cosmiclaboratory.voyager.platform.service.LocationCaptureService"))
            context.startForegroundService(intent)
        } catch (e: Exception) {
            android.util.Log.e("TrackingCoordinator", "Failed to start service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            val intent = Intent(context, Class.forName("com.cosmiclaboratory.voyager.platform.service.LocationCaptureService"))
            context.stopService(intent)
        } catch (e: Exception) {
            android.util.Log.e("TrackingCoordinator", "Failed to stop service", e)
        }
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
