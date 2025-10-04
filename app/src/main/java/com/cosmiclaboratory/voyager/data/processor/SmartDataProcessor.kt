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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartDataProcessor @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val currentStateRepository: CurrentStateRepository,
    private val placeDetectionUseCases: PlaceDetectionUseCases
) {
    
    companion object {
        private const val TAG = "SmartDataProcessor"
        private const val PLACE_PROXIMITY_THRESHOLD = 100.0 // meters
        private const val VISIT_START_THRESHOLD = 50.0 // meters - closer threshold for visit start
    }
    
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Main entry point for processing new location data
     * This is called by LocationTrackingService for each new location
     */
    suspend fun processNewLocation(location: Location) {
        try {
            Log.d(TAG, "Processing new location: ${location.latitude}, ${location.longitude}")
            
            // 0. Ensure current state is initialized
            if (!ensureCurrentStateInitialized()) {
                Log.e(TAG, "Failed to initialize current state, aborting location processing")
                return
            }
            
            // 1. Validate and store location
            val isValid = validateLocation(location)
            if (!isValid) {
                Log.d(TAG, "Location validation failed, skipping processing")
                return
            }
            
            // 2. Store location first
            locationRepository.insertLocation(location)
            Log.d(TAG, "Location stored successfully")
            
            // 3. Update current state with latest location time
            try {
                currentStateRepository.updateLastLocationTime(location.timestamp)
                Log.d(TAG, "Current state updated with location time")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update current state location time", e)
                // Continue processing even if this fails
            }
            
            // 4. Check for place proximity and visit management
            checkPlaceProximityAndManageVisits(location)
            
            // 5. Update daily statistics
            updateDailyStatistics()
            
            // 6. Check for automatic triggers (place detection, etc.)
            checkAutomaticTriggers(location)
            
            Log.d(TAG, "Location processing completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location", e)
            // Don't re-throw to prevent crashing the location service
        }
    }
    
    /**
     * Ensure current state is initialized before processing
     * CRITICAL FIX: Robust auto-recovery and validation
     */
    private suspend fun ensureCurrentStateInitialized(): Boolean {
        return try {
            var currentState = currentStateRepository.getCurrentStateSync()
            if (currentState == null) {
                Log.w(TAG, "CRITICAL DEBUG: Current state not found, initializing...")
                currentStateRepository.initializeState()
                
                // Verify initialization worked with retries
                var retryCount = 0
                while (retryCount < 3) {
                    currentState = currentStateRepository.getCurrentStateSync()
                    if (currentState != null) {
                        Log.d(TAG, "CRITICAL DEBUG: Current state initialized successfully after ${retryCount + 1} attempts")
                        break
                    }
                    retryCount++
                    Log.w(TAG, "CRITICAL DEBUG: State initialization attempt $retryCount failed, retrying...")
                    kotlinx.coroutines.delay(100) // Short delay before retry
                }
                
                if (currentState == null) {
                    Log.e(TAG, "CRITICAL ERROR: Current state initialization failed after 3 attempts!")
                    return false
                }
            }
            
            // Validate state integrity
            if (currentState.id != 1) {
                Log.w(TAG, "CRITICAL DEBUG: Invalid state ID detected, recovering...")
                currentStateRepository.initializeState()
            }
            
            Log.d(TAG, "CRITICAL DEBUG: Current state validation passed - tracking=${currentState.isLocationTrackingActive}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to ensure current state initialization", e)
            // Final attempt at recovery
            try {
                currentStateRepository.initializeState()
                true
            } catch (e2: Exception) {
                Log.e(TAG, "CRITICAL ERROR: Final state recovery attempt failed", e2)
                false
            }
        }
    }
    
    /**
     * Validate location quality and accuracy
     */
    private fun validateLocation(location: Location): Boolean {
        // Check accuracy
        if (location.accuracy > 200f) {
            Log.d(TAG, "Location accuracy too poor: ${location.accuracy}m")
            return false
        }
        
        // Check for reasonable coordinates
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            Log.d(TAG, "Invalid coordinates: 0,0")
            return false
        }
        
        // Check timestamp is reasonable
        val now = LocalDateTime.now()
        val timeDiff = java.time.Duration.between(location.timestamp, now).abs().toMinutes()
        if (timeDiff > 60) { // More than 1 hour difference
            Log.d(TAG, "Location timestamp too old or future: ${location.timestamp}")
            return false
        }
        
        return true
    }
    
    /**
     * Check if location is near any known places and manage visits accordingly
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
            
            when {
                // Case 1: Near a place and not currently visiting any place
                nearbyPlace != null && currentState?.currentPlace == null -> {
                    Log.d(TAG, "Entering place: ${nearbyPlace.name}")
                    startVisitAtPlace(nearbyPlace, location.timestamp)
                }
                
                // Case 2: Near a different place than current
                nearbyPlace != null && currentState?.currentPlace?.id != nearbyPlace.id -> {
                    Log.d(TAG, "Moving from ${currentState?.currentPlace?.name} to ${nearbyPlace.name}")
                    // End current visit and start new one
                    endCurrentVisit(location.timestamp)
                    startVisitAtPlace(nearbyPlace, location.timestamp)
                }
                
                // Case 3: No longer near any place but currently visiting
                nearbyPlace == null && currentState?.currentPlace != null -> {
                    Log.d(TAG, "Leaving place: ${currentState.currentPlace.name}")
                    endCurrentVisit(location.timestamp)
                }
                
                // Case 4: Still at the same place - no action needed
                nearbyPlace != null && currentState?.currentPlace?.id == nearbyPlace.id -> {
                    // Still at same place, just update location time
                    Log.d(TAG, "Still at place: ${nearbyPlace.name}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking place proximity", e)
        }
    }
    
    /**
     * Find the nearest place within threshold distance
     */
    private fun findNearestPlace(location: Location, places: List<Place>): Place? {
        var nearestPlace: Place? = null
        var nearestDistance = Double.MAX_VALUE
        
        places.forEach { place ->
            val distance = LocationUtils.calculateDistance(
                location.latitude, location.longitude,
                place.latitude, place.longitude
            )
            
            // Use different thresholds based on place radius and accuracy
            val threshold = maxOf(
                VISIT_START_THRESHOLD,
                place.radius,
                location.accuracy.toDouble() * 1.5
            )
            
            if (distance <= threshold && distance < nearestDistance) {
                nearestPlace = place
                nearestDistance = distance
            }
        }
        
        if (nearestPlace != null) {
            Log.d(TAG, "Found nearby place: ${nearestPlace!!.name} at ${nearestDistance}m")
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
            
            // Update current state with place and visit info
            currentStateRepository.updateCurrentPlace(
                placeId = place.id,
                visitId = visitId,
                entryTime = timestamp
            )
            Log.d(TAG, "Updated current state with place and visit")
            
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
            
            // Clear current place state
            currentStateRepository.clearCurrentPlace()
            Log.d(TAG, "Cleared current place state")
            
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
            
            Log.d(TAG, "CRITICAL DEBUG: Daily stats - locations=$locationsToday, places=$placesVisitedToday, time=${totalTimeToday}ms")
            
            // Update current state
            currentStateRepository.updateDailyStats(
                locationCount = locationsToday,
                placeCount = placesVisitedToday,
                timeTracked = totalTimeToday
            )
            
            Log.d(TAG, "CRITICAL DEBUG: Daily stats updated successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to update daily statistics - this causes zero data!", e)
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