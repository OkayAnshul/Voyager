package com.cosmiclaboratory.voyager.presentation.screen.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.ids.PlaceId
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

    init {
        viewModelScope.launch {
            placeRepository.observePlaces()
                .map { pendingReviewPlaces(it) }
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
            placeRepository.confirmPlace(PlaceId(placeId))
                .onSuccess { _uiState.update { it.copy(message = "Place confirmed") } }
        }
    }

    fun renamePlace(placeId: Long, newName: String) {
        viewModelScope.launch {
            placeRepository.renamePlace(PlaceId(placeId), newName)
                .onSuccess { _uiState.update { it.copy(message = "Place renamed") } }
        }
    }

    fun setCategory(placeId: Long, category: PlaceCategory) {
        viewModelScope.launch {
            placeRepository.setPlaceCategory(PlaceId(placeId), category)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        /** Confidence below which a place is surfaced for review. */
        const val CONFIDENCE_THRESHOLD = 0.7f

        /**
         * The review queue: places that are either low-confidence or still uncategorised,
         * lowest-confidence first (most-uncertain at the top). Exposed for testing.
         */
        internal fun pendingReviewPlaces(places: List<TimelinePlace>): List<TimelinePlace> =
            places
                // A confirmed place is done — never re-surface it, even if uncategorised
                // (confirming raises confidence but can't invent a category).
                .filter { !it.isConfirmed && (it.confidence < CONFIDENCE_THRESHOLD || it.category == PlaceCategory.UNKNOWN) }
                .sortedBy { it.confidence }
    }
}
