package com.cosmiclaboratory.voyager.presentation.screen.segment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.repository.CorrectionRepository
import com.cosmiclaboratory.voyager.domain.repository.EvidenceRepository
import com.cosmiclaboratory.voyager.domain.repository.TimelineRepository
import com.cosmiclaboratory.voyager.domain.usecase.OverrideSegmentTypeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SegmentDetailUiState(
    val segment: TimelineSegment? = null,
    val evidence: EvidenceBlock? = null,
    val explanation: InferenceExplanation? = null,
    val isLoading: Boolean = true
)

sealed interface SegmentDetailIntent {
    data class ChangeType(val newType: String) : SegmentDetailIntent
}

@HiltViewModel
class SegmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelineRepository: TimelineRepository,
    private val evidenceRepository: EvidenceRepository,
    private val correctionRepository: CorrectionRepository,
    private val overrideSegmentType: OverrideSegmentTypeUseCase
) : ViewModel() {

    private val segmentId: Long = savedStateHandle["segmentId"] ?: -1L

    private val _uiState = MutableStateFlow(SegmentDetailUiState())
    val uiState: StateFlow<SegmentDetailUiState> = _uiState.asStateFlow()

    init {
        if (segmentId > 0) {
            viewModelScope.launch {
                val segment = timelineRepository.getSegmentDetails(segmentId)
                val evidence = evidenceRepository.getSegmentEvidence(segmentId)
                val explanation = evidenceRepository.getInferenceExplanation(segmentId)
                _uiState.update {
                    SegmentDetailUiState(
                        segment = segment,
                        evidence = evidence,
                        explanation = explanation,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onIntent(intent: SegmentDetailIntent) {
        viewModelScope.launch {
            when (intent) {
                is SegmentDetailIntent.ChangeType -> {
                    val beforeType = _uiState.value.segment?.type?.name
                    // Authoritative override → the label actually changes; then reload the
                    // open sheet so it reflects the new type immediately.
                    val newType = try { SegmentType.valueOf(intent.newType) } catch (_: Exception) { null }
                    if (newType != null) {
                        overrideSegmentType.setOverride(segmentId, newType)
                        val refreshed = timelineRepository.getSegmentDetails(segmentId)
                        _uiState.update { it.copy(segment = refreshed) }
                    }
                    correctionRepository.applyCorrection(
                        correctionType = CorrectionType.CHANGE_TRANSPORT_MODE,
                        entityType = "segment",
                        entityId = segmentId,
                        beforeValue = beforeType,
                        afterValue = intent.newType
                    )
                }
            }
        }
    }
}
