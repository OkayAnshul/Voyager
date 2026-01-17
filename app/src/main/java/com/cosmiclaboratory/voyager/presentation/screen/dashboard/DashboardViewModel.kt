package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.data.event.StateEventDispatcher
import com.cosmiclaboratory.voyager.data.event.EventListener
import com.cosmiclaboratory.voyager.data.event.StateEvent
import com.cosmiclaboratory.voyager.data.event.EventTypes
import com.cosmiclaboratory.voyager.utils.WorkManagerHelper
import com.cosmiclaboratory.voyager.utils.EnqueueResult
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class DashboardUiState(
    val totalLocations: Int = 0,
    val totalPlaces: Int = 0,
    val totalTimeTracked: Long = 0L,
    val isTracking: Boolean = false,
    val isLoading: Boolean = true,
    val isDetectingPlaces: Boolean = false,
    val errorMessage: String? = null,
    // Real-time state fields
    val currentPlace: com.cosmiclaboratory.voyager.domain.model.Place? = null,
    val isAtPlace: Boolean = false,
    val currentVisitDuration: Long = 0L,
    val isLocationTrackingActive: Boolean = false,
    // Review System - Phase 1 UX Enhancement
    val pendingReviewCount: Int = 0,
    val reviewPriorityBreakdown: Map<com.cosmiclaboratory.voyager.domain.model.ReviewPriority, Int> = emptyMap()
)

