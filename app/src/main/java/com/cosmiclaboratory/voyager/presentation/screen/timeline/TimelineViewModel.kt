package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.model.ids.PlaceId
import com.cosmiclaboratory.voyager.domain.repository.CorrectionRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.TimelineRepository
import com.cosmiclaboratory.voyager.domain.usecase.OverrideSegmentTypeUseCase
import com.cosmiclaboratory.voyager.domain.usecase.TimelineReview
import com.cosmiclaboratory.voyager.presentation.state.DayNavigationStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Why the timeline has nothing to show — lets the UI pick a calm, specific empty
 * state instead of a single generic "no data" message.
 */
enum class TimelineEmptyReason { NONE, NO_PERMISSION, TRACKING_OFF, CAPTURING_NOW, QUIET_DAY }

data class TimelineUiState(
    val dayKey: String = "",
    val segments: List<TimelineSegment> = emptyList(),
    val totalDistanceM: Double = 0.0,
    val totalSteps: Int = 0,
    val isLoading: Boolean = true,
    val focusedSegmentId: Long? = null,
    val errorMessage: String? = null,
    val activeVisit: ActiveVisitInfo? = null,
    val pendingCandidate: PendingVisitCandidate? = null,
    val isTracking: Boolean = false,
    /** True when only "Approximate" location is granted — drives the city-level banner. */
    val isRoughMode: Boolean = false,
    /** Reason there is nothing to show (only meaningful when [segments] is empty). */
    val emptyReason: TimelineEmptyReason = TimelineEmptyReason.NONE,
    /** Count of low-confidence/unknown visits the user could confirm or rename. */
    val reviewCount: Int = 0
)

sealed interface TimelineIntent {
    data class SelectSegment(val segmentId: Long) : TimelineIntent
    data object ClearSelection : TimelineIntent
    data class CorrectSegmentType(val segmentId: Long, val newType: String) : TimelineIntent
    data class SelectGeocodeName(val placeId: Long, val name: String) : TimelineIntent
    data class RenamePlace(val placeId: Long, val name: String) : TimelineIntent
    /** Confirm a place is correct — raises its confidence and clears the review cue. */
    data class ConfirmPlace(val placeId: Long) : TimelineIntent
    /** Confirm every confirmable low-confidence place on the visible day at once. */
    data object ConfirmAll : TimelineIntent
    data object NavigatePrevious : TimelineIntent
    data object NavigateNext : TimelineIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val correctionRepository: CorrectionRepository,
    private val placeRepository: PlaceRepository,
    private val overrideSegmentType: OverrideSegmentTypeUseCase,
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
                val isTracking = live?.isTracking ?: false
                val hasActive = live?.activeVisit != null || live?.pendingCandidate != null
                val emptyReason = when {
                    segments.isNotEmpty() || hasActive -> TimelineEmptyReason.NONE
                    !permissions.hasAnyLocation -> TimelineEmptyReason.NO_PERMISSION
                    !isTracking -> TimelineEmptyReason.TRACKING_OFF
                    dayKey == todayKey -> TimelineEmptyReason.CAPTURING_NOW
                    else -> TimelineEmptyReason.QUIET_DAY
                }
                TimelineUiState(
                    dayKey = day.dayKey,
                    segments = segments,
                    totalDistanceM = movementDistance,
                    totalSteps = day.totalSteps,
                    isLoading = false,
                    focusedSegmentId = dayNavigation.focusedSegmentId.value,
                    activeVisit = live?.activeVisit,
                    pendingCandidate = live?.pendingCandidate,
                    isTracking = isTracking,
                    isRoughMode = permissions.isApproximateLocationOnly,
                    emptyReason = emptyReason,
                    reviewCount = TimelineReview.reviewCount(segments)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUiState())

    val dayKeys: StateFlow<List<String>> = timelineRepository.observeDayKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onIntent(intent: TimelineIntent) {
        when (intent) {
            is TimelineIntent.SelectSegment ->
                // The detail sheet (SegmentDetailViewModel) loads its own evidence, so just
                // focus the segment here — no need to fetch-and-discard evidence per tap.
                dayNavigation.focusSegment(intent.segmentId)
            is TimelineIntent.ClearSelection -> dayNavigation.clearFocus()
            is TimelineIntent.CorrectSegmentType -> viewModelScope.launch {
                // Apply the authoritative override so the row's label actually changes
                // (the timeline Flow re-emits on the DB write). The feedback row remains
                // the learning signal for FeedbackCalibrationWorker.
                val newType = try { SegmentType.valueOf(intent.newType) } catch (_: Exception) { null }
                if (newType != null) overrideSegmentType.setOverride(intent.segmentId, newType)
                correctionRepository.applyCorrection(
                    correctionType = CorrectionType.CHANGE_TRANSPORT_MODE,
                    entityType = "segment",
                    entityId = intent.segmentId,
                    beforeValue = null,
                    afterValue = intent.newType
                )
            }
            is TimelineIntent.SelectGeocodeName -> viewModelScope.launch {
                placeRepository.renamePlace(PlaceId(intent.placeId), intent.name)
            }
            is TimelineIntent.RenamePlace -> viewModelScope.launch {
                placeRepository.renamePlace(PlaceId(intent.placeId), intent.name)
            }
            is TimelineIntent.ConfirmPlace -> viewModelScope.launch {
                if (intent.placeId > 0) placeRepository.confirmPlace(PlaceId(intent.placeId))
            }
            is TimelineIntent.ConfirmAll -> viewModelScope.launch {
                // Confirm every confirmable (placeId > 0) low-confidence place on the day.
                uiState.value.segments
                    .filter { TimelineReview.isReviewable(it) }
                    .mapNotNull { it.place?.placeId?.takeIf { id -> id > 0 } }
                    .distinct()
                    .forEach { placeId -> placeRepository.confirmPlace(PlaceId(placeId)) }
            }
            is TimelineIntent.NavigatePrevious -> dayNavigation.navigatePreviousDay()
            is TimelineIntent.NavigateNext -> dayNavigation.navigateNextDay()
        }
    }

}
