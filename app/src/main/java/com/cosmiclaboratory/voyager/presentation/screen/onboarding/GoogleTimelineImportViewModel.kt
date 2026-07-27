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
 * Powers the "Bring your history" import card on the intro screen — the migration
 * wedge for users leaving Google Maps Timeline. Wraps [GoogleTimelineImporter].
 */
@HiltViewModel
class GoogleTimelineImportViewModel @Inject constructor(
    private val googleTimelineImporter: GoogleTimelineImporter
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
}
