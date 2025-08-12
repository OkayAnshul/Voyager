package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import com.cosmiclaboratory.voyager.domain.repository.StepsRepository
import com.cosmiclaboratory.voyager.domain.repository.TimelineRepository
import com.cosmiclaboratory.voyager.domain.repository.TrackingRepository
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.TrackingSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val dailySummary: DailySummary? = null,
    val weeklyComparison: ComparisonResult? = null,
    val anomalies: List<Anomaly> = emptyList(),
    val insights: List<DashboardInsight> = emptyList(),
    val topPlaces: List<PlaceSummary> = emptyList(),
    val stepChart: List<HourlySteps> = emptyList(),
    val totalStepsToday: Int = 0,
    val isTracking: Boolean = false,
    val lastSampleAt: Long? = null,
    val sessionStartedAt: Long? = null,
    val activeVisit: ActiveVisitInfo? = null,
    val pendingCandidate: PendingVisitCandidate? = null,
    val streakDays: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val stepsRepository: StepsRepository,
    private val trackingRepository: TrackingRepository,
    private val trackingSessionDao: TrackingSessionDao,
    private val timelineRepository: TimelineRepository,
    private val dailyRollupDao: DailyRollupDao
) : ViewModel() {

    private val todayKey = java.time.LocalDate.now().toString()
    private val todayRange = DateRange(todayKey, todayKey)

    private val _streakDays = kotlinx.coroutines.flow.MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val activeDays = dailyRollupDao.getActiveDayKeys()
            val activeDaySet = activeDays.toHashSet()
            var streak = 0
            var date = java.time.LocalDate.now()
            while (activeDaySet.contains(date.toString())) {
                streak++
                date = date.minusDays(1)
            }
            _streakDays.value = streak
        }
    }

    // Observe active session start time — ticks immediately when tracking begins
    private val sessionStartedAt: Flow<Long?> = trackingSessionDao.observeActiveSession()
        .map { it?.startedAt }

    private data class InnerState(
        val health: TrackingHealth,
        val sessionStart: Long?,
        val liveTimeline: LiveTimelineState,
        val streak: Int
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        analyticsRepository.observeDashboard(todayRange),
        stepsRepository.observeDailySteps(todayKey),
        stepsRepository.observeHourlySteps(todayKey),
        trackingRepository.observeRuntimeState(),
        combine(
            trackingRepository.observeHealth(),
            sessionStartedAt,
            timelineRepository.observeLiveTimeline(),
            _streakDays
        ) { h, s, live, streak -> InnerState(h, s, live, streak) }
    ) { dashboard, steps, hourly, trackingState, inner ->
        DashboardUiState(
            dailySummary = dashboard.dailySummary,
            weeklyComparison = dashboard.weeklyComparison,
            anomalies = dashboard.anomalies,
            insights = dashboard.insights,
            topPlaces = dashboard.topPlaces,
            stepChart = hourly,
            totalStepsToday = steps.totalSteps,
            isTracking = trackingState.isTracking,
            lastSampleAt = inner.health.lastSampleAt,
            sessionStartedAt = inner.sessionStart,
            activeVisit = inner.liveTimeline.activeVisit,
            pendingCandidate = inner.liveTimeline.pendingCandidate,
            streakDays = inner.streak,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
