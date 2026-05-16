package com.cosmiclaboratory.voyager.storage

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.InProgressSegmentSnapshot
import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import com.cosmiclaboratory.voyager.domain.time.Clock
import com.cosmiclaboratory.voyager.storage.database.dao.CurrentRuntimeStateDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.CurrentRuntimeStateEntity
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val EMPTY_STATE = TrackingRuntimeState(
    activeSessionId = null,
    currentSegmentId = null,
    pendingVisitCandidate = null,
    lastConfirmedVisitId = null,
    lastAcceptedSampleId = null,
    lastAcceptedAt = null,
    stateVersion = 0,
    lastPipelineLatencyMs = null
)

@Singleton
class TimelineStateStore @Inject constructor(
    private val currentRuntimeStateDao: CurrentRuntimeStateDao,
    private val healthLogDao: HealthLogDao,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Serializes all RMW updates to the single runtime-state row (H1 fix).
     *
     * Without this, two coroutines both calling `currentRuntimeStateDao.atomicUpdate {}`
     * concurrently could each read the same snapshot, transform it differently, and
     * race to write — silently losing one update. Room's @Transaction batches the SQL
     * but does not serialize the application-level transform lambda.
     *
     * Mutex is inside this class because every write funnels through `update()`.
     * The lock holds only across the in-memory transform + DAO call; the actual SQL
     * write runs inside Room's transaction queue.
     */
    private val updateMutex = Mutex()

    /** In-memory only — transient snapshot of the segment the Segmenter is accumulating. */
    private val _inProgressSegment = MutableStateFlow<InProgressSegmentSnapshot?>(null)
    val inProgressSegment: StateFlow<InProgressSegmentSnapshot?> = _inProgressSegment.asStateFlow()

    fun setInProgressSegment(snapshot: InProgressSegmentSnapshot?) {
        _inProgressSegment.value = snapshot
    }

    val state: StateFlow<TrackingRuntimeState> = currentRuntimeStateDao.observe()
        .map { entity -> entity?.toDomainModel() ?: EMPTY_STATE }
        .stateIn(scope, SharingStarted.Eagerly, EMPTY_STATE)

    suspend fun initialize() {
        val existing = currentRuntimeStateDao.get()
        if (existing == null) {
            currentRuntimeStateDao.upsert(CurrentRuntimeStateEntity())
        }
    }

    suspend fun update(transform: (TrackingRuntimeState) -> TrackingRuntimeState) = updateMutex.withLock {
        currentRuntimeStateDao.atomicUpdate { entity ->
            val currentDomain = entity.toDomainModel()
            val updatedDomain = transform(currentDomain)
            updatedDomain.toEntity(entity.stateVersion)
        }
    }

    suspend fun setActiveSession(sessionId: Long?) {
        update { it.copy(activeSessionId = sessionId) }
    }

    suspend fun setCurrentSegment(segmentId: Long?) {
        update { it.copy(currentSegmentId = segmentId) }
    }

    suspend fun setPendingVisitCandidate(candidate: PendingVisitCandidate?) {
        update { it.copy(pendingVisitCandidate = candidate) }
    }

    suspend fun setLastConfirmedVisitId(visitId: Long?) {
        update { it.copy(lastConfirmedVisitId = visitId) }
    }

    suspend fun recordTimestamp(timestamp: Long) {
        update { it.copy(lastAcceptedAt = timestamp) }
    }

    suspend fun recordSampleAccepted(sampleId: Long, timestamp: Long, latencyMs: Long) {
        update {
            it.copy(
                lastAcceptedSampleId = sampleId,
                lastAcceptedAt = timestamp,
                lastPipelineLatencyMs = latencyMs
            )
        }
    }

    suspend fun getState(): TrackingRuntimeState {
        val entity = currentRuntimeStateDao.get()
        return entity?.toDomainModel() ?: EMPTY_STATE
    }

    private fun CurrentRuntimeStateEntity.toDomainModel(): TrackingRuntimeState {
        val pendingCandidate = pendingVisitCandidateJson?.let { raw ->
            try {
                json.decodeFromString<PendingVisitCandidate>(raw)
            } catch (e: Exception) {
                // H10 fix: don't silently drop. Surface as HealthLog so users can see
                // the pending candidate was lost due to corruption — not "first run".
                Log.w("TimelineStateStore", "Failed to deserialize PendingVisitCandidate", e)
                logCorruption("pendingVisitCandidateJson", raw.take(200), e.message)
                null
            }
        }
        return TrackingRuntimeState(
            activeSessionId = activeSessionId,
            currentSegmentId = currentSegmentId,
            pendingVisitCandidate = pendingCandidate,
            lastConfirmedVisitId = lastConfirmedVisitId,
            lastAcceptedSampleId = lastAcceptedSampleId,
            lastAcceptedAt = lastAcceptedAt,
            stateVersion = stateVersion,
            lastPipelineLatencyMs = lastPipelineLatencyMs,
            lastMotionState = lastMotionState,
            lastDepartedCentroidLat = lastDepartedCentroidLat,
            lastDepartedCentroidLng = lastDepartedCentroidLng,
            lastDepartureTime = lastDepartureTime,
            lastDepartedVisitId = lastDepartedVisitId
        )
    }

    private fun logCorruption(field: String, sample: String, errMsg: String?) {
        scope.launch {
            try {
                healthLogDao.insert(
                    HealthLogEntity(
                        eventType = "STATE_CORRUPTION",
                        eventAt = clock.wallClockMs(),
                        detailsJson = "{\"field\":\"$field\"," +
                            "\"sample\":\"${sample.replace("\"", "'")}\"," +
                            "\"err\":\"${errMsg?.replace("\"", "'") ?: ""}\"}"
                    )
                )
            } catch (_: Throwable) {
                // best-effort
            }
        }
    }

    private fun TrackingRuntimeState.toEntity(currentVersion: Long): CurrentRuntimeStateEntity {
        val candidateJson = pendingVisitCandidate?.let {
            json.encodeToString(it)
        }
        return CurrentRuntimeStateEntity(
            id = 1,
            activeSessionId = activeSessionId,
            currentSegmentId = currentSegmentId,
            pendingVisitCandidateJson = candidateJson,
            lastConfirmedVisitId = lastConfirmedVisitId,
            lastAcceptedSampleId = lastAcceptedSampleId,
            lastAcceptedAt = lastAcceptedAt,
            stateVersion = currentVersion, // DAO.atomicUpdate() handles the +1
            lastPipelineLatencyMs = lastPipelineLatencyMs,
            lastMotionState = lastMotionState,
            lastDepartedCentroidLat = lastDepartedCentroidLat,
            lastDepartedCentroidLng = lastDepartedCentroidLng,
            lastDepartureTime = lastDepartureTime,
            lastDepartedVisitId = lastDepartedVisitId
        )
    }
}
