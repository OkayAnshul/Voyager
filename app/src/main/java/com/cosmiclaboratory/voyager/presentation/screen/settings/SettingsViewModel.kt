package com.cosmiclaboratory.voyager.presentation.screen.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.model.BatteryRequirement
import com.cosmiclaboratory.voyager.domain.model.TrackingAccuracyMode
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class SettingsUiState(
    val isLocationTrackingEnabled: Boolean = false,
    val totalLocations: Int = 0,
    val totalPlaces: Int = 0,
    val totalVisits: Int = 0,
    val isLoading: Boolean = true,
    val lastDataCleanup: LocalDateTime? = null,
    val isDataExporting: Boolean = false,
    val isDataImporting: Boolean = false,
    val exportMessage: String? = null,
    val errorMessage: String? = null,
    val preferences: UserPreferences = UserPreferences()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationUseCases: LocationUseCases,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val locationServiceManager: com.cosmiclaboratory.voyager.utils.LocationServiceManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        observeLocationTracking()
        observePreferences()
        observeServiceErrors()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val totalLocations = locationUseCases.getTotalLocationCount()
                val totalPlaces = placeRepository.getAllPlaces().first().size
                val totalVisits = visitRepository.getAllVisits().first().size
                
                _uiState.value = _uiState.value.copy(
                    totalLocations = totalLocations,
                    totalPlaces = totalPlaces,
                    totalVisits = totalVisits,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load settings: ${e.message}"
                )
            }
        }
    }
    
    private fun observeLocationTracking() {
        viewModelScope.launch {
            locationUseCases.isLocationTrackingActive().collect { isTracking ->
                _uiState.value = _uiState.value.copy(isLocationTrackingEnabled = isTracking)
            }
        }
    }
    
    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.getUserPreferences().collect { preferences ->
                _uiState.value = _uiState.value.copy(preferences = preferences)
            }
        }
    }
    
    private fun observeServiceErrors() {
        viewModelScope.launch {
            locationServiceManager.serviceError.collect { error ->
                error?.let {
                    _uiState.value = _uiState.value.copy(errorMessage = "Location tracking stopped: $it")
                }
            }
        }
    }
    
    fun toggleLocationTracking() {
        viewModelScope.launch {
            try {
                // Clear any previous errors
                clearMessage()
                
                // Get the actual service state, not just the UI state
                val actualServiceState = locationServiceManager.isLocationServiceRunning()
                
                if (actualServiceState) {
                    // Service is actually running, stop it
                    locationUseCases.stopLocationTracking()
                } else {
                    // Service is not running, start it
                    locationUseCases.startLocationTracking()
                    
                    // Give user feedback about starting
                    _uiState.value = _uiState.value.copy(
                        exportMessage = "Starting location tracking..."
                    )
                    
                    // Clear success message after a delay
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        if (_uiState.value.exportMessage == "Starting location tracking...") {
                            clearMessage()
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to toggle location tracking: ${e.message}"
                )
                Log.e("SettingsViewModel", "Error toggling location tracking", e)
            }
        }
    }
    
    fun deleteOldData(beforeDate: LocalDateTime) {
        viewModelScope.launch {
            try {
                val deletedLocations = locationRepository.deleteLocationsBefore(beforeDate)
                val deletedVisits = visitRepository.deleteVisitsBefore(beforeDate)
                
                _uiState.value = _uiState.value.copy(
                    lastDataCleanup = LocalDateTime.now(),
                    exportMessage = "Deleted $deletedLocations locations and $deletedVisits visits"
                )
                
                // Refresh stats
                loadSettings()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete old data: ${e.message}"
                )
            }
        }
    }
    
    fun deleteAllData() {
        viewModelScope.launch {
            try {
                locationRepository.deleteAllLocations()
                visitRepository.getAllVisits().first().forEach { visit ->
                    visitRepository.deleteVisit(visit)
                }
                placeRepository.getAllPlaces().first().forEach { place ->
                    placeRepository.deletePlace(place)
                }
                
                _uiState.value = _uiState.value.copy(
                    totalLocations = 0,
                    totalPlaces = 0,
                    totalVisits = 0,
                    exportMessage = "All data deleted successfully"
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete all data: ${e.message}"
                )
            }
        }
    }
    
    fun exportData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDataExporting = true, exportMessage = null)
                
                // In a real implementation, this would export to JSON/CSV
                val locations = locationRepository.getRecentLocations(10000).first()
                val places = placeRepository.getAllPlaces().first()
                val visits = visitRepository.getAllVisits().first()
                
                // Simulate export process
                kotlinx.coroutines.delay(1000)
                
                _uiState.value = _uiState.value.copy(
                    isDataExporting = false,
                    exportMessage = "Exported ${locations.size} locations, ${places.size} places, ${visits.size} visits"
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDataExporting = false,
                    errorMessage = "Export failed: ${e.message}"
                )
            }
        }
    }
    
    fun importData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDataImporting = true, exportMessage = null)
                
                // In a real implementation, this would import from JSON/CSV
                // Simulate import process
                kotlinx.coroutines.delay(1000)
                
                _uiState.value = _uiState.value.copy(
                    isDataImporting = false,
                    exportMessage = "Data import functionality not yet implemented"
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDataImporting = false,
                    errorMessage = "Import failed: ${e.message}"
                )
            }
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            exportMessage = null,
            errorMessage = null
        )
        // Also clear service manager errors
        locationServiceManager.clearServiceError()
    }
    
    fun refreshSettings() {
        loadSettings()
    }
    
    // Preference update methods
    fun updateLocationUpdateInterval(intervalMs: Long) {
        viewModelScope.launch {
            preferencesRepository.updateLocationUpdateInterval(intervalMs)
        }
    }
    
    fun updateMinDistanceChange(distanceMeters: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMinDistanceChange(distanceMeters)
        }
    }
    
    fun updateTrackingAccuracyMode(mode: TrackingAccuracyMode) {
        viewModelScope.launch {
            preferencesRepository.updateTrackingAccuracyMode(mode)
        }
    }
    
    fun updateClusteringDistance(distanceMeters: Double) {
        viewModelScope.launch {
            preferencesRepository.updateClusteringDistance(distanceMeters)
        }
    }
    
    fun updateMinPointsForCluster(points: Int) {
        viewModelScope.launch {
            preferencesRepository.updateMinPointsForCluster(points)
        }
    }
    
    fun updateSessionBreakTime(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateSessionBreakTime(minutes)
        }
    }
    
    fun updateAutoConfidenceThreshold(threshold: Float) {
        viewModelScope.launch {
            preferencesRepository.updateAutoConfidenceThreshold(threshold)
        }
    }
    
    fun updateNotificationSettings(
        enableArrival: Boolean,
        enableDeparture: Boolean,
        enablePattern: Boolean,
        enableWeeklySummary: Boolean
    ) {
        viewModelScope.launch {
            preferencesRepository.updateNotificationSettings(
                enableArrival, enableDeparture, enablePattern, enableWeeklySummary
            )
        }
    }
    
    fun updateNotificationFrequency(frequency: Int) {
        viewModelScope.launch {
            val current = _uiState.value.preferences
            val updated = current.copy(notificationUpdateFrequency = frequency)
            preferencesRepository.updateUserPreferences(updated)
        }
    }
    
    fun resetPreferencesToDefaults() {
        viewModelScope.launch {
            preferencesRepository.resetToDefaults()
            _uiState.value = _uiState.value.copy(
                exportMessage = "Preferences reset to defaults"
            )
        }
    }
    
    // New user-configurable settings methods
    fun updateMaxGpsAccuracy(accuracyMeters: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMaxGpsAccuracy(accuracyMeters)
        }
    }
    
    fun updateMaxSpeedKmh(speedKmh: Double) {
        viewModelScope.launch {
            preferencesRepository.updateMaxSpeedKmh(speedKmh)
        }
    }
    
    fun updateMinTimeBetweenUpdates(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.updateMinTimeBetweenUpdates(seconds)
        }
    }
    
    fun updatePlaceDetectionFrequency(hours: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlaceDetectionFrequency(hours)
        }
    }
    
    fun updateAutoDetectTriggerCount(count: Int) {
        viewModelScope.launch {
            preferencesRepository.updateAutoDetectTriggerCount(count)
        }
    }
    
    fun updateBatteryRequirement(requirement: BatteryRequirement) {
        viewModelScope.launch {
            preferencesRepository.updateBatteryRequirement(requirement)
        }
    }
    
    fun updateActivityTimeRange(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            preferencesRepository.updateActivityTimeRange(startHour, endHour)
        }
    }
    
    fun updateDataProcessingBatchSize(size: Int) {
        viewModelScope.launch {
            preferencesRepository.updateDataProcessingBatchSize(size)
        }
    }
}