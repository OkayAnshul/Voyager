package com.cosmiclaboratory.voyager.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.repository.ExportRepository
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val exportMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val database: VoyagerDatabase,
    private val exportRepository: ExportRepository
) : ViewModel() {

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = settingsRepository.observeSettings()
        .map { settings -> SettingsUiState(settings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateSetting(key: String, value: Any) {
        viewModelScope.launch {
            settingsRepository.updateSetting(key, value)
        }
    }

    fun applyPreset(presetId: String) {
        viewModelScope.launch {
            settingsRepository.applyPreset(presetId)
        }
    }

    fun resetToDefaults() {
        applyPreset("DAILY_COMMUTER")
    }

    fun clearError() {
        // No-op: errors are transient in current implementation
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                database.clearAllTables()
                _exportMessage.value = "All data deleted successfully"
            } catch (e: Exception) {
                _exportMessage.value = "Failed to delete data: ${e.message}"
            }
        }
    }

    fun exportData(format: ExportFormat) {
        viewModelScope.launch {
            try {
                _exportMessage.value = "Exporting..."
                val today = java.time.LocalDate.now().toString()
                val thirtyDaysAgo = java.time.LocalDate.now().minusDays(30).toString()
                val result = exportRepository.exportRange(
                    DateRange(thirtyDaysAgo, today),
                    format
                )
                result.onSuccess { uri ->
                    _exportMessage.value = "Export saved: ${uri.lastPathSegment}"
                }.onFailure { e ->
                    _exportMessage.value = "Export failed: ${e.message}"
                }
            } catch (e: Exception) {
                _exportMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    fun exportSettings(onResult: (String) -> Unit) {
        viewModelScope.launch {
            settingsRepository.exportSettings()
                .onSuccess { onResult(it) }
        }
    }

    fun importSettings(json: String) {
        viewModelScope.launch {
            settingsRepository.importSettings(json)
                .onSuccess { _exportMessage.value = "Settings imported successfully" }
                .onFailure { e -> _exportMessage.value = "Import failed: ${e.message}" }
        }
    }
}
