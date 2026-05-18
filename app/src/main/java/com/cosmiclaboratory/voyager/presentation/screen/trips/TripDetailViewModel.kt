package com.cosmiclaboratory.voyager.presentation.screen.trips

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.TripDetail
import com.cosmiclaboratory.voyager.domain.usecase.BuildTripDetailUseCase
import com.cosmiclaboratory.voyager.platform.export.TripBookPdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TripDetailUiState(
    val detail: TripDetail? = null,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportUri: Uri? = null,
    val error: String? = null
)

sealed interface TripDetailAction {
    data object ExportBook : TripDetailAction
    data object ConsumeExportResult : TripDetailAction
}

/** Backs the trip detail screen — the day-by-day journal and the PDF trip-book export. */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val buildTripDetail: BuildTripDetailUseCase,
    private val pdfExporter: TripBookPdfExporter
) : ViewModel() {

    private val tripId: Long = savedStateHandle["tripId"] ?: -1L

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val detail = runCatching { buildTripDetail.build(tripId) }
            _uiState.update {
                it.copy(
                    detail = detail.getOrNull(),
                    isLoading = false,
                    error = detail.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun onAction(action: TripDetailAction) {
        when (action) {
            TripDetailAction.ExportBook -> exportBook()
            TripDetailAction.ConsumeExportResult ->
                _uiState.update { it.copy(exportUri = null, error = null) }
        }
    }

    private fun exportBook() {
        val detail = _uiState.value.detail ?: return
        _uiState.update { it.copy(isExporting = true, exportUri = null, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pdfExporter.export(detail) }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportUri = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }
}
