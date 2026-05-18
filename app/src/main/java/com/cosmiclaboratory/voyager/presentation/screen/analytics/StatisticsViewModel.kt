package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.cosmiclaboratory.voyager.domain.model.Anomaly
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val placeRepository: com.cosmiclaboratory.voyager.domain.repository.PlaceRepository,
    private val visitDao: com.cosmiclaboratory.voyager.storage.database.dao.VisitDao,
    private val placeDao: com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao,
    private val buildCarbonFootprint: com.cosmiclaboratory.voyager.domain.usecase.BuildCarbonFootprintUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow<DateRangePeriod>(DateRangePeriod.ThisWeek)
    val selectedPeriod: StateFlow<DateRangePeriod> = _selectedPeriod.asStateFlow()

    init {
        loadAllStatistics()
    }

    fun selectPeriod(period: DateRangePeriod) {
        _selectedPeriod.value = period
        loadAllStatistics()
    }

    private fun loadAllStatistics() {
        val period = _selectedPeriod.value
        val range = period.toDateRange()
        val previousRange = period.previousPeriodRange()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Weekly comparison — with real place counts
                var weeklyData: WeeklyComparisonData? = null
                withTimeoutOrNull(5_000) { analyticsRepository.observeComparisons(range, previousRange).first() }?.let { comparison ->
                    val distDelta = comparison.metricDeltas["distance"]
                    if (distDelta != null) {
                        // Count unique places visited in each period
                        val placesA = countUniquePlaces(range)
                        val placesB = countUniquePlaces(previousRange)
                        val placesChange = if (placesB > 0) ((placesA - placesB).toDouble() / placesB) * 100 else 0.0

                        // Time away = total segment duration minus VISIT segments at home
                        val timeAwayA = computeTimeAwayHours(range)
                        val timeAwayB = computeTimeAwayHours(previousRange)
                        val timeAwayChange = if (timeAwayB > 0) ((timeAwayA - timeAwayB).toDouble() / timeAwayB) * 100 else 0.0

                        weeklyData = WeeklyComparisonData(
                            dateRange = period.displayLabel(),
                            placesThisWeek = placesA,
                            placesLastWeek = placesB,
                            placesChange = placesChange,
                            distanceThisWeek = distDelta.valueA,
                            distanceLastWeek = distDelta.valueB,
                            distanceChange = distDelta.percentDelta,
                            timeAwayThisWeek = timeAwayA,
                            timeAwayLastWeek = timeAwayB,
                            timeAwayChange = timeAwayChange
                        )
                    }
                }

                // Movement stats from dashboard
                var movementStats: MovementStats? = null
                withTimeoutOrNull(5_000) { analyticsRepository.observeDashboard(range).first() }?.let { dashboard ->
                    dashboard.dailySummary?.let { summary ->
                        movementStats = MovementStats(
                            totalDistanceKm = summary.totalDistanceM / 1000.0,
                            avgSpeedKmh = if (summary.totalDistanceM > 0 && summary.firstActivityAt != null && summary.lastActivityAt != null) {
                                val durationHours = (summary.lastActivityAt - summary.firstActivityAt) / 3_600_000.0
                                if (durationHours > 0) (summary.totalDistanceM / 1000.0) / durationHours else 0.0
                            } else 0.0,
                            mostActiveDay = summary.dayKey
                        )
                    }
                }

                // Anomalies
                val anomalies = withTimeoutOrNull(5_000) { analyticsRepository.observeAnomalies(range).first() } ?: emptyList()

                // Place patterns
                val placePatterns = computePlacePatterns(range)

                // Social health stats
                val socialStats = computeSocialStats(range)

                // Carbon footprint — per-transport-mode CO2 estimate
                val carbonFootprint = withTimeoutOrNull(5_000) {
                    buildCarbonFootprint.build(range, period.displayLabel())
                }

                _uiState.value = StatisticsUiState(
                    weeklyComparison = weeklyData,
                    placePatterns = placePatterns,
                    movementStats = movementStats,
                    socialStats = socialStats,
                    anomalies = anomalies,
                    carbonFootprint = carbonFootprint,
                    periodLabel = period.displayLabel(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load statistics: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadAllStatistics()
    }

    private suspend fun countUniquePlaces(range: DateRange): Int {
        val allVisits = collectVisitsForRange(range)
        return allVisits.filter { it.placeId != 0L }.map { it.placeId }.toSet().size
    }

    private suspend fun computeTimeAwayHours(range: DateRange): Int {
        val allVisits = collectVisitsForRange(range)
        val homePlace = placeDao.getHomePlace()
        val homePlaceId = homePlace?.placeId
        val awayMs = allVisits
            .filter { it.placeId != homePlaceId }
            .sumOf { it.dwellMs ?: 0L }
        return (awayMs / 3_600_000).toInt()
    }

    private suspend fun collectVisitsForRange(range: DateRange): List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity> {
        val start = LocalDate.parse(range.startDay)
        val end = LocalDate.parse(range.endDay)
        val visits = mutableListOf<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>()
        var day = start
        while (!day.isAfter(end)) {
            visits.addAll(visitDao.getByDayKey(day.toString()))
            day = day.plusDays(1)
        }
        return visits
    }

    private suspend fun computePlacePatterns(range: DateRange): List<PlacePatternSummary> {
        val allVisits = collectVisitsForRange(range)
        return allVisits
            .filter { it.placeId != 0L }
            .groupBy { it.placeId }
            .mapNotNull { (placeId, visits) ->
                val place = placeDao.getById(placeId) ?: return@mapNotNull null
                val avgDwellMinutes = visits
                    .mapNotNull { it.dwellMs }
                    .let { durations -> if (durations.isNotEmpty()) (durations.average() / 60_000).toInt() else 0 }

                // Determine typical days of week
                val dayOfWeekCounts = visits.groupBy { v ->
                    java.time.Instant.ofEpochMilli(v.arrivalAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                }
                val typicalDays = dayOfWeekCounts
                    .entries
                    .sortedByDescending { it.value.size }
                    .take(3)
                    .map { it.key }

                PlacePatternSummary(
                    placeName = place.userDisplayName ?: place.bestProviderName ?: "Place #$placeId",
                    category = place.category,
                    visitCount = visits.size,
                    typicalDays = typicalDays,
                    avgDurationMinutes = avgDwellMinutes
                )
            }
            .sortedByDescending { it.visitCount }
            .take(10)
    }

    private suspend fun computeSocialStats(range: DateRange): SocialHealthStats {
        val allVisits = collectVisitsForRange(range)
        val uniquePlaceIds = allVisits.filter { it.placeId != 0L }.map { it.placeId }.toSet()

        val categoryBreakdown = mutableMapOf<String, Int>()
        for (placeId in uniquePlaceIds) {
            val place = placeDao.getById(placeId) ?: continue
            categoryBreakdown[place.category] = (categoryBreakdown[place.category] ?: 0) + 1
        }

        // Variety score: number of distinct categories out of max possible (8 categories)
        val varietyScore = ((categoryBreakdown.size.toFloat() / 8f) * 100).toInt().coerceAtMost(100)

        return SocialHealthStats(
            uniquePlaces = uniquePlaceIds.size,
            varietyScore = varietyScore,
            categoryBreakdown = categoryBreakdown
        )
    }

}

data class StatisticsUiState(
    val weeklyComparison: WeeklyComparisonData? = null,
    val placePatterns: List<PlacePatternSummary>? = null,
    val movementStats: MovementStats? = null,
    val socialStats: SocialHealthStats? = null,
    val anomalies: List<Anomaly> = emptyList(),
    val carbonFootprint: com.cosmiclaboratory.voyager.domain.model.CarbonFootprint? = null,
    val periodLabel: String = "This Week",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

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

data class PlacePatternSummary(
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

// Extension functions for DateRangePeriod → DateRange conversion
private fun DateRangePeriod.toDateRange(): DateRange {
    val today = LocalDate.now()
    return when (this) {
        is DateRangePeriod.Today -> DateRange(today.toString(), today.toString())
        is DateRangePeriod.ThisWeek -> {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DateRange(weekStart.toString(), today.toString())
        }
        is DateRangePeriod.ThisMonth -> {
            val monthStart = today.withDayOfMonth(1)
            DateRange(monthStart.toString(), today.toString())
        }
        is DateRangePeriod.Last30Days -> DateRange(today.minusDays(30).toString(), today.toString())
        is DateRangePeriod.Custom -> DateRange(start.toString(), end.toString())
    }
}

private fun DateRangePeriod.previousPeriodRange(): DateRange {
    val today = LocalDate.now()
    return when (this) {
        is DateRangePeriod.Today -> {
            val yesterday = today.minusDays(1)
            DateRange(yesterday.toString(), yesterday.toString())
        }
        is DateRangePeriod.ThisWeek -> {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val prevEnd = weekStart.minusDays(1)
            val prevStart = prevEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DateRange(prevStart.toString(), prevEnd.toString())
        }
        is DateRangePeriod.ThisMonth -> {
            val monthStart = today.withDayOfMonth(1)
            val prevEnd = monthStart.minusDays(1)
            val prevStart = prevEnd.withDayOfMonth(1)
            DateRange(prevStart.toString(), prevEnd.toString())
        }
        is DateRangePeriod.Last30Days -> DateRange(today.minusDays(60).toString(), today.minusDays(31).toString())
        is DateRangePeriod.Custom -> {
            val days = java.time.temporal.ChronoUnit.DAYS.between(start, end)
            DateRange(start.minusDays(days + 1).toString(), start.minusDays(1).toString())
        }
    }
}
