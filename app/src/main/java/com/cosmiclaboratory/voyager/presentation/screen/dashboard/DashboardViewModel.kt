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
    /** Session open but manually paused (capture stopped, resumable). */
    val isPaused: Boolean = false,
    val lastSampleAt: Long? = null,
    val sessionStartedAt: Long? = null,
    val activeVisit: ActiveVisitInfo? = null,
    val pendingCandidate: PendingVisitCandidate? = null,
    val streakDays: Int = 0,
    /** The user's chosen job — drives dashboard module ordering. */
    val activeJob: Job = Job.MEMORY,
    /** Whole-device discharge per day during tracking; null until measured. */
    val batteryPercentPerDay: Int? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val stepsRepository: StepsRepository,
    private val trackingRepository: TrackingRepository,
    private val trackingSessionDao: TrackingSessionDao,
    private val timelineRepository: TimelineRepository,
    private val dailyRollupDao: DailyRollupDao,
    private val settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository,
    private val batteryUsageReporter: com.cosmiclaboratory.voyager.platform.battery.BatteryUsageReporter
) : ViewModel() {

    private val todayKey = java.time.LocalDate.now().toString()
    private val todayRange = DateRange(todayKey, todayKey)

    private val _streakDays = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val _batteryPerDay = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            _streakDays.value = computeStreak(
                dailyRollupDao.getActiveDayKeys().toHashSet(),
                java.time.LocalDate.now()
            )
        }
        viewModelScope.launch {
            _batteryPerDay.value = batteryUsageReporter.estimate().percentPerDay
        }
    }

    // Observe active session start time — ticks immediately when tracking begins
    private val sessionStartedAt: Flow<Long?> = trackingSessionDao.observeActiveSession()
        .map { it?.startedAt }

    private data class InnerState(
        val health: TrackingHealth,
        val sessionStart: Long?,
        val liveTimeline: LiveTimelineState,
        val streak: Int,
        val activeJob: Job,
        val batteryPerDay: Int?
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
            combine(_streakDays, _batteryPerDay) { streak, battery -> streak to battery },
            settingsRepository.observeSettings()
        ) { h, s, live, streakBattery, settings ->
            InnerState(
                health = h, sessionStart = s, liveTimeline = live,
                streak = streakBattery.first,
                activeJob = Job.fromId(settings.activeJob) ?: Job.MEMORY,
                batteryPerDay = streakBattery.second
            )
        }
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
            isPaused = trackingState.isPaused,
            lastSampleAt = inner.health.lastSampleAt,
            sessionStartedAt = inner.sessionStart,
            activeVisit = inner.liveTimeline.activeVisit,
            pendingCandidate = inner.liveTimeline.pendingCandidate,
            streakDays = inner.streak,
            activeJob = inner.activeJob,
            batteryPercentPerDay = inner.batteryPerDay,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    companion object {
        /**
         * Consecutive active days ending at the latest active day. If [today] has no
         * activity *yet*, the streak isn't broken until the day actually ends — so count
         * from yesterday rather than resetting to 0 every morning. Exposed for testing.
         */
        internal fun computeStreak(activeDayKeys: Set<String>, today: java.time.LocalDate): Int {
            var date = if (activeDayKeys.contains(today.toString())) today else today.minusDays(1)
            var streak = 0
            while (activeDayKeys.contains(date.toString())) {
                streak++
                date = date.minusDays(1)
            }
            return streak
        }
    }
}
