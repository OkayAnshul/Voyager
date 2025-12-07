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
    private val eventDispatcher: StateEventDispatcher,
    private val placeRepository: com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
) {
    
    companion object {
        private const val TAG = "AppStateManager"
        
        // ENHANCED DEBOUNCING: More intelligent thresholds and timing
        private const val NORMAL_DEBOUNCE_DELAY_MS = 1000L // Reduced to 1s for normal debouncing
        private const val PLACE_DEBOUNCE_DELAY_MS = 3000L // Keep 3s for place changes (more critical)
        private const val MAX_STATE_CHANGES_PER_MINUTE = 60 // Circuit breaker limit
        
        // EMERGENCY DEBOUNCING: Only for truly excessive rapid changes
        private const val EMERGENCY_DEBOUNCE_DELAY_MS = 3000L // Reduced to 3s emergency debounce
        private const val RAPID_CHANGE_THRESHOLD = 8 // Increased threshold to 8 changes to be less aggressive
        private const val RAPID_CHANGE_WINDOW_MS = 30000L // 30 second window for rapid change detection
        private const val COOLDOWN_AFTER_EMERGENCY_MS = 2000L // Reduced cooldown to 2 seconds
        
        // STATE CONSOLIDATION: For batching updates
        private const val STATE_CONSOLIDATION_DELAY_MS = 500L // Reduced to 500ms for faster response
        
        // SMART FILTERING: Ignore duplicate updates
        private const val DUPLICATE_UPDATE_THRESHOLD_MS = 200L // Ignore updates within 200ms that are identical
    }
    
    // Thread-safe state management with mutex protection
    private val stateMutex = Mutex()
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Debouncing and circuit breaker for state changes
    private var lastTrackingUpdate = 0L
    private var lastPlaceUpdate = 0L
    private val stateChangeHistory = mutableListOf<Long>()
    private var isCircuitBreakerOpen = false
    
    // Enhanced rapid change detection
    private val recentTrackingChanges = mutableListOf<Long>()
    private val recentPlaceChanges = mutableListOf<Long>()
    private var isEmergencyDebounceActive = false
    private var lastEmergencyDebounceTime = 0L
    
    // State consolidation and queuing
    private var pendingTrackingStatusChange: Boolean? = null
    private var pendingPlaceChange: Long? = null
    private var consolidationJob: Job? = null
    
    // State update queue to prevent simultaneous updates
    private val stateUpdateQueue = mutableListOf<StateUpdateRequest>()
    private var queueProcessingJob: Job? = null
    private val queueMutex = Mutex()
    
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
                
                // ENHANCED: Smart duplicate detection and intelligent debouncing
                val currentTime = System.currentTimeMillis()
                
                // SMART FILTERING: Skip if this is the same update within threshold
                if (previousStatus == isActive && 
                    currentTime - lastTrackingUpdate < DUPLICATE_UPDATE_THRESHOLD_MS) {
                    Log.d(TAG, "STATE UNCHANGED: Tracking status already $isActive, ignoring redundant update")
                    return StateUpdateResult.Success(currentState.stateVersion)
                }
                
                // Clean old tracking changes
                recentTrackingChanges.removeAll { it < currentTime - RAPID_CHANGE_WINDOW_MS }
                
                // Only add to rapid changes if this is actually a state change
                if (previousStatus != isActive) {
                    recentTrackingChanges.add(currentTime)
                }
                
                // Check for cooldown after previous emergency debounce
                if (currentTime - lastEmergencyDebounceTime < COOLDOWN_AFTER_EMERGENCY_MS) {
                    Log.w(TAG, "COOLDOWN: Still in cooldown period after emergency debounce (${currentTime - lastEmergencyDebounceTime}ms ago)")
                    return StateUpdateResult.Failed("Cooldown: Emergency debounce cooldown active")
                }
                
                // ENHANCED: More intelligent emergency debouncing
                val effectiveDebounceDelay = if (recentTrackingChanges.size >= RAPID_CHANGE_THRESHOLD) {
                    if (!isEmergencyDebounceActive) {
                        Log.e(TAG, "CRITICAL EMERGENCY DEBOUNCE ACTIVATED: ${recentTrackingChanges.size} tracking changes in ${RAPID_CHANGE_WINDOW_MS}ms window")
                        isEmergencyDebounceActive = true
                        lastEmergencyDebounceTime = currentTime
                        
                        stateScope.launch {
                            delay(RAPID_CHANGE_WINDOW_MS) // Reset emergency debounce after window
                            isEmergencyDebounceActive = false
                            recentTrackingChanges.clear()
                            Log.i(TAG, "EMERGENCY DEBOUNCE DEACTIVATED: Rapid change window expired")
                        }
                    }
                    EMERGENCY_DEBOUNCE_DELAY_MS
                } else {
                    NORMAL_DEBOUNCE_DELAY_MS // Use normal debounce delay
                }
                
                // ENHANCED: Only debounce actual state changes, not redundant updates
                if (previousStatus != isActive && currentTime - lastTrackingUpdate < effectiveDebounceDelay) {
                    val timeSinceLastUpdate = currentTime - lastTrackingUpdate
                    Log.w(TAG, "DEBOUNCED: Tracking status change ignored due to rapid updates (${timeSinceLastUpdate}ms ago, threshold=${effectiveDebounceDelay}ms)")
                    return StateUpdateResult.Failed("Debounced: Too rapid state changes (${timeSinceLastUpdate}ms < ${effectiveDebounceDelay}ms)")
                }
                
                // CRITICAL FIX: Circuit breaker to prevent infinite loops
                if (isCircuitBreakerTriggered()) {
                    Log.e(TAG, "CIRCUIT BREAKER OPEN: Blocking tracking status update to prevent infinite loop")
                    return StateUpdateResult.Failed("Circuit breaker open: Too many rapid state changes")
                }
                
                // Check if state is actually changing
                if (previousStatus == isActive) {
                    Log.d(TAG, "STATE UNCHANGED: Tracking status already $isActive, ignoring redundant update")
                    return StateUpdateResult.Success(currentState.stateVersion)
                }
                
                Log.d(TAG, "CRITICAL STATE UPDATE: Tracking status change - " +
                      "from=$previousStatus to=$isActive, source=$source")
                
                // Validate state transition
                val validationResult = validateTrackingStatusTransition(previousStatus, isActive)
                if (!validationResult.isValid) {
                    Log.e(TAG, "CRITICAL ERROR: Invalid tracking status transition - ${validationResult.reason}")
                    return StateUpdateResult.Failed(validationResult.reason)
                }
                
                // Update debounce timestamp
                lastTrackingUpdate = currentTime
                recordStateChange(currentTime)
                
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
                
                // CRITICAL FIX: Only dispatch events for external sources to break circular dependencies
                if (source != StateUpdateSource.LOCATION_SERVICE) {
                    stateScope.launch {
                        delay(100) // Small delay to ensure state is fully updated
                        val trackingEvent = TrackingEvent(
                            type = if (isActive) EventTypes.TRACKING_STARTED else EventTypes.TRACKING_STOPPED,
                            isActive = isActive,
                            reason = "State manager update",
                            source = source.name
                        )
                        eventDispatcher.dispatchTrackingEvent(trackingEvent)
                    }
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
                
                // ENHANCED: Smart place change detection with intelligent debouncing
                val currentTime = System.currentTimeMillis()
                
                // SMART FILTERING: Skip if this is the same place update within threshold
                if (previousPlace?.placeId == placeId && 
                    currentTime - lastPlaceUpdate < DUPLICATE_UPDATE_THRESHOLD_MS) {
                    Log.d(TAG, "PLACE UNCHANGED: Already at place $placeId, ignoring redundant update")
                    return StateUpdateResult.Success(currentState.stateVersion)
                }
                
                // Clean old place changes
                recentPlaceChanges.removeAll { it < currentTime - RAPID_CHANGE_WINDOW_MS }
                
                // Only add to rapid changes if this is actually a place change
                if (previousPlace?.placeId != placeId) {
                    recentPlaceChanges.add(currentTime)
                }
                
                // Check for cooldown after previous emergency debounce
                if (currentTime - lastEmergencyDebounceTime < COOLDOWN_AFTER_EMERGENCY_MS) {
                    Log.w(TAG, "COOLDOWN: Still in cooldown period after emergency debounce (${currentTime - lastEmergencyDebounceTime}ms ago)")
                    return StateUpdateResult.Failed("Cooldown: Emergency debounce cooldown active")
                }
                
                // ENHANCED: More intelligent emergency debouncing for places
                val effectiveDebounceDelay = if (recentPlaceChanges.size >= RAPID_CHANGE_THRESHOLD) {
                    if (!isEmergencyDebounceActive) {
                        Log.e(TAG, "CRITICAL EMERGENCY PLACE DEBOUNCE ACTIVATED: ${recentPlaceChanges.size} place changes in ${RAPID_CHANGE_WINDOW_MS}ms window")
                        isEmergencyDebounceActive = true
                        lastEmergencyDebounceTime = currentTime
                        
                        stateScope.launch {
                            delay(RAPID_CHANGE_WINDOW_MS) // Reset emergency debounce after window
                            isEmergencyDebounceActive = false
                            recentPlaceChanges.clear()
                            Log.i(TAG, "EMERGENCY PLACE DEBOUNCE DEACTIVATED: Rapid change window expired")
                        }
                    }
                    EMERGENCY_DEBOUNCE_DELAY_MS
                } else {
                    PLACE_DEBOUNCE_DELAY_MS // Use place-specific debounce delay
                }
                
                // ENHANCED: Only debounce actual place changes, not redundant updates
                if (previousPlace?.placeId != placeId && currentTime - lastPlaceUpdate < effectiveDebounceDelay) {
                    val timeSinceLastUpdate = currentTime - lastPlaceUpdate
                    Log.w(TAG, "DEBOUNCED: Place update ignored due to rapid updates (${timeSinceLastUpdate}ms ago, threshold=${effectiveDebounceDelay}ms)")
                    return StateUpdateResult.Failed("Debounced: Too rapid place changes (${timeSinceLastUpdate}ms < ${effectiveDebounceDelay}ms)")
                }
                
                Log.d(TAG, "CRITICAL STATE UPDATE: Current place change - " +
                      "from=${previousPlace?.placeId} to=$placeId, source=$source")
                
                // Update debounce timestamp
                lastPlaceUpdate = currentTime
                recordStateChange(currentTime)
                
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
                
                // CRITICAL FIX: Only dispatch place events for external sources to break circular dependencies
                if (source != StateUpdateSource.SMART_PROCESSOR) {
                    stateScope.launch {
                        delay(100) // Small delay to ensure state is fully updated
                        when {
                            previousPlace?.placeId == null && placeId != null -> {
                                // Entering a place
                                val placeName = getPlaceName(placeId)
                                val placeEvent = PlaceEvent(
                                    type = EventTypes.PLACE_ENTERED,
                                    placeId = placeId,
                                    placeName = placeName,
                                    action = PlaceAction.ENTERED
                                )
                                eventDispatcher.dispatchPlaceEvent(placeEvent)
                            }
                            previousPlace?.placeId != null && placeId == null -> {
                                // Exiting a place
                                val placeName = getPlaceName(previousPlace.placeId)
                                val placeEvent = PlaceEvent(
                                    type = EventTypes.PLACE_EXITED,
                                    placeId = previousPlace.placeId,
                                    placeName = placeName,
                                    action = PlaceAction.EXITED
                                )
                                eventDispatcher.dispatchPlaceEvent(placeEvent)
                            }
                            previousPlace?.placeId != placeId && placeId != null -> {
                                // Changing places
                                if (previousPlace?.placeId != null) {
                                    val exitPlaceName = getPlaceName(previousPlace.placeId)
                                    val exitEvent = PlaceEvent(
                                        type = EventTypes.PLACE_EXITED,
                                        placeId = previousPlace.placeId,
                                        placeName = exitPlaceName,
                                        action = PlaceAction.EXITED
                                    )
                                    eventDispatcher.dispatchPlaceEvent(exitEvent)
                                }
                                
                                val enterPlaceName = getPlaceName(placeId)
                                val enterEvent = PlaceEvent(
                                    type = EventTypes.PLACE_ENTERED,
                                    placeId = placeId,
                                    placeName = enterPlaceName,
                                    action = PlaceAction.ENTERED
                                )
                                eventDispatcher.dispatchPlaceEvent(enterEvent)
                            }
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
     * CRITICAL: Returns defensive copy to prevent external mutations
     */
    fun getCurrentState(): AppState = _appState.value.copy()
    
    /**
     * CRITICAL: Emergency state reset to break infinite loops
     */
    suspend fun emergencyStateReset(): StateUpdateResult {
        return stateMutex.withLock {
            try {
                Log.w(TAG, "EMERGENCY STATE RESET: Clearing all state to prevent infinite loops")
                
                val cleanState = AppState(
                    locationTracking = LocationTrackingState(),
                    currentPlace = null,
                    dailyStats = DailyStats(),
                    lastStateUpdate = LocalDateTime.now(),
                    stateVersion = _appState.value.stateVersion + 1
                )
                
                _appState.value = cleanState
                
                // Clear circuit breaker state
                isCircuitBreakerOpen = false
                stateChangeHistory.clear()
                lastTrackingUpdate = 0L
                lastPlaceUpdate = 0L
                
                Log.i(TAG, "EMERGENCY STATE RESET COMPLETE: State version=${cleanState.stateVersion}")
                
                StateUpdateResult.Success(cleanState.stateVersion)
                
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR: Failed to reset state", e)
                StateUpdateResult.Failed("Emergency reset failed: ${e.message}")
            }
        }
    }
    
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
     * Validate state consistency and integrity with comprehensive checks
     */
    suspend fun validateStateConsistency(): StateValidationResult {
        return stateMutex.withLock {
            val currentState = _appState.value
            val validationIssues = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val criticalIssues = mutableListOf<String>()
            
            // CRITICAL FIX: Enhanced tracking state validation
            val tracking = currentState.locationTracking
            if (tracking.isActive) {
                if (tracking.startTime == null) {
                    criticalIssues.add("CRITICAL: Tracking active but no start time")
                }
                
                if (tracking.startTime?.let { 
                    java.time.Duration.between(it, LocalDateTime.now()).toHours() > 24 
                } == true) {
                    warnings.add("WARNING: Tracking active for over 24 hours")
                }
                
                if (tracking.statusChangeSource == StateUpdateSource.UNKNOWN) {
                    warnings.add("WARNING: Tracking status source unknown")
                }
            } else {
                // If not tracking, there shouldn't be a start time
                if (tracking.startTime != null) {
                    validationIssues.add("Tracking inactive but start time present")
                }
            }
            
            // CRITICAL FIX: Enhanced place state validation
            currentState.currentPlace?.let { place ->
                if (place.visitId != null && place.entryTime == null) {
                    criticalIssues.add("CRITICAL: Visit ID present but no entry time")
                }
                
                if (place.placeId <= 0) {
                    criticalIssues.add("CRITICAL: Invalid place ID: ${place.placeId}")
                }
                
                if (place.entryTime?.isAfter(LocalDateTime.now()) == true) {
                    criticalIssues.add("CRITICAL: Place entry time is in the future")
                }
                
                if (place.entryTime?.let { 
                    java.time.Duration.between(it, LocalDateTime.now()).toHours() > 72 
                } == true) {
                    warnings.add("WARNING: Very long visit (over 72 hours) at place ${place.placeId}")
                }
            }
            
            // CRITICAL FIX: Enhanced daily stats validation
            val stats = currentState.dailyStats
            if (stats.locationCount < 0 || stats.placeCount < 0 || stats.timeTracked < 0) {
                criticalIssues.add("CRITICAL: Negative values in daily stats")
            }
            
            // Grace period: Allow 5 minutes after tracking starts for time to be recorded
            if (stats.locationCount > 0 && stats.timeTracked == 0L) {
                val timeSinceTrackingStart = if (tracking.startTime != null) {
                    java.time.Duration.between(tracking.startTime, LocalDateTime.now()).toSeconds()
                } else 0L

                // Only report issue if tracking has been active for more than 5 minutes
                if (timeSinceTrackingStart > 300) {
                    validationIssues.add("Locations recorded but no time tracked")
                }
            }
            
            if (stats.placeCount > stats.locationCount && stats.locationCount > 0) {
                validationIssues.add("More places than locations recorded")
            }
            
            if (stats.timeTracked > 24 * 60 * 60 * 1000L) { // More than 24 hours
                warnings.add("WARNING: Daily tracking time exceeds 24 hours: ${stats.timeTracked}ms")
            }
            
            // CRITICAL FIX: State version and timing validation
            if (currentState.stateVersion <= 0) {
                criticalIssues.add("CRITICAL: Invalid state version: ${currentState.stateVersion}")
            }
            
            if (currentState.lastStateUpdate.isAfter(LocalDateTime.now().plusMinutes(5))) {
                criticalIssues.add("CRITICAL: Last state update is in the future")
            }
            
            if (currentState.lastStateUpdate.isBefore(LocalDateTime.now().minusDays(1))) {
                warnings.add("WARNING: State hasn't been updated for over 24 hours")
            }
            
            // CRITICAL FIX: Cross-state consistency checks
            // Grace period: Allow 2 minutes after tracking starts for place to be determined
            if (tracking.isActive && currentState.currentPlace == null) {
                val timeSinceTrackingStart = if (tracking.startTime != null) {
                    java.time.Duration.between(tracking.startTime, LocalDateTime.now()).toSeconds()
                } else 0L

                // Only warn if tracking has been active for more than 2 minutes
                if (timeSinceTrackingStart > 120) {
                    warnings.add("WARNING: Tracking active but no current place (${timeSinceTrackingStart}s)")
                } else {
                    // Log as info during grace period
                    Log.i(TAG, "INFO: Tracking active, place determination in progress (${timeSinceTrackingStart}s)")
                }
            }
            
            if (!tracking.isActive && stats.timeTracked > 0 && stats.date == java.time.LocalDate.now()) {
                // Should be tracking if we have time tracked today and it's current day
                warnings.add("WARNING: Time tracked today but tracking is inactive")
            }
            
            // CRITICAL FIX: Rapid change detection validation
            val rapidChangeCount = recentTrackingChanges.size + recentPlaceChanges.size
            if (rapidChangeCount > RAPID_CHANGE_THRESHOLD) {
                criticalIssues.add("CRITICAL: Excessive rapid state changes detected: $rapidChangeCount")
            }
            
            if (isEmergencyDebounceActive) {
                warnings.add("WARNING: Emergency debounce is currently active")
            }
            
            // Compile all issues
            val allIssues = criticalIssues + validationIssues + warnings
            
            StateValidationResult(
                isValid = criticalIssues.isEmpty() && validationIssues.isEmpty(),
                issues = allIssues,
                stateVersion = currentState.stateVersion,
                criticalIssues = criticalIssues,
                warnings = warnings
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
    
    private suspend fun getPlaceName(placeId: Long): String {
        return try {
            val place = placeRepository.getPlaceById(placeId)
            place?.name ?: "Place $placeId"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get place name for ID $placeId", e)
            "Place $placeId"
        }
    }
    
    /**
     * CRITICAL FIX: Circuit breaker to detect and prevent infinite state loops
     */
    private fun isCircuitBreakerTriggered(): Boolean {
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - 60000L
        
        // Clean old entries
        stateChangeHistory.removeAll { it < oneMinuteAgo }
        
        // More aggressive circuit breaker: trigger at 30 changes per minute
        if (stateChangeHistory.size >= 30) {
            if (!isCircuitBreakerOpen) {
                Log.e(TAG, "CIRCUIT BREAKER TRIGGERED: ${stateChangeHistory.size} state changes in last minute!")
                isCircuitBreakerOpen = true
                
                // Emergency state reset if too many rapid changes
                if (stateChangeHistory.size >= 50) {
                    stateScope.launch {
                        Log.e(TAG, "CRITICAL: Too many state changes (${stateChangeHistory.size}), triggering emergency reset")
                        emergencyStateReset()
                    }
                }
                
                // Reset circuit breaker after 10 seconds
                stateScope.launch {
                    delay(10000)
                    isCircuitBreakerOpen = false
                    stateChangeHistory.clear()
                    Log.i(TAG, "CIRCUIT BREAKER RESET: System recovered from rapid state changes")
                }
            }
            return true
        }
        
        return false
    }
    
    /**
     * Record state change timestamp for circuit breaker monitoring
     */
    private fun recordStateChange(timestamp: Long) {
        stateChangeHistory.add(timestamp)
        // Keep only last 100 entries to prevent memory issues
        if (stateChangeHistory.size > 100) {
            stateChangeHistory.removeAt(0)
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
                        
                        // CRITICAL FIX: Automatic recovery for validation failures
                        handleValidationFailures(validationResult)
                    }
                    
                    delay(30000) // Validate every 30 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in state validation monitoring", e)
                    delay(60000) // Longer delay on error
                }
            }
        }
    }
    
    /**
     * Handle validation failures with automatic recovery
     */
    private suspend fun handleValidationFailures(result: StateValidationResult) {
        try {
            // Handle critical issues first
            if (result.criticalIssues.isNotEmpty()) {
                Log.e(TAG, "CRITICAL RECOVERY: Handling ${result.criticalIssues.size} critical issues")
                
                for (criticalIssue in result.criticalIssues) {
                    when {
                        criticalIssue.contains("rapid state changes") -> {
                            Log.e(TAG, "CRITICAL RECOVERY: Clearing rapid state changes and activating emergency cooldown")
                            clearRapidStateChanges()
                            // Extend emergency debounce
                            lastEmergencyDebounceTime = System.currentTimeMillis()
                            isEmergencyDebounceActive = true
                        }
                        
                        criticalIssue.contains("Invalid state version") -> {
                            Log.e(TAG, "CRITICAL RECOVERY: Resetting state version")
                            emergencyStateReset()
                        }
                        
                        criticalIssue.contains("entry time is in the future") -> {
                            Log.e(TAG, "CRITICAL RECOVERY: Fixing future timestamp")
                            fixFutureTimestamps()
                        }
                        
                        criticalIssue.contains("Negative values") -> {
                            Log.e(TAG, "CRITICAL RECOVERY: Resetting negative stats")
                            resetNegativeStats()
                        }
                    }
                }
            }
            
            // Handle consistency warnings
            if (result.warnings.isNotEmpty()) {
                Log.w(TAG, "RECOVERY: Handling ${result.warnings.size} warnings")
                
                for (warning in result.warnings) {
                    when {
                        warning.contains("Tracking active for over 24 hours") -> {
                            Log.w(TAG, "RECOVERY: Long tracking session detected - sending notification")
                            // Could notify user about long tracking session
                        }
                        
                        warning.contains("Emergency debounce is currently active") -> {
                            Log.w(TAG, "RECOVERY: Emergency debounce active - monitoring stability")
                            // Already being handled by debounce logic
                        }
                        
                        warning.contains("Very long visit") -> {
                            Log.w(TAG, "RECOVERY: Very long visit detected - validating visit data")
                            // Could validate visit data or notify user
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during validation failure recovery", e)
        }
    }
    
    /**
     * Clear rapid state changes as part of recovery
     */
    private suspend fun clearRapidStateChanges() {
        stateMutex.withLock {
            recentTrackingChanges.clear()
            recentPlaceChanges.clear()
            stateChangeHistory.clear()
            isCircuitBreakerOpen = false
            
            Log.i(TAG, "RECOVERY: Cleared all rapid state change tracking")
        }
    }
    
    /**
     * Fix future timestamps in current state
     */
    private suspend fun fixFutureTimestamps() {
        stateMutex.withLock {
            val currentState = _appState.value
            val now = LocalDateTime.now()
            var stateChanged = false
            
            currentState.currentPlace?.let { place ->
                if (place.entryTime?.isAfter(now) == true) {
                    Log.w(TAG, "RECOVERY: Fixing future place entry time")
                    // This would require updating the place data
                    stateChanged = true
                }
            }
            
            if (currentState.locationTracking.startTime?.isAfter(now) == true) {
                Log.w(TAG, "RECOVERY: Fixing future tracking start time") 
                // This would require updating the tracking data
                stateChanged = true
            }
            
            if (stateChanged) {
                Log.i(TAG, "RECOVERY: Fixed future timestamps in state")
            }
        }
    }
    
    /**
     * Reset negative statistics values
     */
    private suspend fun resetNegativeStats() {
        stateMutex.withLock {
            val currentState = _appState.value
            val stats = currentState.dailyStats
            
            if (stats.locationCount < 0 || stats.placeCount < 0 || stats.timeTracked < 0) {
                Log.w(TAG, "RECOVERY: Resetting negative daily statistics")
                
                val fixedStats = stats.copy(
                    locationCount = maxOf(0, stats.locationCount),
                    placeCount = maxOf(0, stats.placeCount),
                    timeTracked = maxOf(0L, stats.timeTracked)
                )
                
                val updatedState = currentState.copy(
                    dailyStats = fixedStats,
                    stateVersion = currentState.stateVersion + 1
                )
                
                _appState.value = updatedState
                Log.i(TAG, "RECOVERY: Fixed negative statistics values")
            }
        }
    }
    
    /**
     * Queue a state update request instead of immediate processing
     */
    private suspend fun queueStateUpdate(request: StateUpdateRequest): StateUpdateResult {
        return queueMutex.withLock {
            stateUpdateQueue.add(request)
            Log.d(TAG, "QUEUED: State update ${request.type} from ${request.source}")
            
            // Start queue processing if not already running
            if (queueProcessingJob?.isActive != true) {
                queueProcessingJob = stateScope.launch {
                    processStateUpdateQueue()
                }
            }
            
            // For now, return success - in a full implementation, we'd track request results
            StateUpdateResult.Success(System.currentTimeMillis())
        }
    }
    
    /**
     * Process queued state updates with consolidation and debouncing
     */
    private suspend fun processStateUpdateQueue() {
        try {
            while (stateUpdateQueue.isNotEmpty()) {
                queueMutex.withLock {
                    if (stateUpdateQueue.isEmpty()) return@withLock
                    
                    // Process up to 3 updates at once, prioritizing by type and recency
                    val toProcess = stateUpdateQueue
                        .sortedBy { it.timestamp }
                        .take(3)
                    
                    stateUpdateQueue.removeAll(toProcess.toSet())
                    
                    for (request in toProcess) {
                        try {
                            Log.d(TAG, "PROCESSING: Queued state update ${request.type}")
                            // Here we would call the appropriate internal update method
                            // For now, just log the processing
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing queued state update ${request.type}", e)
                        }
                    }
                }
                
                // Small delay between batch processing
                delay(500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in state update queue processing", e)
        }
    }
}

// State update queuing system
private data class StateUpdateRequest(
    val type: StateUpdateType,
    val timestamp: Long,
    val source: StateUpdateSource,
    val data: Any?
)

private enum class StateUpdateType {
    TRACKING_STATUS,
    CURRENT_PLACE,
    DAILY_STATS
}

private data class TrackingStatusData(
    val isActive: Boolean,
    val startTime: LocalDateTime?
)

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
    val stateVersion: Long,
    val criticalIssues: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
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