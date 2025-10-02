package com.cosmiclaboratory.voyager.presentation.screen.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class InsightsUiState(
    val weeklyAnalytics: TimeAnalytics? = null,
    val movementPatterns: List<MovementPattern> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val analyticsUseCases: AnalyticsUseCases
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()
    
    init {
        loadInsights()
    }
    
    private fun loadInsights() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Get analytics for the past week
                val endTime = LocalDateTime.now()
                val startTime = endTime.minusWeeks(1)
                
                val weeklyAnalytics = analyticsUseCases.generateTimeAnalytics(startTime, endTime)
                val movementPatterns = analyticsUseCases.detectMovementPatterns()
                
                _uiState.value = _uiState.value.copy(
                    weeklyAnalytics = weeklyAnalytics,
                    movementPatterns = movementPatterns,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load insights: ${e.message}"
                )
            }
        }
    }
    
    fun refreshInsights() {
        loadInsights()
    }
}