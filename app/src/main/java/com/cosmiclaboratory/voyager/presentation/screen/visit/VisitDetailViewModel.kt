package com.cosmiclaboratory.voyager.presentation.screen.visit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.ConfidenceBlock
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.CorrectionRepository
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEvidenceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisitDetailUiState(
    val visit: VisitEntity? = null,
    val place: PlaceEntity? = null,
    val evidence: VisitEvidenceEntity? = null,
    val confidenceBlock: ConfidenceBlock? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDeleted: Boolean = false
)

sealed interface VisitDetailIntent {
    data object ConfirmVisit : VisitDetailIntent
    data object DeleteVisit : VisitDetailIntent
    data class RenamePlace(val newName: String) : VisitDetailIntent
    data class AdjustTimes(val newArrival: Long, val newDeparture: Long) : VisitDetailIntent
}

@HiltViewModel
class VisitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val visitEvidenceDao: VisitEvidenceDao,
    private val correctionRepository: CorrectionRepository
) : ViewModel() {

    private val visitId: Long = savedStateHandle["visitId"] ?: -1L

    private val _uiState = MutableStateFlow(VisitDetailUiState())
    val uiState: StateFlow<VisitDetailUiState> = _uiState.asStateFlow()

    init {
        if (visitId > 0) {
            loadVisitData()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Invalid visit ID") }
        }
    }

    private fun loadVisitData() {
        viewModelScope.launch {
            try {
                val visit = visitDao.getById(visitId)
                if (visit == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Visit not found") }
                    return@launch
                }

                val place = placeDao.getById(visit.placeId)
                val evidence = visitEvidenceDao.getByVisitId(visitId)

                val confidenceBlock = evidence?.let {
                    ConfidenceBlock(
                        overall = visit.confidence,
                        arrival = it.arrivalConfidence,
                        departure = it.departureConfidence
                    )
                }

                _uiState.update {
                    it.copy(
                        visit = visit,
                        place = place,
                        evidence = evidence,
                        confidenceBlock = confidenceBlock,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onIntent(intent: VisitDetailIntent) {
        viewModelScope.launch {
            when (intent) {
                is VisitDetailIntent.ConfirmVisit -> {
                    correctionRepository.applyCorrection(
                        correctionType = CorrectionType.CONFIRM_VISIT,
                        entityType = "visit",
                        entityId = visitId,
                        beforeValue = null,
                        afterValue = null
                    )
                    // Mark visit as user-corrected
                    _uiState.value.visit?.let { visit ->
                        visitDao.update(visit.copy(isUserCorrected = true))
                        _uiState.update { it.copy(visit = visit.copy(isUserCorrected = true)) }
                    }
                }

                is VisitDetailIntent.DeleteVisit -> {
                    correctionRepository.applyCorrection(
                        correctionType = CorrectionType.DELETE_VISIT,
                        entityType = "visit",
                        entityId = visitId,
                        beforeValue = null,
                        afterValue = null
                    )
                    _uiState.value.visit?.let { visit ->
                        visitDao.delete(visit)
                        _uiState.update { it.copy(isDeleted = true) }
                    }
                }

                is VisitDetailIntent.RenamePlace -> {
                    val place = _uiState.value.place ?: return@launch
                    val oldName = place.userDisplayName ?: place.bestProviderName ?: ""
                    correctionRepository.applyCorrection(
                        correctionType = CorrectionType.RENAME,
                        entityType = "place",
                        entityId = place.placeId,
                        beforeValue = oldName,
                        afterValue = intent.newName
                    )
                    placeDao.updateDisplayName(place.placeId, intent.newName)
                    _uiState.update {
                        it.copy(place = place.copy(userDisplayName = intent.newName))
                    }
                }

                is VisitDetailIntent.AdjustTimes -> {
                    val visit = _uiState.value.visit ?: return@launch
                    val dwellMs = intent.newDeparture - intent.newArrival
                    correctionRepository.applyCorrection(
                        correctionType = CorrectionType.ADJUST_TIMES,
                        entityType = "visit",
                        entityId = visitId,
                        beforeValue = "${visit.arrivalAt},${visit.departureAt}",
                        afterValue = "${intent.newArrival},${intent.newDeparture}"
                    )
                    val updated = visit.copy(
                        arrivalAt = intent.newArrival,
                        departureAt = intent.newDeparture,
                        dwellMs = dwellMs,
                        isUserCorrected = true
                    )
                    visitDao.update(updated)
                    _uiState.update { it.copy(visit = updated) }
                }
            }
        }
    }
}
