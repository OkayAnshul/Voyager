package com.cosmiclaboratory.voyager.presentation.screen.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.TrackingHealth
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import com.cosmiclaboratory.voyager.domain.model.enums.*
import com.cosmiclaboratory.voyager.domain.repository.TrackingRepository
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingControlUiState(
    val runtimeState: TrackingRuntimeState? = null,
    val health: TrackingHealth? = null,
    val permissionState: PermissionState = PermissionState.FULL,
    val isStarting: Boolean = false,
    val isStopping: Boolean = false,
    val error: String? = null
)

sealed interface TrackingControlIntent {
    data object StartTracking : TrackingControlIntent
    data object StopTracking : TrackingControlIntent
    data object PauseTracking : TrackingControlIntent
    data object ResumeTracking : TrackingControlIntent
    data object DismissError : TrackingControlIntent
}

@HiltViewModel
class TrackingControlViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val permissionMonitor: PermissionMonitor
) : ViewModel() {

    val uiState: StateFlow<TrackingControlUiState> = combine(
        trackingRepository.observeRuntimeState(),
        trackingRepository.observeHealth(),
        permissionMonitor.permissionState
    ) { state, health, permission ->
        TrackingControlUiState(
            runtimeState = state,
            health = health,
            permissionState = permission
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TrackingControlUiState()
    )

    fun onIntent(intent: TrackingControlIntent) {
        viewModelScope.launch {
            when (intent) {
                is TrackingControlIntent.StartTracking -> {
                    trackingRepository.startTracking(StartReason.USER)
                }
                is TrackingControlIntent.StopTracking -> {
                    trackingRepository.stopTracking(StopReason.USER)
                }
                is TrackingControlIntent.PauseTracking -> {
                    trackingRepository.pauseTracking(PauseReason.USER)
                }
                is TrackingControlIntent.ResumeTracking -> {
                    trackingRepository.resumeTracking(ResumeReason.USER)
                }
                is TrackingControlIntent.DismissError -> {
                    // Clear error state
                }
            }
        }
    }
}