private data class DashboardData(
    val locationCount: Int,
    val totalPlaces: Int,
    val dayAnalytics: com.cosmiclaboratory.voyager.domain.model.DayAnalytics,
    val currentStateAnalytics: com.cosmiclaboratory.voyager.domain.model.CurrentStateAnalytics?
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val locationServiceManager: LocationServiceManager,
    private val analyticsUseCases: AnalyticsUseCases,
    private val locationUseCases: LocationUseCases,
    private val placeDetectionUseCases: PlaceDetectionUseCases,
    private val preferencesRepository: PreferencesRepository,
    private val placeReviewRepository: com.cosmiclaboratory.voyager.domain.repository.PlaceReviewRepository,
    private val logger: ProductionLogger,
    private val appStateManager: AppStateManager,
    private val eventDispatcher: StateEventDispatcher,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel(), EventListener {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    // Cache analytics to prevent excessive recalculation
    private var cachedAnalytics: com.cosmiclaboratory.voyager.domain.model.DayAnalytics? = null
    private var lastAnalyticsUpdate: Long = 0
    private val analyticsCache_TimeoutMs = 30_000L // 30 seconds cache
    
    init {
        loadDashboardData()
        observeAppState()
        observeServiceStatus()
        // REMOVED: startPeriodicRefresh() - replaced with event-driven updates
        // See onStateEvent() for reactive updates when data actually changes
        registerForEvents()
    }
    
    /**
     * Observe centralized app state for real-time updates
     */
    private fun observeAppState() {
        viewModelScope.launch {
            appStateManager.appState.collect { appState ->
                logger.d("DashboardViewModel", "App state updated: tracking=${appState.locationTracking.isActive}, placeId=${appState.currentPlace?.placeId}")
                
                // Calculate visit duration for current place
                val visitDuration = appState.currentPlace?.let { placeState ->
                    placeState.entryTime?.let { entryTime ->
                        java.time.Duration.between(entryTime, java.time.LocalDateTime.now()).toMillis()
                    } ?: 0L
                } ?: 0L
                
                // Batch all state updates together for efficiency
                _uiState.value = _uiState.value.copy(
                    isLocationTrackingActive = appState.locationTracking.isActive,
                    isAtPlace = appState.currentPlace != null,
                    totalTimeTracked = appState.dailyStats.timeTracked,
                    totalLocations = appState.dailyStats.locationCount,
                    currentVisitDuration = visitDuration,
                    isTracking = appState.locationTracking.isActive,
                    isLoading = false
                )
                
                // Load current place details only if we don't have them or if place changed
                appState.currentPlace?.let { placeState ->
                    if (_uiState.value.currentPlace?.id != placeState.placeId) {
                        loadCurrentPlaceDetails(placeState.placeId)
                    }
                }
            }
        }
    }

    private fun observeServiceStatus() {
        viewModelScope.launch {
            locationServiceManager.isServiceRunning.collect { isRunning ->
                _uiState.value = _uiState.value.copy(isTracking = isRunning)
            }
        }
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Get current app state first - this is our primary data source
                val currentAppState = appStateManager.getCurrentState()
                logger.d("DashboardViewModel", "Loading dashboard data with app state: tracking=${currentAppState.locationTracking.isActive}")
                
                // Only fetch additional data if app state doesn't have what we need
                val dashboardData = withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    
                    // Get total places count only if not available in app state
                    val totalPlaces = try {
                        placeRepository.getAllPlaces().first().size
                    } catch (e: Exception) {
                        logger.e("DashboardViewModel", "Failed to get places count", e)
                        0
                    }
                    
                    // Use cached analytics with longer timeout for better performance
                    val currentTime = System.currentTimeMillis()
                    val dayAnalytics = if (cachedAnalytics != null && 
                        (currentTime - lastAnalyticsUpdate) < analyticsCache_TimeoutMs) {
                        logger.d("DashboardViewModel", "Using cached day analytics")
                        cachedAnalytics!!
                    } else {
                        logger.d("DashboardViewModel", "Generating fresh day analytics")
                        // CRITICAL FIX: Run heavy analytics generation on IO dispatcher
                        withContext(Dispatchers.IO) {
                            try {
                                val today = java.time.LocalDate.now()
                                val analytics = analyticsUseCases.generateDayAnalytics(today)
                                // Update cache
                                cachedAnalytics = analytics
                                lastAnalyticsUpdate = currentTime
                                analytics
                            } catch (e: Exception) {
                                logger.e("DashboardViewModel", "Failed to generate analytics", e)
                                // Return empty analytics as fallback
                                com.cosmiclaboratory.voyager.domain.model.DayAnalytics(
                                    date = java.time.LocalDate.now(),
                                    placesVisited = 0,
                                    totalTimeTracked = 0L,
                                    distanceTraveled = 0.0,
                                    timeByCategory = emptyMap(),
                                    longestStay = null,
                                    mostFrequentPlace = null
                                )
                            }
                        }
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    logger.perf("DashboardViewModel", "DashboardDataLoad", duration)
                    
                    DashboardData(
                        locationCount = currentAppState.dailyStats.locationCount,
                        totalPlaces = totalPlaces,
                        dayAnalytics = dayAnalytics,
                        currentStateAnalytics = null // Not needed for basic dashboard
                    )
                }
                
                // Get pending review count and priority breakdown (Phase 1 UX)
                val (pendingCount, priorityBreakdown) = try {
                    val reviews = placeReviewRepository.getPendingReviews().first()
                    val count = reviews.size
                    val breakdown = reviews.groupBy { it.priority }
                        .mapValues { it.value.size }
                    count to breakdown
                } catch (e: Exception) {
                    logger.e("DashboardViewModel", "Failed to get pending reviews", e)
                    0 to emptyMap()
                }

                // Update UI state with batch operation for efficiency
                _uiState.value = _uiState.value.copy(
                    totalLocations = dashboardData.locationCount,
                    totalPlaces = dashboardData.totalPlaces,
                    totalTimeTracked = maxOf(currentAppState.dailyStats.timeTracked, dashboardData.dayAnalytics.totalTimeTracked),
                    isTracking = currentAppState.locationTracking.isActive,
                    isLocationTrackingActive = currentAppState.locationTracking.isActive,
                    isAtPlace = currentAppState.currentPlace != null,
                    isLoading = false,
                    pendingReviewCount = pendingCount,
                    reviewPriorityBreakdown = priorityBreakdown
                )
                
                // Load current place details if available
                currentAppState.currentPlace?.let { placeState ->
                    loadCurrentPlaceDetails(placeState.placeId)
                }
                
            } catch (e: Exception) {
                logger.e("DashboardViewModel", "Failed to load dashboard data", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    /**
     * Load details for the current place from app state
     */
    private suspend fun loadCurrentPlaceDetails(placeId: Long) {
        try {
            val place = placeRepository.getPlaceById(placeId)
            place?.let {
                _uiState.value = _uiState.value.copy(
                    currentPlace = it,
                    currentVisitDuration = calculateCurrentVisitDuration()
                )
                logger.d("DashboardViewModel", "Loaded current place details: ${it.name}")
            }
        } catch (e: Exception) {
            logger.e("DashboardViewModel", "Failed to load current place details for ID: $placeId", e)
        }
    }
    
    /**
     * Calculate current visit duration from app state
     */
    private fun calculateCurrentVisitDuration(): Long {
        val currentAppState = appStateManager.getCurrentState()
        return currentAppState.currentPlace?.let { placeState ->
            val entryTime = placeState.entryTime
            val now = java.time.LocalDateTime.now()
            java.time.Duration.between(entryTime, now).toMillis()
        } ?: 0L
    }
    
    fun toggleLocationTracking() {
        viewModelScope.launch {
            val currentState = _uiState.value
            
            if (currentState.isTracking) {
                locationUseCases.stopLocationTracking()
            } else {
                locationUseCases.startLocationTracking()
            }
            // Service status will be updated automatically via observeServiceStatus()
        }
    }
    
    // REMOVED: startPeriodicRefresh() function
    // Periodic refresh caused unnecessary battery drain and recompositions.
    // Now using pure event-driven updates via onStateEvent() and observeAppState().
    // UI updates automatically when:
    // - AppStateManager state changes (observeAppState)
    // - StateEventDispatcher events (onStateEvent)
    // - Service status changes (observeServiceStatus)
    // No polling needed - all updates are reactive and efficient.
    
    fun refreshData() {
        loadDashboardData()
    }
    
    /**
     * Refresh dashboard data - called by pull to refresh
     */
    fun refreshDashboard() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Clear cache to force fresh data
                cachedAnalytics = null
                lastAnalyticsUpdate = 0
                
                // Reload all data
                loadDashboardData()
                
                logger.d("DashboardViewModel", "Dashboard refreshed successfully")
                
            } catch (e: Exception) {
                logger.e("DashboardViewModel", "Failed to refresh dashboard", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun triggerPlaceDetection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDetectingPlaces = true)
                
                // Get user preferences for the worker
                val preferences = preferencesRepository.getCurrentPreferences()
                
                // CRITICAL FIX: Use robust WorkManager enqueuing with verification
                enqueueWorkWithRetry(preferences)
            } catch (e: CancellationException) {
                // Job was cancelled (normal during shutdown/navigation)
                Log.d("DashboardViewModel", "Place detection cancelled (expected during shutdown)")
                _uiState.value = _uiState.value.copy(isDetectingPlaces = false)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to trigger place detection", e)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = "Failed to trigger place detection: ${e.message}"
                )
            }
        }
    }
    
    /**
     * DEBUG: Get current preferences and location stats for troubleshooting
     */
    fun debugGetDiagnosticInfo() {
        viewModelScope.launch {
            try {
                val preferences = preferencesRepository.getCurrentPreferences()
                val locationCount = locationRepository.getRecentLocations(Int.MAX_VALUE).first().size
                val placeCount = placeRepository.getAllPlaces().first().size
                
                val diagnosticInfo = """
                🔧 DIAGNOSTIC INFO:
                📍 Total Locations: $locationCount
                🏠 Total Places: $placeCount
                
                ⚙️ KEY SETTINGS:
                • Place Detection: ${if (preferences.enablePlaceDetection) "✅ ENABLED" else "❌ DISABLED"}
                • Auto Trigger Count: ${preferences.autoDetectTriggerCount} (current: $locationCount)
                • Max GPS Accuracy: ${preferences.maxGpsAccuracyMeters}m
                • Max Speed: ${preferences.maxSpeedKmh} km/h
                • Clustering Distance: ${preferences.clusteringDistanceMeters}m
                • Min Points for Cluster: ${preferences.minPointsForCluster}
                
                🔍 PIPELINE STATUS:
                • Should Auto-Trigger: ${locationCount >= preferences.autoDetectTriggerCount}
                • Detection Ready: ${preferences.enablePlaceDetection && locationCount >= 3}
                """.trimIndent()
                
                Log.i("DashboardViewModel", diagnosticInfo)
                _uiState.value = _uiState.value.copy(errorMessage = diagnosticInfo)
                
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Diagnostic failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "❌ Diagnostic failed: ${e.message}")
            }
        }
    }

    /**
     * DEBUG: Check WorkManager health and initialization status
     */
    fun debugWorkManagerHealth() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDetectingPlaces = true)
                
                Log.i("DashboardViewModel", "Starting WorkManager health check...")
                val healthResult = workManagerHelper.performHealthCheck()
                
                val healthInfo = """
                🔧 WORKMANAGER HEALTH CHECK:
                
                📊 Overall Status: ${if (healthResult.isHealthy) "✅ HEALTHY" else "❌ UNHEALTHY"}
                
                🔍 Individual Checks:
                • Initialization: ${if (healthResult.checks["initialization"] == true) "✅" else "❌"}
                • Instance Creation: ${if (healthResult.checks["instance_creation"] == true) "✅" else "❌"}
                • Hilt Factory: ${if (healthResult.checks["hilt_factory"] == true) "✅" else "❌"}
                • Work Scheduling: ${if (healthResult.checks["work_scheduling"] == true) "✅" else "❌"}
                
                ⚠️ Errors Found:
                ${healthResult.errors.joinToString("\n") { "• $it" }}
                
                🕒 Check Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(healthResult.timestamp))}
                """.trimIndent()
                
                Log.i("DashboardViewModel", healthInfo)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = healthInfo
                )
                
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "WorkManager health check failed", e)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = "❌ Health check failed: ${e.message}"
                )
            }
        }
    }

    /**
     * DEBUG: Reset preferences to fix configuration issues
     */
    fun debugResetPreferences() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDetectingPlaces = true)
                
                Log.i("DashboardViewModel", "Resetting preferences to defaults...")
                preferencesRepository.resetToDefaults()
                
                // Get updated preferences
                val newPreferences = preferencesRepository.getCurrentPreferences()
                
                val resetInfo = """
                🔄 PREFERENCES RESET COMPLETE:
                
                📊 Updated Settings:
                • Place Detection: ${if (newPreferences.enablePlaceDetection) "✅ ENABLED" else "❌ DISABLED"}
                • Auto Trigger Count: ${newPreferences.autoDetectTriggerCount}
                • Max GPS Accuracy: ${newPreferences.maxGpsAccuracyMeters}m
                • Max Speed: ${newPreferences.maxSpeedKmh} km/h
                • Clustering Distance: ${newPreferences.clusteringDistanceMeters}m
                • Min Points for Cluster: ${newPreferences.minPointsForCluster}
                • Battery Requirement: ${newPreferences.batteryRequirement}
                
                ✅ All settings restored to optimal defaults
                """.trimIndent()
                
                Log.i("DashboardViewModel", resetInfo)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = resetInfo
                )
                
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Preferences reset failed", e)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = "❌ Reset failed: ${e.message}"
                )
            }
        }
    }

    /**
     * DEBUG: Manual place detection bypass WorkManager for immediate testing
     */
    fun debugManualPlaceDetection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDetectingPlaces = true)
                
                Log.i("DashboardViewModel", "Starting debug manual place detection...")
                val result = placeDetectionUseCases.debugManualPlaceDetection()
                
                Log.i("DashboardViewModel", "Debug place detection completed: $result")
                
                // Refresh dashboard data to show any new places
                loadDashboardData()
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = "Debug completed: $result"
                )
                
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Debug place detection failed", e)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = "Debug failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Enqueue WorkManager tasks using centralized WorkManagerHelper
     */
    private suspend fun enqueueWorkWithRetry(preferences: UserPreferences) {
        Log.d("DashboardViewModel", "Enqueuing place detection work using WorkManagerHelper...")
        
        val result = workManagerHelper.enqueuePlaceDetectionWork(preferences, isOneTime = true)
        
        when (result) {
            is EnqueueResult.Success -> {
                Log.d("DashboardViewModel", "Place detection work enqueued successfully: ${result.workId}")
                
                // Monitor work completion and refresh data
                val workInfoFlow = workManagerHelper.getWorkInfoFlow(result.workId)
                workInfoFlow?.collect { workInfo ->
                    if (workInfo?.state?.isFinished == true) {
                        _uiState.value = _uiState.value.copy(isDetectingPlaces = false)
                        // Refresh dashboard data to show new places
                        loadDashboardData()
                    }
                } ?: run {
                    // Fallback: Just wait and refresh after a reasonable time
                    kotlinx.coroutines.delay(10000) // Wait 10 seconds
                    _uiState.value = _uiState.value.copy(isDetectingPlaces = false)
                    loadDashboardData()
                }
            }
            is EnqueueResult.Failed -> {
                Log.e("DashboardViewModel", "WorkManager enqueue failed", result.exception)
                _uiState.value = _uiState.value.copy(
                    isDetectingPlaces = false,
                    errorMessage = "Failed to start place detection: ${result.exception.message}"
                )
                
                // FALLBACK: Try manual place detection as last resort
                try {
                    Log.w("DashboardViewModel", "Attempting fallback manual place detection...")
                    val places = placeDetectionUseCases.detectNewPlaces()
                    Log.d("DashboardViewModel", "Fallback manual place detection completed with ${places.size} places")
                    _uiState.value = _uiState.value.copy(
                        isDetectingPlaces = false,
                        errorMessage = null
                    )
                    loadDashboardData() // Refresh to show new places
                } catch (fallbackException: Exception) {
                    Log.e("DashboardViewModel", "Fallback manual place detection also failed", fallbackException)
                }
            }
        }
    }
    
    /**
     * Register for event-driven updates
     */
    private fun registerForEvents() {
        // Register as event listener for real-time updates
        eventDispatcher.registerListener("DashboardViewModel", this)
        
        // Observe specific event streams for reactive UI updates
        viewModelScope.launch {
            eventDispatcher.trackingEvents.collect { trackingEvent ->
                logger.d("DashboardViewModel", "Tracking event received: ${trackingEvent.type}")
                when (trackingEvent.type) {
                    EventTypes.TRACKING_STARTED, EventTypes.TRACKING_STOPPED -> {
                        refreshData() // Refresh dashboard when tracking status changes
                    }
                }
            }
        }
        
        viewModelScope.launch {
            eventDispatcher.placeEvents.collect { placeEvent ->
                logger.d("DashboardViewModel", "Place event received: ${placeEvent.type}")
                when (placeEvent.type) {
                    EventTypes.PLACE_ENTERED, EventTypes.PLACE_EXITED -> {
                        refreshData() // Refresh dashboard when place status changes
                    }
                }
            }
        }
    }
    
    /**
     * Handle incoming state events - ENHANCED for efficiency
     *
     * Granular updates: Only refresh what changed, not the entire state.
     * This eliminates unnecessary recompositions and improves performance.
     */
    override suspend fun onStateEvent(event: StateEvent) {
        try {
            logger.d("DashboardViewModel", "State event: ${event.type}")

            when (event.type) {
                EventTypes.LOCATION_UPDATE -> {
                    // Only update visit duration if at a place, NOT full refresh
                    if (_uiState.value.isAtPlace) {
                        _uiState.value = _uiState.value.copy(
                            currentVisitDuration = calculateCurrentVisitDuration()
                        )
                    }
                }

                EventTypes.PLACE_DETECTED -> {
                    // Full refresh: New place means place count changed
                    loadDashboardData()
                }

                EventTypes.PLACE_ENTERED, EventTypes.PLACE_EXITED -> {
                    // Full refresh: Significant state change
                    loadDashboardData()
                }

                EventTypes.VISIT_STARTED, EventTypes.VISIT_ENDED -> {
                    // Full refresh: Visit stats changed
                    loadDashboardData()
                }

                EventTypes.TRACKING_STARTED, EventTypes.TRACKING_STOPPED -> {
                    // Update tracking status only, NOT full refresh
                    _uiState.value = _uiState.value.copy(
                        isTracking = event.type == EventTypes.TRACKING_STARTED,
                        isLocationTrackingActive = event.type == EventTypes.TRACKING_STARTED
                    )
                }

                else -> {
                    // Unknown event type - log for debugging
                    logger.d("DashboardViewModel", "Unhandled event type: ${event.type}")
                }
            }
        } catch (e: Exception) {
            logger.e("DashboardViewModel", "Error handling event: ${event.type}", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Unregister from event dispatcher when ViewModel is cleared
        eventDispatcher.unregisterListener("DashboardViewModel")
    }
}