package com.cosmiclaboratory.voyager.data.sync

import android.util.Log
import com.cosmiclaboratory.voyager.data.event.*
import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State Synchronizer - Handles event-driven synchronization across repositories
 * CRITICAL: Ensures all components stay in sync when state changes occur
 */
@Singleton
class StateSynchronizer @Inject constructor(
    private val appStateManager: AppStateManager,
    private val eventDispatcher: StateEventDispatcher,
    private val currentStateRepository: CurrentStateRepository,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository
) : EventListener {
    
    companion object {
        private const val TAG = "StateSynchronizer"
    }
    
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false
    
    /**
     * Initialize synchronization system
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "StateSynchronizer already initialized")
            return
        }
        
        // Register as event listener
        eventDispatcher.registerListener("StateSynchronizer", this)
        
        // Start monitoring state changes
        startStateMonitoring()
        
        isInitialized = true
        Log.d(TAG, "StateSynchronizer initialized successfully")
    }
    
    /**
     * Handle incoming state events
     */
    override suspend fun onStateEvent(event: StateEvent) {
        try {
            when (event) {
                is TrackingEvent -> handleTrackingEvent(event)
                is PlaceEvent -> handlePlaceEvent(event)
                is VisitEvent -> handleVisitEvent(event)
                is LocationEvent -> handleLocationEvent(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling state event: ${event.type}", e)
        }
    }
    
    /**
     * Handle tracking status events
     */
    private suspend fun handleTrackingEvent(event: TrackingEvent) {
        Log.d(TAG, "Handling tracking event: ${event.type}")
        
        when (event.type) {
            EventTypes.TRACKING_STARTED -> {
                // Ensure database state matches app state
                syncScope.launch {
                    try {
                        val currentState = appStateManager.getCurrentState()
                        currentStateRepository.updateTrackingStatus(
                            isActive = true,
                            startTime = currentState.locationTracking.startTime
                        )
                        Log.d(TAG, "Database synchronized with tracking start")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on tracking start", e)
                    }
                }
            }
            
            EventTypes.TRACKING_STOPPED -> {
                // Ensure database state matches app state
                syncScope.launch {
                    try {
                        currentStateRepository.updateTrackingStatus(
                            isActive = false,
                            startTime = null
                        )
                        Log.d(TAG, "Database synchronized with tracking stop")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on tracking stop", e)
                    }
                }
            }
        }
    }
    
    /**
     * Handle place-related events
     */
    private suspend fun handlePlaceEvent(event: PlaceEvent) {
        Log.d(TAG, "Handling place event: ${event.type} for place ${event.placeId}")
        
        when (event.action) {
            PlaceAction.ENTERED -> {
                // Update current state in database
                syncScope.launch {
                    try {
                        val currentState = appStateManager.getCurrentState()
                        currentStateRepository.updateCurrentPlace(
                            placeId = event.placeId,
                            visitId = currentState.currentPlace?.visitId,
                            entryTime = currentState.currentPlace?.entryTime
                        )
                        Log.d(TAG, "Database synchronized with place entry: ${event.placeId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on place entry", e)
                    }
                }
            }
            
            PlaceAction.EXITED -> {
                // Clear current place in database
                syncScope.launch {
                    try {
                        currentStateRepository.updateCurrentPlace(
                            placeId = null,
                            visitId = null,
                            entryTime = null
                        )
                        Log.d(TAG, "Database synchronized with place exit: ${event.placeId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on place exit", e)
                    }
                }
            }
            
            PlaceAction.UPDATED -> {
                // Refresh place data if needed
                syncScope.launch {
                    try {
                        // Trigger place data refresh in repositories if needed
                        Log.d(TAG, "Place updated: ${event.placeId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to handle place update", e)
                    }
                }
            }
            
            else -> {
                Log.d(TAG, "Unhandled place action: ${event.action}")
            }
        }
    }
    
    /**
     * Handle visit-related events
     */
    private suspend fun handleVisitEvent(event: VisitEvent) {
        Log.d(TAG, "Handling visit event: ${event.type} for visit ${event.visitId}")
        
        when (event.action) {
            VisitAction.STARTED -> {
                // Ensure current state is updated
                syncScope.launch {
                    try {
                        val currentState = appStateManager.getCurrentState()
                        if (currentState.currentPlace?.visitId != event.visitId) {
                            // Update current state to reflect new visit
                            currentStateRepository.updateCurrentPlace(
                                placeId = event.placeId,
                                visitId = event.visitId,
                                entryTime = event.entryTime
                            )
                        }
                        Log.d(TAG, "Database synchronized with visit start: ${event.visitId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on visit start", e)
                    }
                }
            }
            
            VisitAction.ENDED -> {
                // Clear current visit if it matches
                syncScope.launch {
                    try {
                        val currentState = appStateManager.getCurrentState()
                        if (currentState.currentPlace?.visitId == event.visitId) {
                            currentStateRepository.updateCurrentPlace(
                                placeId = null,
                                visitId = null,
                                entryTime = null
                            )
                        }
                        Log.d(TAG, "Database synchronized with visit end: ${event.visitId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on visit end", e)
                    }
                }
            }
            
            else -> {
                Log.d(TAG, "Unhandled visit action: ${event.action}")
            }
        }
    }
    
    /**
     * Handle location events
     */
    private suspend fun handleLocationEvent(event: LocationEvent) {
        Log.d(TAG, "Handling location event: ${event.type}")
        
        when (event.type) {
            EventTypes.LOCATION_UPDATE -> {
                // Update last location update time
                syncScope.launch {
                    try {
                        currentStateRepository.updateLastLocationTime()
                        Log.d(TAG, "Database synchronized with location update")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync database on location update", e)
                    }
                }
            }
            
            EventTypes.LOCATION_LOST -> {
                // Handle location loss if needed
                Log.d(TAG, "Location signal lost")
            }
        }
    }
    
    /**
     * Start monitoring app state changes
     */
    private fun startStateMonitoring() {
        syncScope.launch {
            appStateManager.appState
                .collect { appState ->
                    try {
                        // Detect state changes and ensure database consistency
                        Log.d(TAG, "App state changed - version: ${appState.stateVersion}")
                        
                        // Perform periodic consistency checks
                        performConsistencyCheck(appState)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during state monitoring", e)
                    }
                }
        }
    }
    
    /**
     * Perform consistency check between app state and database
     */
    private suspend fun performConsistencyCheck(appState: com.cosmiclaboratory.voyager.data.state.AppState) {
        try {
            val dbState = currentStateRepository.getCurrentState().first()
            
            // Check tracking status consistency
            if (dbState?.isLocationTrackingActive != appState.locationTracking.isActive) {
                Log.w(TAG, "CONSISTENCY WARNING: Tracking status mismatch - " +
                      "app=${appState.locationTracking.isActive}, db=${dbState?.isLocationTrackingActive}")
                
                // Sync database to match app state
                currentStateRepository.updateTrackingStatus(
                    isActive = appState.locationTracking.isActive,
                    startTime = appState.locationTracking.startTime
                )
            }
            
            // Check current place consistency
            val appPlaceId = appState.currentPlace?.placeId
            val dbPlaceId = dbState?.currentPlace?.id
            
            if (dbPlaceId != appPlaceId) {
                Log.w(TAG, "CONSISTENCY WARNING: Current place mismatch - " +
                      "app=$appPlaceId, db=$dbPlaceId")
                
                // Sync database to match app state
                currentStateRepository.updateCurrentPlace(
                    placeId = appPlaceId,
                    visitId = appState.currentPlace?.visitId,
                    entryTime = appState.currentPlace?.entryTime
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during consistency check", e)
        }
    }
    
    /**
     * Shutdown synchronizer
     */
    fun shutdown() {
        if (!isInitialized) return
        
        eventDispatcher.unregisterListener("StateSynchronizer")
        syncScope.cancel()
        isInitialized = false
        
        Log.d(TAG, "StateSynchronizer shutdown complete")
    }
}