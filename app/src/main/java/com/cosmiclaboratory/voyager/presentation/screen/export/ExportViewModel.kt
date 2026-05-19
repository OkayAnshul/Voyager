package com.cosmiclaboratory.voyager.presentation.screen.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.data.imports.GoogleTimelineImporter
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.ImportSummary
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.repository.ExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExportUiState(
    val format: ExportFormat = ExportFormat.VOYAGER_JSON,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val importSummary: ImportSummary? = null,
    val error: String? = null
) {
    /** True when the selected range is a single day. */
    val isSingleDay: Boolean get() = startDate == endDate
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportRepository: ExportRepository,
    private val googleTimelineImporter: GoogleTimelineImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun setFormat(format: ExportFormat) {
        _uiState.update { it.copy(format = format) }
    }

    /** Sets the export range. A single-day export is just [start] == [end]. */
    fun setDateRange(start: LocalDate, end: LocalDate) {
        val (lo, hi) = if (start <= end) start to end else end to start
        _uiState.update { it.copy(startDate = lo, endDate = hi) }
    }

    fun export() {
        val state = _uiState.value
        _uiState.update { it.copy(isWorking = true, resultUri = null, error = null) }
        viewModelScope.launch {
            val range = DateRange(state.startDate.toString(), state.endDate.toString())
            val result = exportRepository.exportRange(range, state.format)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    resultUri = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun import(uri: Uri) {
        _uiState.update { it.copy(isWorking = true, importSummary = null, error = null) }
        viewModelScope.launch {
            val result = exportRepository.importData(uri, ExportFormat.VOYAGER_JSON)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    importSummary = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    /** Imports a Google Timeline / Location History export (the migration wedge). */
    fun importGoogleTimeline(uri: Uri) {
        _uiState.update { it.copy(isWorking = true, importSummary = null, error = null) }
        viewModelScope.launch {
            val result = googleTimelineImporter.import(uri)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    importSummary = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun consumeResult() {
        _uiState.update { it.copy(resultUri = null, importSummary = null, error = null) }
    }
}
