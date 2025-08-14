package com.cosmiclaboratory.voyager.presentation.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.MapRepository
import com.cosmiclaboratory.voyager.domain.repository.TimelineRepository
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.presentation.state.DayNavigationStateHolder
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val dayKey: String = "",
    val routes: List<MapRoute> = emptyList(),
    val visitMarkers: List<VisitMarker> = emptyList(),
    val bounds: LatLngBounds? = null,
    val focusedSegmentId: Long? = null,
    val focusedRoute: MapRoute? = null,
    val currentLocation: Pair<Double, Double>? = null,
    val activeVisitLocation: ActiveVisitInfo? = null,
    val focusedVisitId: Long? = null,
    val focusedVisitMarker: VisitMarker? = null,
    val isLoading: Boolean = true,
    val centerOnUserRequested: Boolean = false,
    val fitBoundsRequested: Boolean = false
)

sealed interface MapIntent {
    data class TapMarker(val visitId: Long) : MapIntent
    data class TapRoute(val segmentId: Long) : MapIntent
    data object ClearFocus : MapIntent
    data object NavigatePreviousDay : MapIntent
    data object NavigateNextDay : MapIntent
    data object CenterOnUser : MapIntent
    data object FitBounds : MapIntent
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val timelineRepository: TimelineRepository,
    private val dayNavigation: DayNavigationStateHolder,
    private val rawLocationSampleDao: RawLocationSampleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        // Continuously observe day data — updates in real time as pipeline writes new segments
        viewModelScope.launch {
            dayNavigation.currentDayKey.flatMapLatest { dayKey ->
                _uiState.update { it.copy(dayKey = dayKey, isLoading = true) }
                timelineRepository.observeDay(dayKey).map { day -> dayKey to day }
            }.collectLatest { (dayKey, day) ->
                val markers = mapRepository.getVisitMarkers(dayKey)
                val bounds = mapRepository.getDayBoundingBox(dayKey)
                // Build routes from reconciled timeline segments (already merged by
                // TimelineReconciler) so consecutive same-type movements appear as
                // single continuous polylines instead of fragmented 5-minute chunks.
                val routes = day.segments.mapNotNull { segment ->
                    segment.route?.let { route ->
                        val points = PolylineEncoder.decode(
                            route.simplifiedPolyline ?: route.encodedPolyline
                        )
                        if (points.size >= 2) {
                            MapRoute(
                                routeId = route.routeId,
                                segmentId = segment.segmentId,
                                polylinePoints = points,
                                color = getTransportColor(route.transportMode),
                                transportMode = route.transportMode
                            )
                        } else null
                    }
                }
                _uiState.update {
                    it.copy(
                        routes = routes,
                        visitMarkers = markers,
                        bounds = bounds,
                        isLoading = false,
                        centerOnUserRequested = bounds == null && markers.isEmpty()
                    )
                }
            }
        }

        viewModelScope.launch {
            dayNavigation.focusedSegmentId.collect { segmentId ->
                _uiState.update { it.copy(focusedSegmentId = segmentId) }
                if (segmentId != null) {
                    val route = mapRepository.getRouteForSegment(segmentId)
                    if (route != null) {
                        _uiState.update { it.copy(focusedRoute = route, focusedVisitMarker = null) }
                    } else {
                        // VISIT segment — find the visit marker by matching segment to place
                        val marker = mapRepository.getVisitMarkerForSegment(segmentId)
                            ?: _uiState.value.visitMarkers.firstOrNull()
                        _uiState.update { it.copy(focusedRoute = null, focusedVisitMarker = marker) }
                    }
                } else {
                    _uiState.update { it.copy(focusedRoute = null, focusedVisitMarker = null) }
                }
            }
        }

        viewModelScope.launch {
            dayNavigation.focusedVisitId.collect { visitId ->
                if (visitId != null) {
                    val marker = _uiState.value.visitMarkers.find { it.visitId == visitId }
                    _uiState.update { it.copy(focusedVisitId = visitId, focusedVisitMarker = marker) }
                } else {
                    _uiState.update { it.copy(focusedVisitId = null, focusedVisitMarker = null) }
                }
            }
        }

        // Observe active visit from live timeline
        viewModelScope.launch {
            timelineRepository.observeLiveTimeline().collect { live ->
                _uiState.update { it.copy(activeVisitLocation = live.activeVisit) }
            }
        }

        // Observe live location from the latest raw sample
        viewModelScope.launch {
            rawLocationSampleDao.observeLatest().collect { sample ->
                if (sample != null) {
                    _uiState.update {
                        it.copy(currentLocation = sample.lat to sample.lng)
                    }
                }
            }
        }
    }

    fun onIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.TapMarker -> dayNavigation.focusVisit(intent.visitId)
            is MapIntent.TapRoute -> dayNavigation.focusSegment(intent.segmentId)
            is MapIntent.ClearFocus -> dayNavigation.clearFocus()
            is MapIntent.NavigatePreviousDay -> dayNavigation.navigatePreviousDay()
            is MapIntent.NavigateNextDay -> dayNavigation.navigateNextDay()
            is MapIntent.CenterOnUser -> _uiState.update { it.copy(centerOnUserRequested = true) }
            is MapIntent.FitBounds -> _uiState.update { it.copy(fitBoundsRequested = true) }
        }
    }

    fun consumeCenterOnUser() { _uiState.update { it.copy(centerOnUserRequested = false) } }
    fun consumeFitBounds() { _uiState.update { it.copy(fitBoundsRequested = false) } }

    fun refresh() {
        // With continuous observation via flatMapLatest, data refreshes automatically
        // when the DB changes. This is a no-op kept for API compatibility.
    }

    private fun getTransportColor(transportMode: String): Int = when (transportMode) {
        "WALK" -> 0xFF4CAF50.toInt()
        "RUN" -> 0xFFFF9800.toInt()
        "CYCLE" -> 0xFF2196F3.toInt()
        "DRIVE" -> 0xFF9C27B0.toInt()
        "TRANSIT" -> 0xFFFF5722.toInt()
        else -> 0xFF757575.toInt()
    }
}
