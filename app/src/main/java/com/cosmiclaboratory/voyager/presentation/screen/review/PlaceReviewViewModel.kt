package com.cosmiclaboratory.voyager.presentation.screen.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.PlaceReview
import com.cosmiclaboratory.voyager.domain.model.PlaceNameSuggestion
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.domain.usecase.PlaceReviewUseCases
import com.cosmiclaboratory.voyager.domain.usecase.GatherPlaceNameSuggestionsUseCase
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaceReviewViewModel @Inject constructor(
    private val placeReviewUseCases: PlaceReviewUseCases,
    private val gatherSuggestionsUseCase: GatherPlaceNameSuggestionsUseCase,
    private val placeRepository: PlaceRepository
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(PlaceReviewUiState())
    val uiState: StateFlow<PlaceReviewUiState> = _uiState.asStateFlow()

    // ISSUE #2: Suggestions for place names
    private val _suggestions = MutableStateFlow<Map<Long, List<PlaceNameSuggestion>>>(emptyMap())
    val suggestions: StateFlow<Map<Long, List<PlaceNameSuggestion>>> = _suggestions.asStateFlow()

    // Pending reviews grouped by priority
    val pendingReviews: StateFlow<Map<ReviewPriority, List<PlaceReview>>> =
        placeReviewUseCases.getPendingReviews()
            .map { reviews ->
                reviews.groupBy { it.priority }
                    .toSortedMap(compareByDescending { it.ordinal })
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    // Pending count
    val pendingCount: StateFlow<Int> =
        placeReviewUseCases.getPendingReviewCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    init {
        // Load reviews on init
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Approve a place review as-is
     */
    fun approvePlace(reviewId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            placeReviewUseCases.approvePlace(reviewId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Place approved!"
                        )
                    }
                    clearMessageAfterDelay()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Failed to approve: ${error.message}"
                        )
                    }
                    clearMessageAfterDelay()
                }
        }
    }

    /**
     * Edit and approve a place
     * ISSUE #3: Added customCategoryName parameter for CUSTOM category support
     */
    fun editAndApprovePlace(
        reviewId: Long,
        newName: String?,
        newCategory: PlaceCategory?,
        customCategoryName: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            placeReviewUseCases.editAndApprovePlace(reviewId, newName, newCategory, customCategoryName)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Place updated and approved!",
                            editDialogReviewId = null
                        )
                    }
                    clearMessageAfterDelay()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Failed to update: ${error.message}"
                        )
                    }
                    clearMessageAfterDelay()
                }
        }
    }

    /**
     * Reject a place review
     */
    fun rejectPlace(reviewId: Long, reason: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            placeReviewUseCases.rejectPlace(reviewId, reason)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Place rejected"
                        )
                    }
                    clearMessageAfterDelay()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Failed to reject: ${error.message}"
                        )
                    }
                    clearMessageAfterDelay()
                }
        }
    }

    /**
     * Batch approve high confidence reviews
     */
    fun batchApproveHighConfidence(threshold: Float = 0.8f) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            placeReviewUseCases.batchApproveHighConfidence(threshold)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Approved $count places"
                        )
                    }
                    clearMessageAfterDelay()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Batch approve failed: ${error.message}"
                        )
                    }
                    clearMessageAfterDelay()
                }
        }
    }

    /**
     * Show edit dialog for a review
     * ISSUE #2: Fetch suggestions asynchronously when dialog opens
     */
    fun showEditDialog(reviewId: Long) {
        _uiState.update { it.copy(editDialogReviewId = reviewId) }

        // Fetch suggestions in background
        viewModelScope.launch {
            try {
                // Find the review to get the place
                val review = placeReviewUseCases.getPendingReviews().first()
                    .find { it.id == reviewId }

                review?.let {
                    val place = placeRepository.getPlaceById(it.placeId)
                    place?.let { placeData ->
                        val nameSuggestions = gatherSuggestionsUseCase(placeData)
                        _suggestions.update { currentMap ->
                            currentMap + (reviewId to nameSuggestions)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fail - suggestions are optional
            }
        }
    }

    /**
     * Hide edit dialog
     */
    fun hideEditDialog() {
        _uiState.update { it.copy(editDialogReviewId = null) }
    }

    /**
     * Clear success/error messages
     */
    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    private fun clearMessageAfterDelay() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            clearMessages()
        }
    }
}

/**
 * UI State for Place Review Screen
 */
data class PlaceReviewUiState(
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val editDialogReviewId: Long? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
