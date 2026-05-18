package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.repository.CorrectionRepository
import com.cosmiclaboratory.voyager.domain.repository.EvidenceRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.TimelineRepository
import com.cosmiclaboratory.voyager.presentation.state.DayNavigationStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val dayKey: String = "",
    val segments: List<TimelineSegment> = emptyList(),
    val totalDistanceM: Double = 0.0,
    val totalSteps: Int = 0,
    val isLoading: Boolean = true,
    val focusedSegmentId: Long? = null,
    val selectedSegmentEvidence: EvidenceBlock? = null,
    val errorMessage: String? = null,
    val activeVisit: ActiveVisitInfo? = null,
    val pendingCandidate: PendingVisitCandidate? = null,
    val isTracking: Boolean = false,
    /** True when only "Approximate" location is granted — drives the city-level banner. */
    val isRoughMode: Boolean = false
)

sealed interface TimelineIntent {
    data class SelectSegment(val segmentId: Long) : TimelineIntent
    data object ClearSelection : TimelineIntent
    data class CorrectSegmentType(val segmentId: Long, val newType: String) : TimelineIntent
    data class SelectGeocodeName(val placeId: Long, val name: String) : TimelineIntent
    data class RenamePlace(val placeId: Long, val name: String) : TimelineIntent
    data object NavigatePrevious : TimelineIntent
    data object NavigateNext : TimelineIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val evidenceRepository: EvidenceRepository,
    private val correctionRepository: CorrectionRepository,
    private val placeRepository: PlaceRepository,
    private val dayNavigation: DayNavigationStateHolder,
    private val permissionMonitor: com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
) : ViewModel() {

    private val todayKey = java.time.LocalDate.now().toString()

    val uiState: StateFlow<TimelineUiState> = dayNavigation.currentDayKey
        .flatMapLatest { dayKey ->
            val liveFlow: Flow<LiveTimelineState?> = if (dayKey == todayKey) {
                timelineRepository.observeLiveTimeline()
            } else {
                flowOf(null)
            }
            combine(
                timelineRepository.observeDay(dayKey),
                liveFlow,
                permissionMonitor.snapshot
            ) { day, live, permissions ->
                // Merge DB segments with the in-progress segment from the Segmenter's
                // in-memory buffer so the user sees real-time movement data before the
                // 5-minute periodic flush writes it to the database.
                // Filter out VISIT segments that overlap the active visit to prevent
                // duplicate display (active visit card + segment list).
                val activeArrival = live?.activeVisit?.arrivalAt
                val baseSegments = if (activeArrival != null) {
                    day.segments.filterNot { seg ->
                        // Remove any segments whose time range falls within the active visit
                        // period. These are pre-confirmation artifacts (UNKNOWN_MOTION from
                        // initial samples, and placeless VISIT segments from the Segmenter)
                        // that duplicate the active visit card shown above.
                        seg.startAt >= activeArrival
                    }
                } else {
                    day.segments
                }
                val rawSegments = if (live?.inProgressSegment != null) {
                    baseSegments + live.inProgressSegment
                } else {
                    baseSegments
                }
                val segments = rawSegments
                // Only count movement distance (exclude stationary segments and gaps)
                val movementDistance = segments
                    .filter { it.type != SegmentType.VISIT && it.type != SegmentType.DWELL && it.type != SegmentType.GAP }
                    .sumOf { it.distanceM }
                TimelineUiState(
                    dayKey = day.dayKey,
                    segments = segments,
                    totalDistanceM = movementDistance,
                    totalSteps = day.totalSteps,
                    isLoading = false,
                    focusedSegmentId = dayNavigation.focusedSegmentId.value,
                    activeVisit = live?.activeVisit,
                    pendingCandidate = live?.pendingCandidate,
                    isTracking = live?.isTracking ?: false,
                    isRoughMode = permissions.isApproximateLocationOnly
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUiState())

    val dayKeys: StateFlow<List<String>> = timelineRepository.observeDayKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onIntent(intent: TimelineIntent) {
        when (intent) {
            is TimelineIntent.SelectSegment -> {
                dayNavigation.focusSegment(intent.segmentId)
                viewModelScope.launch {
                    val evidence = evidenceRepository.getSegmentEvidence(intent.segmentId)
                    // Evidence is available for segment detail sheet
                }
            }
            is TimelineIntent.ClearSelection -> dayNavigation.clearFocus()
            is TimelineIntent.CorrectSegmentType -> viewModelScope.launch {
                correctionRepository.applyCorrection(
                    correctionType = CorrectionType.CHANGE_TRANSPORT_MODE,
                    entityType = "segment",
                    entityId = intent.segmentId,
                    beforeValue = null,
                    afterValue = intent.newType
                )
            }
            is TimelineIntent.SelectGeocodeName -> viewModelScope.launch {
                placeRepository.renamePlace(intent.placeId, intent.name)
            }
            is TimelineIntent.RenamePlace -> viewModelScope.launch {
                placeRepository.renamePlace(intent.placeId, intent.name)
            }
            is TimelineIntent.NavigatePrevious -> dayNavigation.navigatePreviousDay()
            is TimelineIntent.NavigateNext -> dayNavigation.navigateNextDay()
        }
    }

}
