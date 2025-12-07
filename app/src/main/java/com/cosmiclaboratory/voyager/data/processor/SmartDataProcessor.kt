package com.cosmiclaboratory.voyager.data.processor

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import com.cosmiclaboratory.voyager.utils.LocationUtils
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import com.cosmiclaboratory.voyager.utils.logDailyStats
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.domain.exception.*
import com.cosmiclaboratory.voyager.domain.validation.ValidationService
import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.data.state.StateUpdateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX 3: PendingVisit data class for dwell time detection
 */
data class PendingVisit(
    val placeId: Long,
    val firstDetectionTime: LocalDateTime,
    val lastLocationInside: LocalDateTime,
    val isConfirmed: Boolean = false
)

@Singleton
class SmartDataProcessor @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val currentStateRepository: CurrentStateRepository,
    private val placeDetectionUseCases: PlaceDetectionUseCases,
    private val logger: ProductionLogger,
    private val appStateManager: AppStateManager,
    private val errorHandler: ErrorHandler,
    private val validationService: ValidationService,
    private val preferencesRepository: com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
) {

    companion object {
        private const val TAG = "SmartDataProcessor"
        private const val PLACE_PROXIMITY_THRESHOLD = 100.0 // meters
        // FIX 4: Entry/exit hysteresis thresholds
        private const val ENTRY_THRESHOLD = 50.0 // meters - smaller threshold for entering a place
        private const val EXIT_THRESHOLD = 150.0 // meters - larger threshold for exiting (3x entry)
        private const val VISIT_START_THRESHOLD = 50.0 // meters - kept for compatibility
    }

    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // FIX 3: Track pending visits for dwell time detection
    private var pendingVisit: PendingVisit? = null
    
    /**
     * Main entry point for processing new location data
     * This is called by LocationTrackingService for each new location
     */
    suspend fun processNewLocation(location: Location) {
        errorHandler.executeWithErrorHandling(
            operation = {
                Log.d(TAG, "Processing new location: ${location.latitude}, ${location.longitude}")
                
                // 0. Ensure current state is initialized
                ensureCurrentStateInitializedSafely()
                
                // 1. Validate and store location
                validateLocationSafely(location)
                
                // 2. Store location first
                storeLocationSafely(location)
                
                // 3. Update current state with latest location time
                updateLocationTimeSafely(location)
                
                // 4. Check for place proximity and visit management
                checkPlaceProximityAndManageVisits(location)
                
                // 5. Update daily statistics
                updateDailyStatistics()
                
                // 6. Check for automatic triggers (place detection, etc.)
                checkAutomaticTriggers(location)
                
                Log.d(TAG, "Location processing completed successfully")
                Unit
            },
            context = ErrorContext(
                operation = "processNewLocation",
                component = "SmartDataProcessor",
                userId = null,
                metadata = mapOf(
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "accuracy" to location.accuracy.toString()
                )
            )
        ).getOrElse {
            // Error already handled by ErrorHandler, just log and continue
            Log.d(TAG, "Location processing failed but was handled gracefully")
        }
    }
    
    /**
     * Ensure current state is initialized before processing
     * CRITICAL FIX: Robust auto-recovery and validation with standardized error handling
     */
    private suspend fun ensureCurrentStateInitializedSafely() {
        errorHandler.executeWithErrorHandling(
            operation = {
                var currentState = currentStateRepository.getCurrentStateSync()
                if (currentState == null) {
                    logger.w(TAG, "Current state not found, initializing...")
                    currentStateRepository.initializeState()
                    
                    // Verify initialization worked with retries
                    var retryCount = 0
                    while (retryCount < 3) {
                        currentState = currentStateRepository.getCurrentStateSync()
                        if (currentState != null) {
                            logger.i(TAG, "Current state initialized successfully after ${retryCount + 1} attempts")
                            break
                        }
                        retryCount++
                        logger.w(TAG, "State initialization attempt $retryCount failed, retrying...")
                        kotlinx.coroutines.delay(100)
                    }
                    
                    if (currentState == null) {
                        throw DatabaseException.StateInitializationException(
                            "Current state initialization failed after 3 attempts",
                            recoveryAction = RecoveryAction.REINITIALIZE_STATE
                        )
                    }
                }
                
                // Validate state integrity
                if (currentState.id != 1) {
                    logger.w(TAG, "Invalid state ID detected, recovering...")
                    currentStateRepository.initializeState()
                }
                
                logger.d(TAG, "Current state validation passed - tracking=${currentState.isLocationTrackingActive}")
                Unit
            },
            context = ErrorContext(
                operation = "ensureCurrentStateInitialized",
                component = "SmartDataProcessor"
            )
        ).getOrThrow()
    }
    
    /**
     * Validate location quality and accuracy using ValidationService
     */
    private suspend fun validateLocationSafely(location: Location) {
        validationService.validateLocation(
            location = location,
            context = ErrorContext(
                operation = "validateLocation",
                component = "SmartDataProcessor",
                metadata = mapOf(
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "accuracy" to location.accuracy.toString(),
                    "timestamp" to location.timestamp.toString()
                )
            )
        ).getOrThrow()
    }
    
    /**
     * Store location with standardized error handling
     */
    private suspend fun storeLocationSafely(location: Location) {
        errorHandler.executeWithErrorHandling(
            operation = {
                locationRepository.insertLocation(location)
                Log.d(TAG, "Location stored successfully")
                Unit
            },
            context = ErrorContext(
                operation = "storeLocation",
                component = "SmartDataProcessor",
                metadata = mapOf(
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString()
                )
            )
        ).getOrThrow()
    }
    
    /**
     * Update location time with standardized error handling
     */
    private suspend fun updateLocationTimeSafely(location: Location) {
        errorHandler.executeWithErrorHandling(
            operation = {
                currentStateRepository.updateLastLocationTime(location.timestamp)
                Log.d(TAG, "Current state updated with location time")
                Unit
            },
            context = ErrorContext(
                operation = "updateLocationTime",
                component = "SmartDataProcessor"
            )
        ).onFailure {
            // Continue processing even if this fails
            Log.d(TAG, "Failed to update location time but continuing processing")
        }
    }
    
    /**
     * FIX 3: Check if location is near any known places and manage visits with dwell time detection
     */
    private suspend fun checkPlaceProximityAndManageVisits(location: Location) {
        try {
            val allPlaces = placeRepository.getAllPlaces().first()
            if (allPlaces.isEmpty()) {
                Log.d(TAG, "No places found for proximity check")
                return
            }

            // Find the closest place
            val nearbyPlace = findNearestPlace(location, allPlaces)
            val currentState = currentStateRepository.getCurrentStateSync()

            // Get preferences for dwell time configuration
            val preferences = preferencesRepository.getCurrentPreferences()
            val minDwellTimeSeconds = preferences.minDwellTimeSeconds

            when {
                // Case 1: Near a place, no pending or active visit
                nearbyPlace != null && pendingVisit == null && currentState?.currentPlace == null -> {
                    pendingVisit = PendingVisit(
                        placeId = nearbyPlace.id,
                        firstDetectionTime = location.timestamp,
                        lastLocationInside = location.timestamp
                    )
                    Log.d(TAG, "Started dwell time tracking for: ${nearbyPlace.name}")
                }

                // Case 2: Continuing dwell at same place
                nearbyPlace != null && pendingVisit?.placeId == nearbyPlace.id -> {
                    val dwellDuration = java.time.Duration.between(
                        pendingVisit!!.firstDetectionTime,
                        location.timestamp
                    ).seconds

                    pendingVisit = pendingVisit!!.copy(lastLocationInside = location.timestamp)

                    // Confirm visit after minimum dwell time
                    if (dwellDuration >= minDwellTimeSeconds && !pendingVisit!!.isConfirmed) {
                        startVisitAtPlace(nearbyPlace, pendingVisit!!.firstDetectionTime)
                        pendingVisit = pendingVisit!!.copy(isConfirmed = true)
                        Log.d(TAG, "Visit confirmed after ${dwellDuration}s dwell time")
                    }
                }

                // Case 3: Near a different place than pending
                nearbyPlace != null && pendingVisit != null && pendingVisit!!.placeId != nearbyPlace.id -> {
                    if (pendingVisit!!.isConfirmed) {
                        // End confirmed visit and start tracking new place
                        endCurrentVisit(location.timestamp)
                    }
                    // Abandon unconfirmed pending visit and start new one
                    pendingVisit = PendingVisit(
                        placeId = nearbyPlace.id,
                        firstDetectionTime = location.timestamp,
                        lastLocationInside = location.timestamp
                    )
                    Log.d(TAG, "Switched to tracking new place: ${nearbyPlace.name}")
                }

                // Case 4: Moved away from place
                nearbyPlace == null && pendingVisit != null -> {
                    if (!pendingVisit!!.isConfirmed) {
                        Log.d(TAG, "Abandoned dwell - no visit created (just passing by)")
                    } else if (currentState?.currentPlace != null) {
                        endCurrentVisit(location.timestamp)
                    }
                    pendingVisit = null
                }

                // Case 5: Still at confirmed place - update if needed
                nearbyPlace != null && currentState?.currentPlace?.id == nearbyPlace.id -> {
                    Log.d(TAG, "Still at place: ${nearbyPlace.name}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking place proximity", e)
        }
    }
    
    /**
     * FIX 4: Find the nearest place with entry/exit hysteresis
     */
    private suspend fun findNearestPlace(location: Location, places: List<Place>): Place? {
        val currentState = currentStateRepository.getCurrentStateSync()
        var nearestPlace: Place? = null
        var nearestDistance = Double.MAX_VALUE

        places.forEach { place ->
            val distance = LocationUtils.calculateDistance(
                location.latitude, location.longitude,
                place.latitude, place.longitude
            )

            // FIX 4: Hysteresis - different thresholds for entry vs exit
            val threshold = if (currentState?.currentPlace?.id == place.id || pendingVisit?.placeId == place.id) {
                // Currently at or tracking this place - use larger threshold to prevent ping-pong
                maxOf(EXIT_THRESHOLD, place.radius * 1.5, location.accuracy.toDouble() * 2.0)
            } else {
                // Not at this place - use smaller threshold for entry
                maxOf(ENTRY_THRESHOLD, place.radius, location.accuracy.toDouble() * 1.5)
            }

            if (distance <= threshold && distance < nearestDistance) {
                nearestPlace = place
                nearestDistance = distance
            }
        }

        if (nearestPlace != null) {
            Log.d(TAG, "Found nearby place: ${nearestPlace!!.name} at ${String.format("%.1f", nearestDistance)}m")
        }

        return nearestPlace
    }
    
    /**
     * Start a new visit at the given place
     */
    private suspend fun startVisitAtPlace(place: Place, timestamp: LocalDateTime) {
        try {
            Log.d(TAG, "Starting visit at place: ${place.name}")
            
            // Start new visit
            val visitId = visitRepository.startVisit(place.id, timestamp)
            Log.d(TAG, "Created visit with ID: $visitId")
            
            // CRITICAL: Update app state manager first for place transition
            val stateResult = appStateManager.updateCurrentPlace(
                placeId = place.id,
                visitId = visitId,
                entryTime = timestamp,
                source = StateUpdateSource.SMART_PROCESSOR
            )
            
            if (stateResult is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Success) {
                // Update repository after state manager confirms
                currentStateRepository.updateCurrentPlace(
                    placeId = place.id,
                    visitId = visitId,
                    entryTime = timestamp
                )
                logger.i(TAG, "Updated current place via state manager: ${place.name}")
            } else {
                logger.e(TAG, "State manager rejected place update for: ${place.name}")
            }
            
            // Verify state was updated
            val updatedState = currentStateRepository.getCurrentStateSync()
            Log.d(TAG, "Current state after visit start: isAtPlace=${updatedState?.isAtPlace}, place=${updatedState?.currentPlace?.name}")
            
            Log.d(TAG, "Started visit $visitId at place ${place.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting visit at place ${place.name}", e)
        }
    }
    
    /**
     * End the current active visit
     */
    private suspend fun endCurrentVisit(timestamp: LocalDateTime) {
        try {
            Log.d(TAG, "Ending current visit at timestamp: $timestamp")
            
            val currentVisit = visitRepository.getCurrentVisit()
            if (currentVisit != null) {
                Log.d(TAG, "Found current visit to end: ${currentVisit.id}")
                
                val completedVisit = currentVisit.complete(timestamp)
                visitRepository.updateVisit(completedVisit)
                
                Log.d(TAG, "Ended visit ${currentVisit.id}, duration: ${completedVisit.duration}ms")
            } else {
                Log.d(TAG, "No current visit found to end")
            }
            
            // CRITICAL: Clear current place via state manager first
            val stateResult = appStateManager.updateCurrentPlace(
                placeId = null,
                visitId = null,
                entryTime = null,
                source = StateUpdateSource.SMART_PROCESSOR
            )
            
            if (stateResult is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Success) {
                // Clear repository after state manager confirms
                currentStateRepository.clearCurrentPlace()
                logger.i(TAG, "Cleared current place via state manager")
            } else {
                logger.e(TAG, "State manager rejected place clearing")
            }
            
            // Verify state was cleared
            val updatedState = currentStateRepository.getCurrentStateSync()
            Log.d(TAG, "Current state after visit end: isAtPlace=${updatedState?.isAtPlace}, place=${updatedState?.currentPlace?.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ending current visit", e)
        }
    }
    
    /**
     * Update daily statistics in current state
     */
    private suspend fun updateDailyStatistics() {
        try {
            // Get today's data
            val today = LocalDate.now()
            val startOfDay = today.atStartOfDay()
            val endOfDay = today.plusDays(1).atStartOfDay()
            
            // Count locations today
            val locationsToday = locationRepository.getLocationsSince(startOfDay)
                .filter { it.timestamp.isBefore(endOfDay) }
                .size
            
            // Count unique places visited today
            val visitsToday = visitRepository.getVisitsBetween(startOfDay, endOfDay).first()
            val placesVisitedToday = visitsToday.map { it.placeId }.distinct().size
            
            // CRITICAL FIX: Calculate total time including active visits
            val totalTimeToday = visitsToday.sumOf { visit ->
                if (visit.exitTime != null) {
                    // Completed visit - use stored duration
                    visit.duration
                } else {
                    // Active visit - calculate current duration
                    visit.getCurrentDuration()
                }
            }
            
            logger.logDailyStats(TAG, locationsToday, placesVisitedToday, totalTimeToday)
            
            // CRITICAL: Update app state manager first for consistency
            val stateResult = appStateManager.updateDailyStats(
                locationCount = locationsToday,
                placeCount = placesVisitedToday,
                timeTracked = totalTimeToday,
                source = StateUpdateSource.SMART_PROCESSOR
            )
            
            if (stateResult is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Success) {
                // Update repository after state manager confirms
                currentStateRepository.updateDailyStats(
                    locationCount = locationsToday,
                    placeCount = placesVisitedToday,
                    timeTracked = totalTimeToday
                )
                logger.i(TAG, "Daily stats updated successfully via state manager")
            } else {
                logger.e(TAG, "State manager rejected daily stats update")
            }
            
        } catch (e: Exception) {
            logger.e(TAG, "Failed to update daily statistics", e)
        }
    }
    
    /**
     * Check for automatic triggers like place detection
     */
    private suspend fun checkAutomaticTriggers(location: Location) {
        processingScope.launch {
            try {
                // This is handled by LocationTrackingService's automatic place detection
                // We can add additional triggers here if needed
                
                // Example: Trigger analytics refresh every 100 locations
                val locationCount = locationRepository.getLocationCount()
                if (locationCount % 100 == 0) {
                    Log.d(TAG, "Reached $locationCount locations - could trigger analytics refresh")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking automatic triggers", e)
            }
        }
    }
    
    /**
     * Force reprocess all recent location data for debugging
     */
    suspend fun reprocessRecentData(hoursBack: Int = 24) {
        try {
            Log.d(TAG, "Reprocessing recent data from last $hoursBack hours")
            
            val cutoffTime = LocalDateTime.now().minusHours(hoursBack.toLong())
            val recentLocations = locationRepository.getLocationsSince(cutoffTime)
            
            Log.d(TAG, "Found ${recentLocations.size} recent locations to reprocess")
            
            // Process each location in order
            recentLocations.sortedBy { it.timestamp }.forEach { location ->
                processNewLocation(location)
            }
            
            Log.d(TAG, "Reprocessing completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reprocessing recent data", e)
        }
    }
    
    /**
     * Get processing statistics for monitoring
     */
    suspend fun getProcessingStats(): ProcessingStats {
        return try {
            val currentState = currentStateRepository.getCurrentStateSync()
            val totalLocations = locationRepository.getLocationCount()
            val allPlaces = placeRepository.getAllPlaces().first()
            val activeVisits = visitRepository.getActiveVisits()
            
            ProcessingStats(
                totalLocationsProcessed = totalLocations,
                totalPlacesDetected = allPlaces.size,
                activeVisitsCount = activeVisits.size,
                isCurrentlyAtPlace = currentState?.isAtPlace ?: false,
                currentPlaceName = currentState?.currentPlace?.name,
                lastProcessedTime = currentState?.lastLocationUpdate
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting processing stats", e)
            ProcessingStats()
        }
    }
}

/**
 * Statistics about data processing
 */
data class ProcessingStats(
    val totalLocationsProcessed: Int = 0,
    val totalPlacesDetected: Int = 0,
    val activeVisitsCount: Int = 0,
    val isCurrentlyAtPlace: Boolean = false,
    val currentPlaceName: String? = null,
    val lastProcessedTime: LocalDateTime? = null
)