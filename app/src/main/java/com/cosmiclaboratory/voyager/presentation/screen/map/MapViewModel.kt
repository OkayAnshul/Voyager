package com.cosmiclaboratory.voyager.presentation.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases
import com.cosmiclaboratory.voyager.domain.usecase.PlaceUseCases
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import com.cosmiclaboratory.voyager.data.event.StateEventDispatcher
import com.cosmiclaboratory.voyager.data.event.EventListener
import com.cosmiclaboratory.voyager.data.event.StateEvent
import com.cosmiclaboratory.voyager.data.event.EventTypes
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
    val selectedPlaceVisits: List<com.cosmiclaboratory.voyager.domain.model.Visit> = emptyList(),  // ISSUE #4
    val currentPlace: Place? = null,
    val isAtPlace: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mapCenter: Pair<Double, Double>? = null,
    val zoomLevel: Float = 15f
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationUseCases: LocationUseCases,
    private val placeUseCases: PlaceUseCases,
    private val placeRepository: PlaceRepository,
    private val visitRepository: com.cosmiclaboratory.voyager.domain.repository.VisitRepository,  // ISSUE #4
    private val appStateManager: AppStateManager,
    private val logger: ProductionLogger,
    private val eventDispatcher: StateEventDispatcher
) : ViewModel(), EventListener {
    
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    
    init {
        loadMapData()
        observeAppState()
        observeLocationTracking()
        registerForEvents()
    }
    
    private fun loadMapData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Get recent locations (last 24 hours)
                val recentLocations = locationUseCases.getRecentLocations(1000)
                
                // Get all places (get current data, not continuous stream for loading)
                val places = placeUseCases.getAllPlaces().first()
                
                val lastLocation = recentLocations.lastOrNull()
                
                _uiState.value = _uiState.value.copy(
                    locations = recentLocations.takeLast(100), // Limit for performance
                    places = places,
                    userLocation = lastLocation,
                    mapCenter = lastLocation?.let { it.latitude to it.longitude },
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load map data: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Observe centralized app state for real-time map updates
     */
    private fun observeAppState() {
        viewModelScope.launch {
            appStateManager.appState.collect { appState ->
                logger.d("MapViewModel", "App state updated: tracking=${appState.locationTracking.isActive}, placeId=${appState.currentPlace?.placeId}")
                
                // Update tracking status from centralized state
                _uiState.value = _uiState.value.copy(
                    isTracking = appState.locationTracking.isActive,
                    isAtPlace = appState.currentPlace != null
                )
                
                // Load current place details if we have a place ID
                appState.currentPlace?.let { placeState ->
                    loadCurrentPlaceForMap(placeState.placeId)
                }
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
    
    /**
     * Load current place details for map display
     */
    private fun loadCurrentPlaceForMap(placeId: Long) {
        viewModelScope.launch {
            try {
                val place = placeRepository.getPlaceById(placeId)
                place?.let {
                    _uiState.value = _uiState.value.copy(
                        currentPlace = it
                    )
                    logger.d("MapViewModel", "Loaded current place for map: ${it.name}")
                }
            } catch (e: Exception) {
                logger.e("MapViewModel", "Failed to load current place for map: $placeId", e)
            }
        }
    }
    
    fun selectPlace(place: Place?) {
        _uiState.value = _uiState.value.copy(
            selectedPlace = place,
            selectedPlaceVisits = emptyList(),  // Clear previous visits
            mapCenter = place?.let { it.latitude to it.longitude }
        )

        // ISSUE #4: Fetch visits for the selected place
        place?.let {
            viewModelScope.launch {
                try {
                    val visits = visitRepository.getVisitsForPlace(it.id).first()
                    _uiState.value = _uiState.value.copy(
                        selectedPlaceVisits = visits
                    )
                } catch (e: Exception) {
                    logger.e("MapViewModel", "Failed to load visits for place ${it.id}", e)
                }
            }
        }
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
    
    fun onMapClick(latitude: Double, longitude: Double) {
        logger.d("MapViewModel", "Map clicked at: $latitude, $longitude")
        // Deselect any selected place when map is clicked
        _uiState.value = _uiState.value.copy(
            selectedPlace = null
        )
    }
    
    fun onMapReady(mapView: org.osmdroid.views.MapView) {
        logger.d("MapViewModel", "Map is ready and initialized")
        // Map is ready, could add additional setup here if needed
    }
    
    /**
     * Register for event-driven updates
     */
    private fun registerForEvents() {
        // Register as event listener for real-time map updates
        eventDispatcher.registerListener("MapViewModel", this)
        
        // Observe location events for real-time location updates
        viewModelScope.launch {
            eventDispatcher.locationEvents.collect { locationEvent ->
                logger.d("MapViewModel", "Location event received: ${locationEvent.type}")
                when (locationEvent.type) {
                    EventTypes.LOCATION_UPDATE -> {
                        // Update user location on map in real-time
                        val newLocation = com.cosmiclaboratory.voyager.domain.model.Location(
                            latitude = locationEvent.latitude,
                            longitude = locationEvent.longitude,
                            timestamp = locationEvent.timestamp,
                            accuracy = locationEvent.accuracy,
                            speed = locationEvent.speed
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            userLocation = newLocation,
                            // Auto-center on user if no place is selected
                            mapCenter = if (_uiState.value.selectedPlace == null) {
                                newLocation.latitude to newLocation.longitude
                            } else _uiState.value.mapCenter
                        )
                    }
                }
            }
        }
        
        // Observe place events for map updates
        viewModelScope.launch {
            eventDispatcher.placeEvents.collect { placeEvent ->
                logger.d("MapViewModel", "Place event received: ${placeEvent.type}")
                when (placeEvent.type) {
                    EventTypes.PLACE_DETECTED -> {
                        // Refresh places when new place is detected
                        refreshMapData()
                    }
                    EventTypes.PLACE_ENTERED -> {
                        // Highlight current place on map
                        loadCurrentPlaceForMap(placeEvent.placeId)
                        // Center map on current place if not manually moved
                        if (_uiState.value.selectedPlace == null) {
                            viewModelScope.launch {
                                val place = placeRepository.getPlaceById(placeEvent.placeId)
                                place?.let {
                                    _uiState.value = _uiState.value.copy(
                                        mapCenter = it.latitude to it.longitude
                                    )
                                }
                            }
                        }
                    }
                    EventTypes.PLACE_EXITED -> {
                        // Remove current place highlight
                        _uiState.value = _uiState.value.copy(
                            currentPlace = null
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Handle incoming state events
     */
    override suspend fun onStateEvent(event: StateEvent) {
        try {
            logger.d("MapViewModel", "State event received: ${event.type}")
            
            when (event.type) {
                EventTypes.LOCATION_UPDATE -> {
                    // Location updates are handled in the event stream above
                }
                EventTypes.PLACE_DETECTED -> {
                    // Refresh places list when new place is detected
                    refreshMapData()
                }
                EventTypes.TRACKING_STARTED, EventTypes.TRACKING_STOPPED -> {
                    // Update tracking status indicator on map
                    val appState = appStateManager.getCurrentState()
                    _uiState.value = _uiState.value.copy(
                        isTracking = appState.locationTracking.isActive
                    )
                }
            }
        } catch (e: Exception) {
            logger.e("MapViewModel", "Error handling state event: ${event.type}", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Unregister from event dispatcher when ViewModel is cleared
        eventDispatcher.unregisterListener("MapViewModel")
    }
}