package com.cosmiclaboratory.voyager.presentation.screen.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.ExportDataUseCase
import com.cosmiclaboratory.voyager.domain.usecase.ExportFormat
import com.cosmiclaboratory.voyager.domain.usecase.ExportResult
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
    private val exportDataUseCase: ExportDataUseCase,
    private val locationServiceManager: com.cosmiclaboratory.voyager.utils.LocationServiceManager,
    private val sleepScheduleManager: com.cosmiclaboratory.voyager.utils.SleepScheduleManager,
    private val motionDetectionManager: com.cosmiclaboratory.voyager.utils.MotionDetectionManager,
    private val workManagerHelper: com.cosmiclaboratory.voyager.utils.WorkManagerHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        observeLocationTracking()
        observePreferences()
        observeServiceErrors()
        initializeDailySummaryIfEnabled()
    }

    /**
     * Initialize DailySummaryWorker on app start
     * Session #6 Bug Fix - DailySummaryWorker exists but was never scheduled
     */
    private fun initializeDailySummaryIfEnabled() {
        viewModelScope.launch {
            try {
                // Schedule daily summary notifications (runs at 9 PM daily)
                initializeDailySummaryWorker()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to initialize daily summary", e)
            }
        }
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

    /**
     * Initialize DailySummaryWorker for daily summary notifications
     * Session #6 Bug Fix - This worker was implemented but never scheduled
     */
    private fun initializeDailySummaryWorker() {
        viewModelScope.launch {
            try {
                val success = workManagerHelper.scheduleDailySummary(hour = 21) // 9 PM
                if (success) {
                    android.util.Log.d("SettingsViewModel", "DailySummaryWorker scheduled successfully for 9 PM daily")
                } else {
                    android.util.Log.w("SettingsViewModel", "DailySummaryWorker scheduling returned false")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to schedule DailySummaryWorker", e)
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
    
    /**
     * Export data to JSON format (default)
     */
    fun exportData() {
        exportData(ExportFormat.JSON)
    }

    /**
     * Export data in specified format
     */
    fun exportData(format: ExportFormat) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDataExporting = true, exportMessage = null, errorMessage = null)

                val result = exportDataUseCase(format, limit = 10000)

                when (result) {
                    is ExportResult.Success -> {
                        val sizeInKb = result.fileSize / 1024
                        _uiState.value = _uiState.value.copy(
                            isDataExporting = false,
                            exportMessage = "Export successful!\nSaved to: ${result.filePath}\nSize: ${sizeInKb}KB"
                        )
                        Log.d("SettingsViewModel", "Export successful: ${result.filePath}")
                    }
                    is ExportResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isDataExporting = false,
                            errorMessage = "Export failed: ${result.message}"
                        )
                        Log.e("SettingsViewModel", "Export failed: ${result.message}")
                    }
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDataExporting = false,
                    errorMessage = "Export failed: ${e.message}"
                )
                Log.e("SettingsViewModel", "Export exception", e)
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

    // Sleep Schedule settings (Phase 8.1)
    fun updateSleepModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSleepModeEnabled(enabled)
        }
    }

    fun updateSleepStartHour(hour: Int) {
        viewModelScope.launch {
            preferencesRepository.updateSleepStartHour(hour)
        }
    }

    fun updateSleepEndHour(hour: Int) {
        viewModelScope.launch {
            preferencesRepository.updateSleepEndHour(hour)
        }
    }

    fun getSleepScheduleDisplay(preferences: UserPreferences): String {
        return sleepScheduleManager.formatSleepSchedule(preferences)
    }

    fun getEstimatedBatterySavings(preferences: UserPreferences): Int {
        return sleepScheduleManager.estimateBatterySavings(preferences)
    }

    // Motion Detection settings (Phase 8.4)
    fun updateMotionDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateMotionDetectionEnabled(enabled)
        }
    }

    fun updateMotionSensitivity(threshold: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMotionSensitivityThreshold(threshold)
        }
    }

    fun isMotionDetectionAvailable(): Boolean {
        // Delegate to MotionDetectionManager to check availability
        return motionDetectionManager.isMotionDetectionAvailable()
    }

    // Phase 2: Activity Recognition
    fun updateActivityRecognition(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateActivityRecognition(enabled)
        }
    }

    // Profile Management
    fun applySettingsProfile(profileName: String) {
        viewModelScope.launch {
            preferencesRepository.applySettingsProfile(profileName)
        }
    }

    fun getCurrentProfile(): com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile {
        val profileName = _uiState.value.preferences.currentProfile
        return try {
            com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile.valueOf(profileName)
        } catch (e: IllegalArgumentException) {
            com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile.CUSTOM
        }
    }

    // Advanced Place Detection Parameters
    fun updateMinimumDistanceBetweenPlaces(meters: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMinimumDistanceBetweenPlaces(meters)
            markProfileAsCustom()
        }
    }

    fun updateStationaryThreshold(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateStationaryThreshold(minutes)
            markProfileAsCustom()
        }
    }

    fun updateStationaryMovementThreshold(meters: Float) {
        viewModelScope.launch {
            preferencesRepository.updateStationaryMovementThreshold(meters)
            markProfileAsCustom()
        }
    }

    // Geocoding Parameters
    fun updateGeocodingCacheDuration(days: Int) {
        viewModelScope.launch {
            preferencesRepository.updateGeocodingCacheDuration(days)
            markProfileAsCustom()
        }
    }

    // UI Refresh Intervals
    fun updateDashboardRefreshIntervals(atPlaceSeconds: Int, trackingSeconds: Int, idleSeconds: Int) {
        viewModelScope.launch {
            preferencesRepository.updateDashboardRefreshIntervals(atPlaceSeconds, trackingSeconds, idleSeconds)
            markProfileAsCustom()
        }
    }

    // Pattern Analysis Parameters
    fun updatePatternAnalysisSettings(minVisits: Int, minConfidence: Float, timeWindowMinutes: Int, analysisDays: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePatternAnalysisSettings(minVisits, minConfidence, timeWindowMinutes, analysisDays)
            markProfileAsCustom()
        }
    }

    // Anomaly Detection Parameters
    fun updateAnomalyDetectionSettings(recentDays: Int, lookbackDays: Int, durationThreshold: Float, timeThresholdHours: Int) {
        viewModelScope.launch {
            preferencesRepository.updateAnomalyDetectionSettings(recentDays, lookbackDays, durationThreshold, timeThresholdHours)
            markProfileAsCustom()
        }
    }

    // Battery & Performance
    fun updateStationaryMultipliers(intervalMultiplier: Float, distanceMultiplier: Float) {
        viewModelScope.launch {
            preferencesRepository.updateStationaryMultipliers(intervalMultiplier, distanceMultiplier)
            markProfileAsCustom()
        }
    }

    fun updateStationaryIntervalMultiplier(multiplier: Float) {
        viewModelScope.launch {
            val currentDistance = _uiState.value.preferences.stationaryDistanceMultiplier
            preferencesRepository.updateStationaryMultipliers(multiplier, currentDistance)
            markProfileAsCustom()
        }
    }

    fun updateAutoDetectBatteryThreshold(threshold: Int) {
        viewModelScope.launch {
            val updatedPreferences = _uiState.value.preferences.copy(
                autoDetectBatteryThreshold = threshold
            )
            preferencesRepository.updateUserPreferences(updatedPreferences)
            markProfileAsCustom()
        }
    }

    fun updateUseActivityRecognition(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateActivityRecognition(enabled)
            markProfileAsCustom()
        }
    }

    fun updateMotionSensitivityThreshold(threshold: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMotionSensitivityThreshold(threshold)
            markProfileAsCustom()
        }
    }

    // Update full preferences (for category detection settings)
    fun updatePreferences(preferences: UserPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateUserPreferences(preferences)
            markProfileAsCustom()
        }
    }

    // Place Review Settings (Week 5)
    fun updateAutoApproveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoApproveEnabled(enabled)
            markProfileAsCustom()
        }
    }

    fun updateAutoApproveThreshold(threshold: Float) {
        viewModelScope.launch {
            preferencesRepository.updateAutoApproveThreshold(threshold)
            markProfileAsCustom()
        }
    }

    fun updateReviewNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateReviewNotificationsEnabled(enabled)
            markProfileAsCustom()
        }
    }

    // Timeline Settings (Phase 3)
    fun updateTimelineTimeWindow(windowMinutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateTimelineTimeWindow(windowMinutes.toLong())
            markProfileAsCustom()
        }
    }

    fun updateTimelineDistanceThreshold(distanceMeters: Double) {
        viewModelScope.launch {
            preferencesRepository.updateTimelineDistanceThreshold(distanceMeters)
            markProfileAsCustom()
        }
    }

    // ISSUE #5: All update methods already exist above

    /**
     * Mark the current profile as CUSTOM when user modifies individual parameters
     */
    private suspend fun markProfileAsCustom() {
        val currentProfile = preferencesRepository.getCurrentProfileName()
        if (currentProfile != "CUSTOM") {
            preferencesRepository.updateCurrentProfile("CUSTOM")
        }
    }
}