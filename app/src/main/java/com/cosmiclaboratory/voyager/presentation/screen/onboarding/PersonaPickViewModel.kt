package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Job
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the onboarding persona pick. Choosing a persona applies a full tracking
 * preset and records the chosen [Job] — the app is configured end-to-end from
 * one screen.
 */
@HiltViewModel
class PersonaPickViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun choosePersona(job: Job, presetId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            // Apply the preset first so every behaviour key is set, then record
            // the job so onboarding knows the persona pick is complete.
            settingsRepository.applyPreset(presetId)
            settingsRepository.updateSetting("active_job", job.id)
            onDone()
        }
    }
}
