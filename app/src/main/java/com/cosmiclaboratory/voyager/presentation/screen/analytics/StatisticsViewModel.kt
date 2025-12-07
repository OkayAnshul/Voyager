package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for StatisticsScreen
 *
 * Consolidates data from multiple analytics use cases:
 * - Weekly comparisons
 * - Place patterns
 * - Movement statistics
 * - Social health metrics
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val analyticsUseCases: com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases,
    private val placeRepository: com.cosmiclaboratory.voyager.domain.repository.PlaceRepository,
    private val visitRepository: com.cosmiclaboratory.voyager.domain.repository.VisitRepository,
    private val locationRepository: com.cosmiclaboratory.voyager.domain.repository.LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadAllStatistics()
    }

    private fun loadAllStatistics() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Load weekly comparison data
                val weeklyComparison = loadWeeklyComparison()

                // Load place patterns
                val placePatterns = loadPlacePatterns()

                // Load movement stats
                val movementStats = loadMovementStats()

                // Load social health stats
                val socialStats = loadSocialHealthStats()

                _uiState.value = StatisticsUiState(
                    weeklyComparison = weeklyComparison,
                    placePatterns = placePatterns,
                    movementStats = movementStats,
                    socialStats = socialStats,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load statistics: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadWeeklyComparison(): WeeklyComparisonData {
        val now = java.time.LocalDateTime.now()
        val thisWeekStart = now.minusDays(now.dayOfWeek.value.toLong() - 1).withHour(0).withMinute(0)
        val lastWeekStart = thisWeekStart.minusWeeks(1)

        val thisWeekAnalytics = analyticsUseCases.generateTimeAnalytics(thisWeekStart, now)
        val lastWeekAnalytics = analyticsUseCases.generateTimeAnalytics(
            lastWeekStart,
            lastWeekStart.plusWeeks(1)
        )

        val thisWeekVisits = visitRepository.getVisitsBetween(thisWeekStart, now).first()
        val lastWeekVisits = visitRepository.getVisitsBetween(
            lastWeekStart,
            lastWeekStart.plusWeeks(1)
        ).first()

        val placesThisWeek = thisWeekVisits.map { it.placeId }.distinct().size
        val placesLastWeek = lastWeekVisits.map { it.placeId }.distinct().size

        val distanceThisWeek = calculateDistance(thisWeekStart, now)
        val distanceLastWeek = calculateDistance(lastWeekStart, lastWeekStart.plusWeeks(1))

        val timeAwayThisWeek = (thisWeekAnalytics.totalTimeTracked / (1000 * 60 * 60)).toInt()
        val timeAwayLastWeek = (lastWeekAnalytics.totalTimeTracked / (1000 * 60 * 60)).toInt()

        return WeeklyComparisonData(
            dateRange = "${thisWeekStart.toLocalDate()} - ${now.toLocalDate()}",
            placesThisWeek = placesThisWeek,
            placesLastWeek = placesLastWeek,
            placesChange = calculatePercentChange(placesLastWeek, placesThisWeek),
            distanceThisWeek = distanceThisWeek,
            distanceLastWeek = distanceLastWeek,
            distanceChange = calculatePercentChange(distanceLastWeek.toInt(), distanceThisWeek.toInt()),
            timeAwayThisWeek = timeAwayThisWeek,
            timeAwayLastWeek = timeAwayLastWeek,
            timeAwayChange = calculatePercentChange(timeAwayLastWeek, timeAwayThisWeek)
        )
    }

    private suspend fun loadPlacePatterns(): List<PlacePattern> {
        val places = placeRepository.getAllPlaces().first()
        val thirtyDaysAgo = java.time.LocalDateTime.now().minusDays(30)
        val visits = visitRepository.getVisitsBetween(thirtyDaysAgo, java.time.LocalDateTime.now())
            .first()

        return places.mapNotNull { place ->
            val placeVisits = visits.filter { it.placeId == place.id }
            if (placeVisits.isEmpty()) return@mapNotNull null

            val avgDuration = placeVisits.map { it.duration }.average().toLong()
            val typicalDays = placeVisits
                .map { it.entryTime.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
                .groupingBy { it }
                .eachCount()
                .filter { it.value >= 2 }
                .keys
                .toList()

            PlacePattern(
                placeName = place.osmSuggestedName ?: place.name,
                category = place.address ?: place.category.name,
                visitCount = placeVisits.size,
                typicalDays = typicalDays,
                avgDurationMinutes = (avgDuration / (1000 * 60)).toInt()
            )
        }.sortedByDescending { it.visitCount }
    }

    private suspend fun loadMovementStats(): MovementStats {
        val thirtyDaysAgo = java.time.LocalDateTime.now().minusDays(30)
        val now = java.time.LocalDateTime.now()

        val totalDistance = calculateDistance(thirtyDaysAgo, now)
        val locations = locationRepository.getLocationsSince(thirtyDaysAgo)

        val avgSpeed = if (locations.isNotEmpty()) {
            locations.mapNotNull { it.speed }.filter { it > 0 }.average()
        } else 0.0

        val visitsByDay = visitRepository.getVisitsBetween(thirtyDaysAgo, now)
            .first()
            .groupBy { it.entryTime.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }

        val mostActiveDay = visitsByDay.maxByOrNull { it.value.size }?.key ?: "N/A"

        return MovementStats(
            totalDistanceKm = totalDistance / 1000.0,
            avgSpeedKmh = avgSpeed * 3.6,
            mostActiveDay = mostActiveDay
        )
    }

    private suspend fun loadSocialHealthStats(): SocialHealthStats {
        val places = placeRepository.getAllPlaces().first()
        val uniquePlaces = places.size

        val categoryBreakdown = places
            .groupBy { it.address ?: it.category.name }
            .mapValues { it.value.size }

        val varietyScore = (uniquePlaces.coerceAtMost(50) * 2).coerceAtMost(100)

        return SocialHealthStats(
            uniquePlaces = uniquePlaces,
            varietyScore = varietyScore,
            categoryBreakdown = categoryBreakdown
        )
    }

    private suspend fun calculateDistance(start: java.time.LocalDateTime, end: java.time.LocalDateTime): Double {
        val locations = locationRepository.getLocationsSince(start).filter { it.timestamp.isBefore(end) }

        var totalDistance = 0.0
        for (i in 0 until locations.size - 1) {
            val loc1 = locations[i]
            val loc2 = locations[i + 1]
            totalDistance += com.cosmiclaboratory.voyager.utils.LocationUtils.calculateDistance(
                loc1.latitude, loc1.longitude,
                loc2.latitude, loc2.longitude
            )
        }
        return totalDistance
    }

    private fun calculatePercentChange(old: Int, new: Int): Double {
        if (old == 0) return if (new > 0) 100.0 else 0.0
        return ((new - old).toDouble() / old) * 100.0
    }

    fun refresh() {
        loadAllStatistics()
    }
}

data class StatisticsUiState(
    val weeklyComparison: WeeklyComparisonData? = null,
    val placePatterns: List<PlacePattern>? = null,
    val movementStats: MovementStats? = null,
    val socialStats: SocialHealthStats? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// Data classes matching AnalyticsUseCases structure
data class WeeklyComparisonData(
    val dateRange: String,
    val placesThisWeek: Int,
    val placesLastWeek: Int,
    val placesChange: Double,
    val distanceThisWeek: Double,
    val distanceLastWeek: Double,
    val distanceChange: Double,
    val timeAwayThisWeek: Int,
    val timeAwayLastWeek: Int,
    val timeAwayChange: Double
)

data class PlacePattern(
    val placeName: String,
    val category: String,
    val visitCount: Int,
    val typicalDays: List<String>,
    val avgDurationMinutes: Int
)

data class MovementStats(
    val totalDistanceKm: Double,
    val avgSpeedKmh: Double,
    val mostActiveDay: String
)

data class SocialHealthStats(
    val uniquePlaces: Int,
    val varietyScore: Int,
    val categoryBreakdown: Map<String, Int>
)

// Mock data removed - now using real data from repositories
