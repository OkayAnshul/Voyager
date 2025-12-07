package com.cosmiclaboratory.voyager.presentation.screen.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Anomaly
import com.cosmiclaboratory.voyager.domain.model.PlacePattern
import com.cosmiclaboratory.voyager.domain.usecase.AnalyzePlacePatternsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.DetectAnomaliesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for place patterns and anomalies screen
 */
@HiltViewModel
class PlacePatternsViewModel @Inject constructor(
    private val analyzePlacePatternsUseCase: AnalyzePlacePatternsUseCase,
    private val detectAnomaliesUseCase: DetectAnomaliesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlacePatternsUiState>(PlacePatternsUiState.Loading)
    val uiState: StateFlow<PlacePatternsUiState> = _uiState.asStateFlow()

    init {
        loadPatternsAndAnomalies()
    }

    /**
     * Load patterns and anomalies
     */
    fun loadPatternsAndAnomalies() {
        viewModelScope.launch {
            _uiState.value = PlacePatternsUiState.Loading

            try {
                // Load both patterns and anomalies in parallel
                val patterns = analyzePlacePatternsUseCase()
                val anomalies = detectAnomaliesUseCase()

                _uiState.value = PlacePatternsUiState.Success(
                    patterns = patterns,
                    anomalies = anomalies
                )
            } catch (e: Exception) {
                _uiState.value = PlacePatternsUiState.Error(
                    message = e.message ?: "Failed to load patterns and anomalies"
                )
            }
        }
    }

    /**
     * Refresh data
     */
    fun refresh() {
        loadPatternsAndAnomalies()
    }
}

/**
 * UI state for patterns and anomalies screen
 */
sealed class PlacePatternsUiState {
    /**
     * Loading state
     */
    data object Loading : PlacePatternsUiState()

    /**
     * Success state with patterns and anomalies
     */
    data class Success(
        val patterns: List<PlacePattern>,
        val anomalies: List<Anomaly>
    ) : PlacePatternsUiState()

    /**
     * Error state
     */
    data class Error(
        val message: String
    ) : PlacePatternsUiState()
}
