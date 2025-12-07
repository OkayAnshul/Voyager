package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.WeeklyComparison
import com.cosmiclaboratory.voyager.domain.usecase.CompareMonthlyAnalyticsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.CompareWeeklyAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for time comparison analytics screen (weekly and monthly)
 */
@HiltViewModel
class WeeklyComparisonViewModel @Inject constructor(
    private val compareWeeklyAnalyticsUseCase: CompareWeeklyAnalyticsUseCase,
    private val compareMonthlyAnalyticsUseCase: CompareMonthlyAnalyticsUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ComparisonTab.WEEKLY)
    val selectedTab: StateFlow<ComparisonTab> = _selectedTab.asStateFlow()

    private val _uiState = MutableStateFlow<WeeklyComparisonUiState>(WeeklyComparisonUiState.Loading)
    val uiState: StateFlow<WeeklyComparisonUiState> = _uiState.asStateFlow()

    init {
        loadComparison()
    }

    /**
     * Switch between weekly and monthly tabs
     */
    fun selectTab(tab: ComparisonTab) {
        if (_selectedTab.value != tab) {
            _selectedTab.value = tab
            loadComparison()
        }
    }

    /**
     * Load comparison data based on selected tab
     */
    fun loadComparison() {
        viewModelScope.launch {
            _uiState.value = WeeklyComparisonUiState.Loading

            try {
                val comparison = when (_selectedTab.value) {
                    ComparisonTab.WEEKLY -> compareWeeklyAnalyticsUseCase()
                    ComparisonTab.MONTHLY -> compareMonthlyAnalyticsUseCase()
                }
                _uiState.value = WeeklyComparisonUiState.Success(comparison)
            } catch (e: Exception) {
                _uiState.value = WeeklyComparisonUiState.Error(
                    message = e.message ?: "Failed to load comparison"
                )
            }
        }
    }

    /**
     * Refresh the comparison data
     */
    fun refresh() {
        loadComparison()
    }
}

/**
 * Tab selection for comparison view
 */
enum class ComparisonTab {
    WEEKLY,
    MONTHLY
}

/**
 * UI state for weekly comparison screen
 */
sealed class WeeklyComparisonUiState {
    /**
     * Loading state
     */
    data object Loading : WeeklyComparisonUiState()

    /**
     * Success state with comparison data
     */
    data class Success(
        val comparison: WeeklyComparison
    ) : WeeklyComparisonUiState()

    /**
     * Error state
     */
    data class Error(
        val message: String
    ) : WeeklyComparisonUiState()
}
