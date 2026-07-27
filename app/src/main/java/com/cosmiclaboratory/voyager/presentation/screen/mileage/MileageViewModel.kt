package com.cosmiclaboratory.voyager.presentation.screen.mileage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.model.MileageRateConfig
import com.cosmiclaboratory.voyager.domain.model.toMileageRateConfig
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.usecase.BuildMileageLogUseCase
import com.cosmiclaboratory.voyager.domain.usecase.MileageDeduction
import com.cosmiclaboratory.voyager.platform.export.MileageCsvExporter
import com.cosmiclaboratory.voyager.platform.export.MileagePdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val EMPTY_LOG = MileageLog(entries = emptyList(), rangeLabel = "This Month")
private val EMPTY_ESTIMATE = MileageDeduction.estimate(EMPTY_LOG, MileageRateConfig())

data class MileageUiState(
    val range: DateRangePeriod = DateRangePeriod.ThisMonth,
    val log: MileageLog = EMPTY_LOG,
    val config: MileageRateConfig = MileageRateConfig(),
    val estimate: MileageDeduction.Estimate = EMPTY_ESTIMATE,
    /** Drives currently multi-selected for batch classification; non-empty ⇒ selection mode. */
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportUri: Uri? = null,
    /** MIME type of [exportUri] so the share sheet advertises the right format. */
    val exportMime: String = "application/pdf",
    val error: String? = null
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

/** Transient, non-persisted UI signals layered over the reactive log. */
private data class MileageTransient(
    val isExporting: Boolean = false,
    val exportUri: Uri? = null,
    val exportMime: String = "application/pdf",
    val error: String? = null
)

sealed interface MileageAction {
    data class SelectRange(val range: DateRangePeriod) : MileageAction
    data class Classify(val segmentId: Long, val purpose: MileagePurpose) : MileageAction
    /** Classifies every drive in the current selection, then leaves selection mode. */
    data class ClassifyBatch(val purpose: MileagePurpose) : MileageAction
    data class ToggleSelection(val segmentId: Long) : MileageAction
    data object ClearSelection : MileageAction
    data object ExportPdf : MileageAction
    data object ExportCsv : MileageAction
    data object ConsumeExportResult : MileageAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MileageViewModel @Inject constructor(
    private val buildMileageLog: BuildMileageLogUseCase,
    private val pdfExporter: MileagePdfExporter,
    private val csvExporter: MileageCsvExporter,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val range = MutableStateFlow<DateRangePeriod>(DateRangePeriod.ThisMonth)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val transient = MutableStateFlow(MileageTransient())

    private val config = settingsRepository.observeSettings().map { it.toMileageRateConfig() }

    val uiState: StateFlow<MileageUiState> = combine(
        range,
        range.flatMapLatest { buildMileageLog.observeLog(it) },
        config,
        selection,
        transient
    ) { selectedRange, log, config, selectedIds, t ->
        MileageUiState(
            range = selectedRange,
            log = log,
            config = config,
            estimate = MileageDeduction.estimate(log, config),
            // Drop any selected ids that fell out of the current log (range change, etc.).
            selectedIds = selectedIds.intersect(log.entries.map { it.segmentId }.toSet()),
            isLoading = false,
            isExporting = t.isExporting,
            exportUri = t.exportUri,
            exportMime = t.exportMime,
            error = t.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MileageUiState())

    fun onAction(action: MileageAction) {
        when (action) {
            is MileageAction.SelectRange -> {
                range.value = action.range
                selection.value = emptySet()
            }
            is MileageAction.Classify -> viewModelScope.launch {
                buildMileageLog.classify(action.segmentId, action.purpose)
            }
            is MileageAction.ClassifyBatch -> {
                val ids = selection.value.toList()
                selection.value = emptySet()
                if (ids.isNotEmpty()) viewModelScope.launch {
                    buildMileageLog.classifyAll(ids, action.purpose)
                }
            }
            is MileageAction.ToggleSelection -> selection.update { current ->
                if (action.segmentId in current) current - action.segmentId
                else current + action.segmentId
            }
            is MileageAction.ClearSelection -> selection.value = emptySet()
            is MileageAction.ExportPdf -> export(isCsv = false)
            is MileageAction.ExportCsv -> export(isCsv = true)
            is MileageAction.ConsumeExportResult ->
                transient.update { it.copy(exportUri = null, error = null) }
        }
    }

    private fun export(isCsv: Boolean) {
        val state = uiState.value
        val log = state.log
        val config = state.config
        val mime = if (isCsv) "text/csv" else "application/pdf"
        transient.update { it.copy(isExporting = true, exportUri = null, exportMime = mime, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (isCsv) csvExporter.export(log, config) else pdfExporter.export(log, config)
            }
            transient.update {
                it.copy(
                    isExporting = false,
                    exportUri = result.getOrNull(),
                    exportMime = mime,
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }
}
