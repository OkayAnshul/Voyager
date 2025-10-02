package com.cosmiclaboratory.voyager.presentation.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val locations: List<Location> = emptyList(),
    val places: List<Place> = emptyList(),
    val isTracking: Boolean = false,
    val userLocation: Location? = null,
    val selectedPlace: Place? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mapCenter: Pair<Double, Double>? = null,
    val zoomLevel: Float = 15f
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationUseCases: LocationUseCases,
    private val placeUseCases: PlaceUseCases
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    
    init {
        loadMapData()
        observeLocationTracking()
    }
    
    private fun loadMapData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Get recent locations (last 24 hours)
                val recentLocations = locationUseCases.getRecentLocations(1000)
                
                // Get all places
                placeUseCases.getAllPlaces().collect { places ->
                    val lastLocation = recentLocations.lastOrNull()
                    
                    _uiState.value = _uiState.value.copy(
                        locations = recentLocations.takeLast(100), // Limit for performance
                        places = places,
                        userLocation = lastLocation,
                        mapCenter = lastLocation?.let { it.latitude to it.longitude },
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load map data: ${e.message}"
                )
            }
        }
    }
    
    private fun observeLocationTracking() {
        viewModelScope.launch {
            locationUseCases.isLocationTrackingActive().collect { isTracking ->
                _uiState.value = _uiState.value.copy(isTracking = isTracking)
            }
        }
    }
    
    fun selectPlace(place: Place?) {
        _uiState.value = _uiState.value.copy(
            selectedPlace = place,
            mapCenter = place?.let { it.latitude to it.longitude }
        )
    }
    
    fun centerOnUser() {
        val userLocation = _uiState.value.userLocation
        if (userLocation != null) {
            _uiState.value = _uiState.value.copy(
                mapCenter = userLocation.latitude to userLocation.longitude,
                selectedPlace = null
            )
        }
    }
    
    fun toggleLocationTracking() {
        viewModelScope.launch {
            if (_uiState.value.isTracking) {
                locationUseCases.stopLocationTracking()
            } else {
                locationUseCases.startLocationTracking()
            }
        }
    }
    
    fun refreshMapData() {
        loadMapData()
    }
    
    fun updateMapCenter(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            mapCenter = latitude to longitude
        )
    }
    
    fun updateZoomLevel(zoom: Float) {
        _uiState.value = _uiState.value.copy(
            zoomLevel = zoom
        )
    }
}