package com.cosmiclaboratory.voyager.data.state

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import com.cosmiclaboratory.voyager.data.event.*

/**
 * Unified State Manager - Single Source of Truth for Application State
 * 
 * CRITICAL COMPONENT: Eliminates data inconsistencies by centralizing all state management
 * Resolves race conditions and ensures atomic state transitions
 */
@Singleton
class AppStateManager @Inject constructor(
    private val eventDispatcher: StateEventDispatcher
) {
    
    companion object {
        private const val TAG = "AppStateManager"
    }
    
    // Thread-safe state management with mutex protection
    private val stateMutex = Mutex()
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Centralized app state with proper synchronization
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    // State change observers for event-driven updates
    private val stateChangeObservers = mutableSetOf<StateChangeObserver>()
    
    init {
        // Start state validation monitoring
        startStateValidationMonitoring()
    }
    
    /**
     * Update location tracking status with atomic operations
     * CRITICAL: Ensures no race conditions between service and state updates
     */
    suspend fun updateTrackingStatus(
        isActive: Boolean, 
        startTime: LocalDateTime? = null,
        source: StateUpdateSource = StateUpdateSource.UNKNOWN
    ): StateUpdateResult {
        return stateMutex.withLock {
            try {
                val currentState = _appState.value
                val previousStatus = currentState.locationTracking.isActive
                
                Log.d(TAG, "CRITICAL STATE UPDATE: Tracking status change - " +
                      "from=$previousStatus to=$isActive, source=$source")
                
                // Validate state transition
                val validationResult = validateTrackingStatusTransition(previousStatus, isActive)
                if (!validationResult.isValid) {
                    Log.e(TAG, "CRITICAL ERROR: Invalid tracking status transition - ${validationResult.reason}")
                    return StateUpdateResult.Failed(validationResult.reason)
                }
                
                // Calculate effective start time
                val effectiveStartTime = when {
                    isActive && startTime != null -> startTime
                    isActive && currentState.locationTracking.startTime != null -> currentState.locationTracking.startTime
                    isActive -> LocalDateTime.now()
                    else -> null
                }
                
                // Create new tracking state
                val newTrackingState = currentState.locationTracking.copy(
                    isActive = isActive,
                    startTime = effectiveStartTime,
                    lastStatusChange = LocalDateTime.now(),
                    statusChangeSource = source
                )
                
                // Update app state atomically
                val newAppState = currentState.copy(
                    locationTracking = newTrackingState,
                    lastStateUpdate = LocalDateTime.now(),
                    stateVersion = currentState.stateVersion + 1
                )
                
                _appState.value = newAppState
                
                // Notify observers of state change
                notifyStateChange(StateChangeEvent.TrackingStatusChanged(previousStatus, isActive, source))
                
                // Dispatch tracking event for event-driven synchronization
                stateScope.launch {
                    val trackingEvent = TrackingEvent(
                        type = if (isActive) EventTypes.TRACKING_STARTED else EventTypes.TRACKING_STOPPED,
                        isActive = isActive,
                        reason = "State manager update",
                        source = source.name
                    )
                    eventDispatcher.dispatchTrackingEvent(trackingEvent)
                }
                
                Log.d(TAG, "CRITICAL SUCCESS: Tracking status updated successfully - version=${newAppState.stateVersion}")
                
                StateUpdateResult.Success(newAppState.stateVersion)
                
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR: Failed to update tracking status", e)
                StateUpdateResult.Failed("Exception during state update: ${e.message}")
            }
        }
    }
    
    /**
     * Update current place with proper validation and synchronization
     */
    suspend fun updateCurrentPlace(
        placeId: Long?,
        visitId: Long?,
        entryTime: LocalDateTime?,
        source: StateUpdateSource = StateUpdateSource.UNKNOWN
    ): StateUpdateResult {
        return stateMutex.withLock {
            try {
                val currentState = _appState.value
                val previousPlace = currentState.currentPlace
                
                Log.d(TAG, "CRITICAL STATE UPDATE: Current place change - " +
                      "from=${previousPlace?.placeId} to=$placeId, source=$source")
                
                // Validate place transition
                val validationResult = validatePlaceTransition(previousPlace?.placeId, placeId)
                if (!validationResult.isValid) {
                    Log.e(TAG, "CRITICAL ERROR: Invalid place transition - ${validationResult.reason}")
                    return StateUpdateResult.Failed(validationResult.reason)
                }
                
                // Create new place state
                val newPlaceState = if (placeId != null) {
                    CurrentPlaceState(
                        placeId = placeId,
                        visitId = visitId,
                        entryTime = entryTime ?: LocalDateTime.now(),
                        lastUpdate = LocalDateTime.now()
                    )
                } else {
                    null
                }
                
                // Update app state atomically
                val newAppState = currentState.copy(
                    currentPlace = newPlaceState,
                    lastStateUpdate = LocalDateTime.now(),
                    stateVersion = currentState.stateVersion + 1
                )
                
                _appState.value = newAppState
                
                // Notify observers of state change
                notifyStateChange(StateChangeEvent.CurrentPlaceChanged(previousPlace?.placeId, placeId, source))
                
                // Dispatch place event for event-driven synchronization
                stateScope.launch {
                    when {
                        previousPlace?.placeId == null && placeId != null -> {
                            // Entering a place
                            val placeEvent = PlaceEvent(
                                type = EventTypes.PLACE_ENTERED,
                                placeId = placeId,
                                placeName = "Place $placeId", // TODO: Get actual place name
                                action = PlaceAction.ENTERED
                            )
                            eventDispatcher.dispatchPlaceEvent(placeEvent)
                        }
                        previousPlace?.placeId != null && placeId == null -> {
                            // Exiting a place
                            val placeEvent = PlaceEvent(
                                type = EventTypes.PLACE_EXITED,
                                placeId = previousPlace.placeId,
                                placeName = "Place ${previousPlace.placeId}", // TODO: Get actual place name
                                action = PlaceAction.EXITED
                            )
                            eventDispatcher.dispatchPlaceEvent(placeEvent)
                        }
                        previousPlace?.placeId != placeId && placeId != null -> {
                            // Changing places
                            if (previousPlace?.placeId != null) {
                                val exitEvent = PlaceEvent(
                                    type = EventTypes.PLACE_EXITED,
                                    placeId = previousPlace.placeId,
                                    placeName = "Place ${previousPlace.placeId}",
                                    action = PlaceAction.EXITED
                                )
                                eventDispatcher.dispatchPlaceEvent(exitEvent)
                            }
                            
                            val enterEvent = PlaceEvent(
                                type = EventTypes.PLACE_ENTERED,
                                placeId = placeId,
                                placeName = "Place $placeId",
                                action = PlaceAction.ENTERED
                            )
                            eventDispatcher.dispatchPlaceEvent(enterEvent)
                        }
                    }
                }
                
                Log.d(TAG, "CRITICAL SUCCESS: Current place updated successfully - version=${newAppState.stateVersion}")
                
                StateUpdateResult.Success(newAppState.stateVersion)
                
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR: Failed to update current place", e)
                StateUpdateResult.Failed("Exception during place update: ${e.message}")
            }
        }
    }
    
    /**
     * Update daily statistics with atomic operations
     */
    suspend fun updateDailyStats(
        locationCount: Int,
        placeCount: Int,
        timeTracked: Long,
        source: StateUpdateSource = StateUpdateSource.UNKNOWN
    ): StateUpdateResult {
        return stateMutex.withLock {
            try {
                val currentState = _appState.value
                
                Log.d(TAG, "CRITICAL STATE UPDATE: Daily stats update - " +
                      "locations=$locationCount, places=$placeCount, time=${timeTracked}ms, source=$source")
                
                // Validate statistics
                if (locationCount < 0 || placeCount < 0 || timeTracked < 0) {
                    return StateUpdateResult.Failed("Invalid statistics: negative values not allowed")
                }
                
                // Create new daily stats
                val newDailyStats = DailyStats(
                    date = java.time.LocalDate.now(),
                    locationCount = locationCount,
                    placeCount = placeCount,
                    timeTracked = timeTracked,
                    lastUpdate = LocalDateTime.now()
                )
                
                // Update app state atomically
                val newAppState = currentState.copy(
                    dailyStats = newDailyStats,
                    lastStateUpdate = LocalDateTime.now(),
                    stateVersion = currentState.stateVersion + 1
                )
                
                _appState.value = newAppState
                
                // Notify observers of state change
                notifyStateChange(StateChangeEvent.DailyStatsChanged(newDailyStats, source))
                
                Log.d(TAG, "CRITICAL SUCCESS: Daily stats updated successfully - version=${newAppState.stateVersion}")
                
                StateUpdateResult.Success(newAppState.stateVersion)
                
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR: Failed to update daily stats", e)
                StateUpdateResult.Failed("Exception during stats update: ${e.message}")
            }
        }
    }
    
    /**
     * Get current state synchronously with proper thread safety
     */
    fun getCurrentState(): AppState = _appState.value
    
    /**
     * Register state change observer for event-driven updates
     */
    fun registerStateObserver(observer: StateChangeObserver) {
        stateChangeObservers.add(observer)
        Log.d(TAG, "Registered state observer: ${observer::class.simpleName}")
    }
    
    /**
     * Unregister state change observer
     */
    fun unregisterStateObserver(observer: StateChangeObserver) {
        stateChangeObservers.remove(observer)
        Log.d(TAG, "Unregistered state observer: ${observer::class.simpleName}")
    }
    
    /**
     * Validate state consistency and integrity
     */
    suspend fun validateStateConsistency(): StateValidationResult {
        return stateMutex.withLock {
            val currentState = _appState.value
            val validationIssues = mutableListOf<String>()
            
            // Check tracking state consistency
            if (currentState.locationTracking.isActive && currentState.locationTracking.startTime == null) {
                validationIssues.add("Tracking active but no start time")
            }
            
            // Check place state consistency
            currentState.currentPlace?.let { place ->
                if (place.visitId != null && place.entryTime == null) {
                    validationIssues.add("Visit ID present but no entry time")
                }
            }
            
            // Check daily stats consistency
            val stats = currentState.dailyStats
            if (stats.locationCount > 0 && stats.timeTracked == 0L) {
                validationIssues.add("Locations recorded but no time tracked")
            }
            
            StateValidationResult(
                isValid = validationIssues.isEmpty(),
                issues = validationIssues,
                stateVersion = currentState.stateVersion
            )
        }
    }
    
    /**
     * Force state synchronization with external sources
     */
    suspend fun forceSynchronization(source: StateUpdateSource): StateUpdateResult {
        Log.d(TAG, "CRITICAL: Forcing state synchronization from source=$source")
        
        return try {
            // This will be called by external components to trigger sync
            // Implementation depends on specific sync requirements
            notifyStateChange(StateChangeEvent.ForceSyncRequested(source))
            StateUpdateResult.Success(_appState.value.stateVersion)
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to force synchronization", e)
            StateUpdateResult.Failed("Synchronization failed: ${e.message}")
        }
    }
    
    // Private helper methods
    
    private fun validateTrackingStatusTransition(from: Boolean, to: Boolean): ValidationResult {
        // Allow all transitions but log them for monitoring
        return ValidationResult(
            isValid = true,
            reason = "Transition from $from to $to is valid"
        )
    }
    
    private fun validatePlaceTransition(fromPlaceId: Long?, toPlaceId: Long?): ValidationResult {
        // Basic validation - can be enhanced with business rules
        return ValidationResult(
            isValid = true,
            reason = "Place transition from $fromPlaceId to $toPlaceId is valid"
        )
    }
    
    private fun notifyStateChange(event: StateChangeEvent) {
        stateScope.launch {
            stateChangeObservers.forEach { observer ->
                try {
                    observer.onStateChanged(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying state observer", e)
                }
            }
        }
    }
    
    private fun startStateValidationMonitoring() {
        stateScope.launch {
            while (isActive) {
                try {
                    val validationResult = validateStateConsistency()
                    if (!validationResult.isValid) {
                        Log.w(TAG, "CRITICAL WARNING: State validation failed - issues: ${validationResult.issues}")
                        notifyStateChange(StateChangeEvent.ValidationFailed(validationResult))
                    }
                    
                    delay(30000) // Validate every 30 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in state validation monitoring", e)
                    delay(60000) // Longer delay on error
                }
            }
        }
    }
}

// Data classes for state management

data class AppState(
    val locationTracking: LocationTrackingState = LocationTrackingState(),
    val currentPlace: CurrentPlaceState? = null,
    val dailyStats: DailyStats = DailyStats(),
    val lastStateUpdate: LocalDateTime = LocalDateTime.now(),
    val stateVersion: Long = 1L
)

data class LocationTrackingState(
    val isActive: Boolean = false,
    val startTime: LocalDateTime? = null,
    val lastStatusChange: LocalDateTime = LocalDateTime.now(),
    val statusChangeSource: StateUpdateSource = StateUpdateSource.UNKNOWN
)

data class CurrentPlaceState(
    val placeId: Long,
    val visitId: Long? = null,
    val entryTime: LocalDateTime,
    val lastUpdate: LocalDateTime = LocalDateTime.now()
)

data class DailyStats(
    val date: java.time.LocalDate = java.time.LocalDate.now(),
    val locationCount: Int = 0,
    val placeCount: Int = 0,
    val timeTracked: Long = 0L,
    val lastUpdate: LocalDateTime = LocalDateTime.now()
)

// State update management

enum class StateUpdateSource {
    LOCATION_SERVICE,
    SMART_PROCESSOR,
    DATA_ORCHESTRATOR,
    USER_ACTION,
    SYSTEM_RECOVERY,
    UNKNOWN
}

sealed class StateUpdateResult {
    data class Success(val stateVersion: Long) : StateUpdateResult()
    data class Failed(val reason: String) : StateUpdateResult()
}

data class ValidationResult(
    val isValid: Boolean,
    val reason: String
)

data class StateValidationResult(
    val isValid: Boolean,
    val issues: List<String>,
    val stateVersion: Long
)

// Event system for state changes

sealed class StateChangeEvent {
    data class TrackingStatusChanged(
        val previousStatus: Boolean,
        val newStatus: Boolean,
        val source: StateUpdateSource
    ) : StateChangeEvent()
    
    data class CurrentPlaceChanged(
        val previousPlaceId: Long?,
        val newPlaceId: Long?,
        val source: StateUpdateSource
    ) : StateChangeEvent()
    
    data class DailyStatsChanged(
        val newStats: DailyStats,
        val source: StateUpdateSource
    ) : StateChangeEvent()
    
    data class ValidationFailed(
        val result: StateValidationResult
    ) : StateChangeEvent()
    
    data class ForceSyncRequested(
        val source: StateUpdateSource
    ) : StateChangeEvent()
}

interface StateChangeObserver {
    suspend fun onStateChanged(event: StateChangeEvent)
}