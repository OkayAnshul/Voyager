package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class TimelineEntry(
    val id: String,
    val type: TimelineEntryType,
    val timestamp: LocalDateTime,
    val place: Place? = null,
    val visit: Visit? = null,
    val location: Location? = null,
    val title: String,
    val subtitle: String? = null,
    val duration: Long? = null
)

enum class TimelineEntryType {
    VISIT_START,
    VISIT_END,
    PLACE_DETECTED,
    MOVEMENT,
    DAY_SUMMARY
}

data class TimelineUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val timelineEntries: List<TimelineEntry> = emptyList(),
    val dayAnalytics: DayAnalytics? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
    private val analyticsUseCases: AnalyticsUseCases
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()
    
    init {
        loadTimelineForDate(LocalDate.now())
    }
    
    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadTimelineForDate(date)
    }
    
    private fun loadTimelineForDate(date: LocalDate) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val startOfDay = date.atStartOfDay()
                val endOfDay = date.plusDays(1).atStartOfDay()
                
                // Get visits for the day
                val visits = visitRepository.getVisitsBetween(startOfDay, endOfDay).first()
                
                // Get all places
                val places = placeRepository.getAllPlaces().first()
                val placesMap = places.associateBy { it.id }
                
                // Get locations for the day
                val locations = locationRepository.getLocationsSince(startOfDay)
                    .filter { it.timestamp.isBefore(endOfDay) }
                
                // Generate day analytics
                val dayAnalytics = analyticsUseCases.generateDayAnalytics(date)
                
                // Build timeline entries
                val timelineEntries = buildTimelineEntries(visits, placesMap, locations, date)
                
                _uiState.value = _uiState.value.copy(
                    timelineEntries = timelineEntries,
                    dayAnalytics = dayAnalytics,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load timeline: ${e.message}"
                )
            }
        }
    }
    
    private fun buildTimelineEntries(
        visits: List<Visit>,
        placesMap: Map<Long, Place>,
        locations: List<Location>,
        date: LocalDate
    ): List<TimelineEntry> {
        val entries = mutableListOf<TimelineEntry>()
        
        // Add day summary at the beginning
        entries.add(
            TimelineEntry(
                id = "day_summary_$date",
                type = TimelineEntryType.DAY_SUMMARY,
                timestamp = date.atStartOfDay(),
                title = "Day Summary",
                subtitle = "${visits.size} places visited"
            )
        )
        
        // Process visits
        visits.forEach { visit ->
            val place = placesMap[visit.placeId]
            
            // Visit start
            entries.add(
                TimelineEntry(
                    id = "visit_start_${visit.id}",
                    type = TimelineEntryType.VISIT_START,
                    timestamp = visit.entryTime,
                    place = place,
                    visit = visit,
                    title = "Arrived at ${place?.name ?: "Unknown Place"}",
                    subtitle = formatTime(visit.entryTime)
                )
            )
            
            // Visit end (if available)
            visit.exitTime?.let { exitTime ->
                entries.add(
                    TimelineEntry(
                        id = "visit_end_${visit.id}",
                        type = TimelineEntryType.VISIT_END,
                        timestamp = exitTime,
                        place = place,
                        visit = visit,
                        title = "Left ${place?.name ?: "Unknown Place"}",
                        subtitle = "${formatTime(exitTime)} • ${formatDuration(visit.duration)}",
                        duration = visit.duration
                    )
                )
            }
        }
        
        // Add movement indicators between visits
        addMovementEntries(entries, locations)
        
        // Sort by timestamp
        return entries.sortedBy { it.timestamp }
    }
    
    private fun addMovementEntries(entries: MutableList<TimelineEntry>, locations: List<Location>) {
        val visitTimes = entries.filter { 
            it.type == TimelineEntryType.VISIT_START || it.type == TimelineEntryType.VISIT_END 
        }.map { it.timestamp }.sorted()
        
        for (i in 0 until visitTimes.size - 1) {
            val startTime = visitTimes[i]
            val endTime = visitTimes[i + 1]
            
            val movementLocations = locations.filter { 
                it.timestamp.isAfter(startTime) && it.timestamp.isBefore(endTime) 
            }
            
            if (movementLocations.isNotEmpty()) {
                val midTime = startTime.plusSeconds(
                    java.time.Duration.between(startTime, endTime).seconds / 2
                )
                
                entries.add(
                    TimelineEntry(
                        id = "movement_${startTime.toEpochSecond(java.time.ZoneOffset.UTC)}",
                        type = TimelineEntryType.MOVEMENT,
                        timestamp = midTime,
                        title = "Traveling",
                        subtitle = "${movementLocations.size} location updates"
                    )
                )
            }
        }
    }
    
    private fun formatTime(dateTime: LocalDateTime): String {
        return "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }
    
    fun refreshTimeline() {
        loadTimelineForDate(_uiState.value.selectedDate)
    }
}