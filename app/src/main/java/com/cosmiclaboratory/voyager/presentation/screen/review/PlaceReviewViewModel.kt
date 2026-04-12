package com.cosmiclaboratory.voyager.presentation.screen.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.presentation.state.SharedUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceReviewUiState(
    val pendingPlaces: List<TimelinePlace> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class PlaceReviewViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val sharedUiState: SharedUiState
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceReviewUiState())
    val uiState: StateFlow<PlaceReviewUiState> = _uiState.asStateFlow()

    /** Threshold below which places are shown for review */
    private val confidenceThreshold = 0.7f

    init {
        viewModelScope.launch {
            placeRepository.observePlaces()
                .map { places ->
                    places.filter { it.confidence < confidenceThreshold || it.category == PlaceCategory.UNKNOWN }
                        .sortedBy { it.confidence }
                }
                .collect { pending ->
                    _uiState.update {
                        it.copy(pendingPlaces = pending, isLoading = false)
                    }
                    sharedUiState.updatePendingReviewCount(pending.size)
                }
        }
    }

    fun confirmPlace(placeId: Long) {
        viewModelScope.launch {
            placeRepository.confirmPlace(placeId)
                .onSuccess { _uiState.update { it.copy(message = "Place confirmed") } }
        }
    }

    fun renamePlace(placeId: Long, newName: String) {
        viewModelScope.launch {
            placeRepository.renamePlace(placeId, newName)
                .onSuccess { _uiState.update { it.copy(message = "Place renamed") } }
        }
    }

    fun setCategory(placeId: Long, category: PlaceCategory) {
        viewModelScope.launch {
            placeRepository.setPlaceCategory(placeId, category)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
