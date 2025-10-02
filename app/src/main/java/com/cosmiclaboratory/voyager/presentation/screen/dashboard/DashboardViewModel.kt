package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalLocations: Int = 0,
    val totalPlaces: Int = 0,
    val totalTimeTracked: Long = 0L,
    val isTracking: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val locationServiceManager: LocationServiceManager,
    private val analyticsUseCases: AnalyticsUseCases,
    private val locationUseCases: LocationUseCases
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
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
                // Get total location count using use cases
                val locationCount = locationUseCases.getTotalLocationCount()
                
                // Get all places count
                val totalPlaces = placeRepository.getAllPlaces().first().size
                
                // Calculate real analytics for today
                val today = java.time.LocalDate.now()
                val dayAnalytics = analyticsUseCases.generateDayAnalytics(today)
                
                _uiState.value = _uiState.value.copy(
                    totalLocations = locationCount,
                    totalPlaces = totalPlaces,
                    totalTimeTracked = dayAnalytics.totalTimeTracked,
                    isTracking = locationServiceManager.isLocationServiceRunning(),
                    isLoading = false
                )
            } catch (e: Exception) {
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
                kotlinx.coroutines.delay(30000) // Refresh every 30 seconds
                if (_uiState.value.isTracking) {
                    loadDashboardData()
                }
            }
        }
    }
    
    fun refreshData() {
        loadDashboardData()
    }
}