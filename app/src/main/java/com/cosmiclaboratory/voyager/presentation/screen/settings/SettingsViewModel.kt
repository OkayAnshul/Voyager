package com.cosmiclaboratory.voyager.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
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
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationUseCases: LocationUseCases,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        observeLocationTracking()
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
    
    fun toggleLocationTracking() {
        viewModelScope.launch {
            if (_uiState.value.isLocationTrackingEnabled) {
                locationUseCases.stopLocationTracking()
            } else {
                locationUseCases.startLocationTracking()
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
    }
    
    fun refreshSettings() {
        loadSettings()
    }
}