package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.data.imports.GoogleTimelineImporter
import com.cosmiclaboratory.voyager.domain.model.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoogleTimelineImportUiState(
    val isWorking: Boolean = false,
    val summary: ImportSummary? = null,
    val error: String? = null
)

/**
 * Drives the first-launch [GoogleTimelineImportScreen] — the migration wedge for
 * users leaving Google Maps Timeline. Wraps [GoogleTimelineImporter].
 */
@HiltViewModel
class GoogleTimelineImportViewModel @Inject constructor(
    private val googleTimelineImporter: GoogleTimelineImporter,
    private val preferences: GoogleTimelineImportPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleTimelineImportUiState())
    val uiState: StateFlow<GoogleTimelineImportUiState> = _uiState.asStateFlow()

    fun import(uri: Uri) {
        _uiState.update { it.copy(isWorking = true, summary = null, error = null) }
        viewModelScope.launch {
            val result = googleTimelineImporter.import(uri)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    summary = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    /** Marks the one-time prompt as seen — called whichever path the user takes. */
    fun markPromptSeen() {
        viewModelScope.launch { preferences.markSeen() }
    }
}
