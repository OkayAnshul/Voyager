package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Job
import com.cosmiclaboratory.voyager.domain.model.enums.StartReason
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.repository.TrackingRepository
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the onboarding persona pick — a single question (pick a [Job]). Each job maps
 * to a sensible starting tracking preset so one tap configures the app end-to-end, and
 * completing the pick **auto-starts tracking** so capture begins the moment onboarding
 * ends. The mapped preset stays fully tunable later in Settings.
 */
@HiltViewModel
class PersonaPickViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val trackingRepository: TrackingRepository,
    private val permissionMonitor: PermissionMonitor
) : ViewModel() {

    fun choosePersona(job: Job, onDone: () -> Unit) {
        viewModelScope.launch {
            // Apply the mapped preset first so every behaviour key is set, then
            // record the job so onboarding knows the persona pick is complete.
            settingsRepository.applyPreset(presetIdFor(job))
            settingsRepository.updateSetting("active_job", job.id)

            // Start capturing immediately — the whole point of finishing onboarding.
            // Only when foreground location is granted (PERMISSIONS ran just before this);
            // if the user declined, this no-ops and the dashboard start path still works.
            if (permissionMonitor.snapshot.value.hasAnyLocation) {
                trackingRepository.startTracking(StartReason.USER)
                    .onFailure { Log.w(TAG, "Onboarding auto-start tracking failed: ${it.message}") }
            }

            onDone()
        }
    }

    /** Maps a [Job] to its default starting preset (ids from [SettingsPresets]). */
    private fun presetIdFor(job: Job): String = when (job) {
        Job.MEMORY -> "DAILY_COMMUTER" // balanced home–work tracking
        Job.PROOF -> "PRECISION_MAX"   // tight detail + full 365-day retention for evidence
        Job.HABITS -> "CITY_EXPLORER"  // catches short stops → richer patterns
    }

    private companion object {
        const val TAG = "PersonaPickViewModel"
    }
}
