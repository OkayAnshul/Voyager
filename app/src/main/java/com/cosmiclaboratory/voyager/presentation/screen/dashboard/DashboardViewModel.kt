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
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DashboardUiState(
    val totalLocations: Int = 0,
    val totalPlaces: Int = 0,
    val totalTimeTracked: Long = 0L,
    val isTracking: Boolean = false,
    val isLoading: Boolean = true,
    val isDetectingPlaces: Boolean = false,
    // Real-time state fields
    val currentPlace: com.cosmiclaboratory.voyager.domain.model.Place? = null,
    val isAtPlace: Boolean = false,
    val currentVisitDuration: Long = 0L,
    val isLocationTrackingActive: Boolean = false
)

private data class DashboardData(
    val locationCount: Int,
    val totalPlaces: Int,
    val dayAnalytics: com.cosmiclaboratory.voyager.domain.model.DayAnalytics,
    val currentStateAnalytics: com.cosmiclaboratory.voyager.domain.model.CurrentStateAnalytics
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val locationServiceManager: LocationServiceManager,
    private val analyticsUseCases: AnalyticsUseCases,
    private val locationUseCases: LocationUseCases,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    // Cache analytics to prevent excessive recalculation
    private var cachedAnalytics: com.cosmiclaboratory.voyager.domain.model.DayAnalytics? = null
    private var lastAnalyticsUpdate: Long = 0
    private val analyticsCache_TimeoutMs = 30_000L // 30 seconds cache
    
    init {
        loadDashboardData()
        observeServiceStatus()
        startPeriodicRefresh()
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
                // Run heavy database operations on IO dispatcher to avoid blocking main thread
                val dashboardData = withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    
                    // Get total location count using use cases
                    val locationCount = locationUseCases.getTotalLocationCount()
                    android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Location count = $locationCount")
                    
                    // Get all places count
                    val totalPlaces = placeRepository.getAllPlaces().first().size
                    android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Total places = $totalPlaces")
                    
                    // Get real-time state analytics (no caching for real-time data)
                    val currentStateAnalytics = analyticsUseCases.getCurrentStateAnalytics()
                    android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Current state analytics - tracking=${currentStateAnalytics.isLocationTrackingActive}, todayTime=${currentStateAnalytics.todayTimeTracked}")
                    
                    // Use cached day analytics if available and not expired
                    val currentTime = System.currentTimeMillis()
                    val dayAnalytics = if (cachedAnalytics != null && 
                        (currentTime - lastAnalyticsUpdate) < analyticsCache_TimeoutMs) {
                        android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Using cached day analytics - totalTime=${cachedAnalytics!!.totalTimeTracked}")
                        cachedAnalytics!!
                    } else {
                        android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Generating fresh day analytics")
                        val today = java.time.LocalDate.now()
                        val analytics = analyticsUseCases.generateDayAnalytics(today)
                        android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Fresh analytics generated - totalTime=${analytics.totalTimeTracked}")
                        // Update cache
                        cachedAnalytics = analytics
                        lastAnalyticsUpdate = currentTime
                        analytics
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.d("DashboardViewModel", "CRITICAL DEBUG: Dashboard data loaded in ${duration}ms")
                    
                    // Return both analytics
                    DashboardData(locationCount, totalPlaces, dayAnalytics, currentStateAnalytics)
                }
                
                // Update UI on main thread - combine both day analytics and real-time state
                val combinedTimeTracked = maxOf(
                    dashboardData.dayAnalytics.totalTimeTracked,
                    dashboardData.currentStateAnalytics.todayTimeTracked
                )
                
                _uiState.value = _uiState.value.copy(
                    totalLocations = dashboardData.locationCount,
                    totalPlaces = dashboardData.totalPlaces,
                    totalTimeTracked = combinedTimeTracked,
                    isTracking = locationServiceManager.isLocationServiceRunning(),
                    isLoading = false,
                    // Real-time state updates
                    currentPlace = dashboardData.currentStateAnalytics.currentPlace,
                    isAtPlace = dashboardData.currentStateAnalytics.isAtPlace,
                    currentVisitDuration = dashboardData.currentStateAnalytics.currentVisitDuration,
                    isLocationTrackingActive = dashboardData.currentStateAnalytics.isLocationTrackingActive
                )
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Failed to load dashboard data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
            }
        }
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
    
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                // Dynamic refresh interval based on activity
                val refreshInterval = if (_uiState.value.isAtPlace) {
                    30000L // 30 seconds when user is at a place (active visit)
                } else if (_uiState.value.isTracking) {
                    60000L // 60 seconds when tracking but not at a place
                } else {
                    120000L // 2 minutes when not tracking
                }
                
                kotlinx.coroutines.delay(refreshInterval)
                
                if (_uiState.value.isTracking) {
                    // Always refresh real-time state, but respect cache for day analytics
                    val currentTime = System.currentTimeMillis()
                    if (_uiState.value.isAtPlace || cachedAnalytics == null || 
                        (currentTime - lastAnalyticsUpdate) > analyticsCache_TimeoutMs) {
                        android.util.Log.d("DashboardViewModel", "Periodic refresh triggered (interval: ${refreshInterval}ms)")
                        loadDashboardData()
                    }
                }
            }
        }
    }
    
    fun refreshData() {
        loadDashboardData()
    }
    
    fun triggerPlaceDetection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isDetectingPlaces = true)
                
                // Get user preferences for the worker
                val preferences = preferencesRepository.getCurrentPreferences()
                
                // Trigger immediate place detection
                val immediateWork = PlaceDetectionWorker.createOneTimeWorkRequest(preferences)
                val workManager = WorkManager.getInstance(context)
                workManager.enqueue(immediateWork)
                
                // Monitor work completion and refresh data
                workManager.getWorkInfoByIdFlow(immediateWork.id).collect { workInfo ->
                    if (workInfo?.state?.isFinished == true) {
                        _uiState.value = _uiState.value.copy(isDetectingPlaces = false)
                        // Refresh dashboard data to show new places
                        loadDashboardData()
                    }
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDetectingPlaces = false)
                // Could show error message here
            }
        }
    }
}