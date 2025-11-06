package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.TrackingHealth
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import com.cosmiclaboratory.voyager.domain.model.enums.PauseReason
import com.cosmiclaboratory.voyager.domain.model.enums.ResumeReason
import com.cosmiclaboratory.voyager.domain.model.enums.StartReason
import com.cosmiclaboratory.voyager.domain.model.enums.StopReason
import com.cosmiclaboratory.voyager.domain.repository.TrackingRepository
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
import com.cosmiclaboratory.voyager.platform.coordinator.TrackingRuntimeCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val coordinator: TrackingRuntimeCoordinator,
    private val permissionMonitor: PermissionMonitor
) : TrackingRepository {

    override suspend fun startTracking(reason: StartReason): Result<Long> =
        coordinator.start(reason)

    override suspend fun stopTracking(reason: StopReason): Result<Unit> =
        coordinator.stop(reason)

    override suspend fun pauseTracking(reason: PauseReason): Result<Unit> =
        coordinator.pause(reason)

    override suspend fun resumeTracking(reason: ResumeReason): Result<Unit> =
        coordinator.resume(reason)

    override fun observeRuntimeState(): StateFlow<TrackingRuntimeState> =
        coordinator.runtimeState

    override fun observeHealth(): Flow<TrackingHealth> =
        coordinator.runtimeState.map { state ->
            TrackingHealth(
                isServiceRunning = state.activeSessionId != null,
                lastSampleAt = state.lastAcceptedAt,
                permissionState = permissionMonitor.checkCurrentState().name,
                batteryPct = null,
                isCharging = false,
                workerHeartbeats = emptyMap()
            )
        }
}
