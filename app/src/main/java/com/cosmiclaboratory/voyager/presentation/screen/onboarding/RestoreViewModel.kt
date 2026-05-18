package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.ImportSummary
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.repository.ExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestoreUiState(
    val isWorking: Boolean = false,
    val summary: ImportSummary? = null,
    val error: String? = null
)

/**
 * Drives the first-launch [RestoreScreen]. Restores a `.voyager` backup
 * (a Voyager JSON export) into the fresh database.
 */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val exportRepository: ExportRepository,
    private val restorePreferences: RestorePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    fun restore(uri: Uri) {
        _uiState.update { it.copy(isWorking = true, summary = null, error = null) }
        viewModelScope.launch {
            val result = exportRepository.importData(uri, ExportFormat.VOYAGER_JSON)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    summary = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Marks the one-time prompt as seen — called whichever path the user takes. */
    fun markPromptSeen() {
        viewModelScope.launch { restorePreferences.markSeen() }
    }
}
