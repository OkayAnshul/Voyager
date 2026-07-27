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
    private val buildCarbonFootprint: com.cosmiclaboratory.voyager.domain.usecase.BuildCarbonFootprintUseCase,
    private val detectRecurringPatterns: com.cosmiclaboratory.voyager.domain.usecase.DetectRecurringPatternsUseCase,
    private val detectNotableEvents: com.cosmiclaboratory.voyager.domain.usecase.DetectNotableEventsUseCase,
    private val buildOnThisDay: com.cosmiclaboratory.voyager.domain.usecase.BuildOnThisDayUseCase,
    private val buildHeatmap: com.cosmiclaboratory.voyager.domain.usecase.BuildHeatmapUseCase,
    private val detectSleepRhythm: com.cosmiclaboratory.voyager.domain.usecase.DetectSleepRhythmUseCase,
    private val analyzeCommute: com.cosmiclaboratory.voyager.domain.usecase.AnalyzeCommuteUseCase,
    private val computeTimeBudget: com.cosmiclaboratory.voyager.domain.usecase.ComputeTimeBudgetUseCase,
    private val detectRoutineBreaks: com.cosmiclaboratory.voyager.domain.usecase.DetectRoutineBreaksUseCase,
    private val predictNextPlace: com.cosmiclaboratory.voyager.domain.usecase.PredictNextPlaceUseCase,
    private val computeDayRhythm: com.cosmiclaboratory.voyager.domain.usecase.ComputeDayRhythmUseCase,
    private val computeTrackingStreak: com.cosmiclaboratory.voyager.domain.usecase.ComputeTrackingStreakUseCase,
    private val computeExplorationScore: com.cosmiclaboratory.voyager.domain.usecase.ComputeExplorationScoreUseCase
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

                // Routine breaks — "you usually go to Work on Mondays, not today".
                val routineBreaks = withTimeoutOrNull(5_000) { detectRoutineBreaks.forToday() } ?: emptyList()

                // Place patterns
                val placePatterns = computePlacePatterns(range)

                // Recurring routines — "Gym every Tuesday ~7pm". Multi-week by
                // nature, so it ignores the selected period on purpose.
                val routines = computeRoutines()

                // Forward-looking: routines still ahead today.
                val upcoming = withTimeoutOrNull(5_000) { predictNextPlace.upcomingToday() } ?: emptyList()

                // Notable moments + past-year memories (history-based, not
                // period-scoped).
                val notableHighlights = computeNotableHighlights()
                val memories = withTimeoutOrNull(5_000) { buildOnThisDay.build() } ?: emptyList()

                // Activity heatmap over a trailing 26-week window (a heatmap
                // needs a long window to read; independent of the period chip)
                // + year-in-review for the current calendar year.
                val heatmapEnd = LocalDate.now()
                val heatmapStart = heatmapEnd.minusWeeks(26)
                val heatmap = withTimeoutOrNull(5_000) {
                    buildHeatmap.heatmap(
                        com.cosmiclaboratory.voyager.domain.model.HeatmapMetric.DISTANCE,
                        heatmapStart.toString(),
                        heatmapEnd.toString()
                    )
                }
                val yearInReview = withTimeoutOrNull(5_000) { buildHeatmap.yearInReview(heatmapEnd.year) }

                // Life-rhythm insights. Sleep + commute are history-based; the
                // time budget is scoped to the selected period.
                val sleepRhythm = withTimeoutOrNull(5_000) { detectSleepRhythm.forHome() }
                val commute = withTimeoutOrNull(5_000) { analyzeCommute.analyze() }
                val timeBudget = withTimeoutOrNull(5_000) { computeTimeBudget.analyze(range) }
                val dayRhythm = withTimeoutOrNull(5_000) { computeDayRhythm.analyze() }

                // Movement extras (Pro): tracking-day streak + exploration score.
                val trackingStreak = withTimeoutOrNull(5_000) { computeTrackingStreak.compute() }
                val explorationScore = withTimeoutOrNull(5_000) { computeExplorationScore.compute(range) }

                // Social health stats
                val socialStats = computeSocialStats(range)

                // Overview narrative — one honest sentence built from the models
                // (pure, tested in InsightNarratorsTest).
                val placeCount = weeklyData?.placesThisWeek ?: socialStats.uniquePlaces
                val overviewNarrative = InsightNarrators.overview(
                    weekly = weeklyData,
                    movement = movementStats,
                    placeCount = placeCount,
                    periodLabel = period.displayLabel()
                )
                val weeklyNarrative = InsightNarrators.weekly(weeklyData, period.displayLabel())
                val highlightsNarrative = InsightNarrators.highlights(notableHighlights.size, memories.size)

                // Carbon footprint — per-transport-mode CO2 estimate
                val carbonFootprint = withTimeoutOrNull(5_000) {
                    buildCarbonFootprint.build(range, period.displayLabel())
                }

                _uiState.value = StatisticsUiState(
                    weeklyComparison = weeklyData,
                    placePatterns = placePatterns,
                    routines = routines,
                    upcoming = upcoming,
                    notableHighlights = notableHighlights,
                    memories = memories,
                    movementStats = movementStats,
                    trackingStreak = trackingStreak,
                    explorationScore = explorationScore,
                    heatmap = heatmap,
                    yearInReview = yearInReview,
                    sleepRhythm = sleepRhythm,
                    commute = commute,
                    timeBudget = timeBudget,
                    dayRhythm = dayRhythm,
                    socialStats = socialStats,
                    anomalies = anomalies,
                    routineBreaks = routineBreaks,
                    carbonFootprint = carbonFootprint,
                    overviewNarrative = overviewNarrative,
                    weeklyNarrative = weeklyNarrative,
                    highlightsNarrative = highlightsNarrative,
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

    /**
     * Detected recurring routines, grouped for display: patterns for the same
     * place at the same hour collapse into one row so "Gym, Mon + Wed + Fri
     * ~6pm" reads as a single habit rather than three. Ordered by confidence.
     */
    private suspend fun computeRoutines(): List<RoutineSummary> {
        val patterns = withTimeoutOrNull(5_000) { detectRecurringPatterns.forAllPlaces() } ?: return emptyList()
        if (patterns.isEmpty()) return emptyList()
        return patterns
            .groupBy { it.placeId to it.typicalHour }
            .mapNotNull { (key, group) ->
                val (placeId, hour) = key
                val place = placeDao.getById(placeId) ?: return@mapNotNull null
                RoutineSummary(
                    placeName = place.userDisplayName ?: place.bestProviderName ?: "Place #$placeId",
                    category = place.category,
                    daysIso = group.map { calendarDowToIso(it.dayOfWeek) }.distinct().sorted(),
                    typicalHour = hour,
                    visitCount = group.sumOf { it.visitCount },
                    confidence = group.maxOf { it.confidence }
                )
            }
            .sortedWith(compareByDescending<RoutineSummary> { it.confidence }.thenByDescending { it.visitCount })
            .take(12)
    }

    /** Calendar day-of-week (SUNDAY=1..SATURDAY=7) → ISO (MONDAY=1..SUNDAY=7). */
    private fun calendarDowToIso(calDow: Int): Int =
        if (calDow == java.util.Calendar.SUNDAY) 7 else calDow - 1

    /** Records + firsts worth surfacing, flattened to display rows. */
    private suspend fun computeNotableHighlights(): List<NotableHighlight> {
        val events = withTimeoutOrNull(5_000) { detectNotableEvents.forRecentDays() } ?: return emptyList()
        return events.mapNotNull { event ->
            when (event) {
                is com.cosmiclaboratory.voyager.domain.usecase.NotableEvent.FirstVisitToPlace ->
                    NotableHighlight(
                        title = "New place discovered",
                        detail = "${event.placeName ?: "A new place"} · first visit ${event.dayKey}"
                    )
                is com.cosmiclaboratory.voyager.domain.usecase.NotableEvent.LongestDwellAtPlace ->
                    NotableHighlight(
                        title = "Longest stay",
                        detail = "${event.placeName ?: "A place"} · ${formatDwell(event.dwellMs)} on ${event.dayKey}"
                    )
                is com.cosmiclaboratory.voyager.domain.usecase.NotableEvent.LongestDistanceDay ->
                    NotableHighlight(
                        title = "Record distance",
                        detail = "%.1f km on ${event.dayKey}".format(event.distanceM / 1000)
                    )
                is com.cosmiclaboratory.voyager.domain.usecase.NotableEvent.FirstActivityOfDay -> null
            }
        }
    }

    private fun formatDwell(ms: Long): String {
        val hours = ms / 3_600_000.0
        return if (hours >= 1.0) "%.1f h".format(hours) else "${ms / 60_000} min"
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
    val routines: List<RoutineSummary>? = null,
    val upcoming: List<com.cosmiclaboratory.voyager.domain.usecase.UpcomingVisit> = emptyList(),
    val notableHighlights: List<NotableHighlight> = emptyList(),
    val memories: List<com.cosmiclaboratory.voyager.domain.model.OnThisDayMemory> = emptyList(),
    val movementStats: MovementStats? = null,
    val trackingStreak: com.cosmiclaboratory.voyager.domain.usecase.TrackingStreak? = null,
    val explorationScore: com.cosmiclaboratory.voyager.domain.usecase.ExplorationScore? = null,
    val heatmap: com.cosmiclaboratory.voyager.domain.model.Heatmap? = null,
    val yearInReview: com.cosmiclaboratory.voyager.domain.model.YearInReview? = null,
    val sleepRhythm: com.cosmiclaboratory.voyager.domain.usecase.SleepRhythm? = null,
    val commute: com.cosmiclaboratory.voyager.domain.usecase.CommuteStats? = null,
    val timeBudget: com.cosmiclaboratory.voyager.domain.usecase.TimeBudget? = null,
    val dayRhythm: com.cosmiclaboratory.voyager.domain.usecase.DayRhythm? = null,
    val socialStats: SocialHealthStats? = null,
    val anomalies: List<Anomaly> = emptyList(),
    val routineBreaks: List<com.cosmiclaboratory.voyager.domain.usecase.RoutineBreak> = emptyList(),
    val carbonFootprint: com.cosmiclaboratory.voyager.domain.model.CarbonFootprint? = null,
    val overviewNarrative: InsightNarrative? = null,
    val weeklyNarrative: InsightNarrative? = null,
    val highlightsNarrative: InsightNarrative? = null,
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

/**
 * A display-ready recurring routine: one place, a set of days, and the typical
 * arrival hour. Built from [com.cosmiclaboratory.voyager.domain.usecase.RecurringPattern].
 */
data class RoutineSummary(
    val placeName: String,
    val category: String,
    val daysIso: List<Int>,   // java.time DayOfWeek values (MONDAY=1..SUNDAY=7)
    val typicalHour: Int,     // 0..23
    val visitCount: Int,
    val confidence: Float
)

/** A surfaced record/first for the Highlights lens, pre-formatted for display. */
data class NotableHighlight(
    val title: String,
    val detail: String
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
