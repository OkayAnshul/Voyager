package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.StatisticalInsight
import com.cosmiclaboratory.voyager.domain.usecase.StatisticalAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Movement & Time Pattern Analytics
 * Displays temporal trends, distance patterns, and time-based insights
 */
@HiltViewModel
class MovementAnalyticsViewModel @Inject constructor(
    private val statisticalAnalyticsUseCase: StatisticalAnalyticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovementAnalyticsUiState())
    val uiState: StateFlow<MovementAnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val allInsights = statisticalAnalyticsUseCase()

                // Filter insights relevant to movement and time patterns
                val temporalTrends = allInsights.filterIsInstance<StatisticalInsight.TemporalTrend>()
                val distributions = allInsights.filterIsInstance<StatisticalInsight.Distribution>()
                val correlations = allInsights.filterIsInstance<StatisticalInsight.Correlation>()
                    .filter { it.variable1.contains("time", ignoreCase = true) ||
                             it.variable2.contains("time", ignoreCase = true) }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    temporalTrends = temporalTrends,
                    distributions = distributions,
                    correlations = correlations
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load analytics: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadAnalytics()
    }
}

data class MovementAnalyticsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val temporalTrends: List<StatisticalInsight.TemporalTrend> = emptyList(),
    val distributions: List<StatisticalInsight.Distribution> = emptyList(),
    val correlations: List<StatisticalInsight.Correlation> = emptyList()
)
