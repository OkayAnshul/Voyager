package com.cosmiclaboratory.voyager.presentation.screen.mileage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.usecase.BuildMileageLogUseCase
import com.cosmiclaboratory.voyager.platform.export.MileagePdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MileageUiState(
    val range: DateRangePeriod = DateRangePeriod.ThisMonth,
    val log: MileageLog = MileageLog(entries = emptyList(), rangeLabel = "This Month"),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportUri: Uri? = null,
    val error: String? = null
)

/** Transient, non-persisted UI signals layered over the reactive log. */
private data class MileageTransient(
    val isExporting: Boolean = false,
    val exportUri: Uri? = null,
    val error: String? = null
)

sealed interface MileageAction {
    data class SelectRange(val range: DateRangePeriod) : MileageAction
    data class Classify(val segmentId: Long, val purpose: MileagePurpose) : MileageAction
    data object ExportPdf : MileageAction
    data object ConsumeExportResult : MileageAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MileageViewModel @Inject constructor(
    private val buildMileageLog: BuildMileageLogUseCase,
    private val pdfExporter: MileagePdfExporter
) : ViewModel() {

    private val range = MutableStateFlow<DateRangePeriod>(DateRangePeriod.ThisMonth)
    private val transient = MutableStateFlow(MileageTransient())

    val uiState: StateFlow<MileageUiState> = combine(
        range,
        range.flatMapLatest { buildMileageLog.observeLog(it) },
        transient
    ) { selectedRange, log, t ->
        MileageUiState(
            range = selectedRange,
            log = log,
            isLoading = false,
            isExporting = t.isExporting,
            exportUri = t.exportUri,
            error = t.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MileageUiState())

    fun onAction(action: MileageAction) {
        when (action) {
            is MileageAction.SelectRange -> range.value = action.range
            is MileageAction.Classify -> viewModelScope.launch {
                buildMileageLog.classify(action.segmentId, action.purpose)
            }
            is MileageAction.ExportPdf -> exportPdf()
            is MileageAction.ConsumeExportResult ->
                transient.update { it.copy(exportUri = null, error = null) }
        }
    }

    private fun exportPdf() {
        val log = uiState.value.log
        transient.update { it.copy(isExporting = true, exportUri = null, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pdfExporter.export(log) }
            transient.update {
                it.copy(
                    isExporting = false,
                    exportUri = result.getOrNull(),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }
}
