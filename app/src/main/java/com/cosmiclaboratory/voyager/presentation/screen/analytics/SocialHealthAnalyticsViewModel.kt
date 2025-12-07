package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.StatisticalInsight
import com.cosmiclaboratory.voyager.domain.usecase.PersonalizedInsightsGenerator
import com.cosmiclaboratory.voyager.domain.usecase.StatisticalAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Social & Health Analytics
 * Displays place category statistics, frequency analysis, and personalized health insights
 */
@HiltViewModel
class SocialHealthAnalyticsViewModel @Inject constructor(
    private val statisticalAnalyticsUseCase: StatisticalAnalyticsUseCase,
    private val personalizedInsightsGenerator: PersonalizedInsightsGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialHealthAnalyticsUiState())
    val uiState: StateFlow<SocialHealthAnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val allInsights = statisticalAnalyticsUseCase()
                val personalizedMessages = personalizedInsightsGenerator.generateMessages()

                // Extract relevant insights
                val placeStatistics = allInsights.filterIsInstance<StatisticalInsight.PlaceStatistics>()
                val frequencyAnalysis = allInsights.filterIsInstance<StatisticalInsight.FrequencyAnalysis>()

                // Group by category
                val categoryBreakdown = placeStatistics.groupBy { it.place.category }
                    .mapValues { (_, stats) ->
                        CategoryStats(
                            totalVisits = stats.sumOf { it.visitCount },
                            avgDuration = stats.map { it.meanDuration }.average(),
                            placeCount = stats.size
                        )
                    }

                // Social places
                val socialPlaces = placeStatistics.filter {
                    it.place.category in listOf(
                        PlaceCategory.SOCIAL,
                        PlaceCategory.RESTAURANT,
                        PlaceCategory.ENTERTAINMENT
                    )
                }

                // Health/activity places
                val healthPlaces = placeStatistics.filter {
                    it.place.category in listOf(
                        PlaceCategory.GYM,
                        PlaceCategory.OUTDOOR,
                        PlaceCategory.HEALTHCARE
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    placeStatistics = placeStatistics,
                    frequencyAnalysis = frequencyAnalysis,
                    categoryBreakdown = categoryBreakdown,
                    socialPlaces = socialPlaces,
                    healthPlaces = healthPlaces,
                    personalizedInsights = personalizedMessages
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

data class CategoryStats(
    val totalVisits: Int,
    val avgDuration: Double,
    val placeCount: Int
)

data class SocialHealthAnalyticsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val placeStatistics: List<StatisticalInsight.PlaceStatistics> = emptyList(),
    val frequencyAnalysis: List<StatisticalInsight.FrequencyAnalysis> = emptyList(),
    val categoryBreakdown: Map<PlaceCategory, CategoryStats> = emptyMap(),
    val socialPlaces: List<StatisticalInsight.PlaceStatistics> = emptyList(),
    val healthPlaces: List<StatisticalInsight.PlaceStatistics> = emptyList(),
    val personalizedInsights: List<com.cosmiclaboratory.voyager.domain.model.PersonalizedMessage> = emptyList()
)
