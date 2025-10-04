package com.cosmiclaboratory.voyager.data.repository

import android.util.Log
import com.cosmiclaboratory.voyager.data.database.dao.CurrentStateDao
import com.cosmiclaboratory.voyager.data.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.data.database.dao.VisitDao
import com.cosmiclaboratory.voyager.data.database.entity.CurrentStateEntity
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.data.state.StateUpdateSource
import com.cosmiclaboratory.voyager.domain.model.CurrentState
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentStateRepositoryImpl @Inject constructor(
    private val currentStateDao: CurrentStateDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val appStateManager: AppStateManager
) : CurrentStateRepository {
    
    companion object {
        private const val TAG = "CurrentStateRepository"
    }
    
    override fun getCurrentState(): Flow<CurrentState?> {
        return currentStateDao.getCurrentState().map { entity ->
            entity?.let { convertToDomainModel(it) }
        }
    }
    
    override suspend fun getCurrentStateSync(): CurrentState? {
        val entity = currentStateDao.getCurrentStateSync()
        return entity?.let { convertToDomainModel(it) }
    }
    
    override suspend fun updateCurrentPlace(
        placeId: Long?,
        visitId: Long?,
        entryTime: LocalDateTime?
    ) {
        try {
            ensureStateExists()
            
            // Validate references exist if provided
            placeId?.let { id ->
                val placeExists = placeDao.getPlaceById(id) != null
                if (!placeExists) {
                    Log.w(TAG, "Attempted to set current place to non-existent place: $id")
                    return
                }
            }
            
            visitId?.let { id ->
                val visitExists = visitDao.getVisitById(id) != null
                if (!visitExists) {
                    Log.w(TAG, "Attempted to set current visit to non-existent visit: $id")
                    return
                }
            }
            
            // CRITICAL: Update unified state manager first for consistency
            val stateResult = appStateManager.updateCurrentPlace(
                placeId = placeId,
                visitId = visitId,
                entryTime = entryTime,
                source = StateUpdateSource.SMART_PROCESSOR
            )
            
            if (stateResult is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Failed) {
                Log.e(TAG, "CRITICAL ERROR: AppStateManager rejected place update - ${stateResult.reason}")
                return
            }
            
            // Update database after state manager confirms the change
            currentStateDao.updateCurrentPlace(placeId, visitId, entryTime)
            Log.d(TAG, "CRITICAL SUCCESS: Updated current place synchronized - placeId=$placeId, visitId=$visitId")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to update current place", e)
            recoverState()
            throw e
        }
    }
    
    override suspend fun updateTrackingStatus(
        isActive: Boolean,
        startTime: LocalDateTime?
    ) {
        try {
            // Ensure state exists before updating
            ensureStateExists()
            
            // CRITICAL: Update unified state manager first for atomic operations
            val stateResult = appStateManager.updateTrackingStatus(
                isActive = isActive,
                startTime = startTime,
                source = StateUpdateSource.LOCATION_SERVICE
            )
            
            if (stateResult is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Failed) {
                Log.e(TAG, "CRITICAL ERROR: AppStateManager rejected tracking status update - ${stateResult.reason}")
                return
            }
            
            // Update database after state manager confirms the change
            currentStateDao.updateTrackingStatus(isActive, startTime)
            Log.d(TAG, "CRITICAL SUCCESS: Updated tracking status synchronized - isActive=$isActive, startTime=$startTime")
            
            // Validate the update was successful
            val updatedState = getCurrentStateSync()
            if (updatedState?.isLocationTrackingActive != isActive) {
                Log.w(TAG, "CRITICAL WARNING: State validation failed - expected isActive=$isActive, got ${updatedState?.isLocationTrackingActive}")
                // Force synchronization between state manager and database
                appStateManager.forceSynchronization(StateUpdateSource.SYSTEM_RECOVERY)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to update tracking status", e)
            // Attempt recovery by reinitializing state
            recoverState()
            throw e
        }
    }
    
    override suspend fun updateDailyStats(
        locationCount: Int,
        placeCount: Int,
        timeTracked: Long
    ) {
        try {
            ensureStateExists()
            
            // CRITICAL: Update unified state manager first for consistency
            val stateResult = appStateManager.updateDailyStats(
                locationCount = locationCount,
                placeCount = placeCount,
                timeTracked = timeTracked,
                source = StateUpdateSource.SMART_PROCESSOR
            )
            
            if (stateResult is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Failed) {
                Log.e(TAG, "CRITICAL ERROR: AppStateManager rejected daily stats update - ${stateResult.reason}")
                return
            }
            
            // Update database after state manager confirms the change
            currentStateDao.updateDailyStats(locationCount, placeCount, timeTracked)
            Log.d(TAG, "CRITICAL SUCCESS: Updated daily stats synchronized - locations=$locationCount, places=$placeCount, time=${timeTracked}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to update daily stats", e)
            throw e
        }
    }
    
    override suspend fun updateLastLocationTime(timestamp: LocalDateTime) {
        currentStateDao.updateLastLocationTime(timestamp)
    }
    
    override suspend fun clearCurrentPlace() {
        currentStateDao.clearCurrentPlace()
    }
    
    override suspend fun resetDailyStats() {
        currentStateDao.resetDailyStats()
    }
    
    override suspend fun initializeState() {
        try {
            currentStateDao.initializeState()
            Log.d(TAG, "State initialized successfully")
            
            // Validate initialization
            val state = getCurrentStateSync()
            if (state == null) {
                Log.w(TAG, "State initialization validation failed - state is null")
                // Force create a valid state
                createDefaultState()
            } else {
                Log.d(TAG, "State validation passed: isLocationTrackingActive=${state.isLocationTrackingActive}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize state", e)
            // Try to recover by creating default state
            createDefaultState()
        }
    }
    
    override suspend fun getCurrentVisitDuration(): Long {
        val state = getCurrentStateSync()
        return state?.currentVisitDuration ?: 0L
    }
    
    override suspend fun isCurrentlyAtPlace(): Boolean {
        val state = getCurrentStateSync()
        return state?.isAtPlace ?: false
    }
    
    /**
     * Convert CurrentStateEntity to domain model with related data
     */
    private suspend fun convertToDomainModel(entity: CurrentStateEntity): CurrentState {
        // Get current place if available
        val currentPlace: com.cosmiclaboratory.voyager.domain.model.Place? = entity.currentPlaceId?.let { placeId ->
            placeDao.getPlaceById(placeId)?.toDomainModel()
        }
        
        // Get current visit if available
        val currentVisit: com.cosmiclaboratory.voyager.domain.model.Visit? = entity.currentVisitId?.let { visitId ->
            // Get the specific visit by ID
            visitDao.getVisitById(visitId)?.toDomainModel()
        }
        
        // Use the proper mapper from CurrentStateMapper.kt
        return entity.toDomainModel(
            currentPlace = currentPlace,
            currentVisit = currentVisit
        )
    }
    
    /**
     * Ensure state exists, create if missing
     */
    private suspend fun ensureStateExists() {
        val state = currentStateDao.getCurrentStateSync()
        if (state == null) {
            Log.w(TAG, "CurrentState is null, initializing...")
            initializeState()
        }
    }
    
    /**
     * Create a default state with safe initial values
     */
    private suspend fun createDefaultState() {
        try {
            val defaultState = CurrentStateEntity(
                id = 1,
                isLocationTrackingActive = false,
                trackingStartTime = null,
                currentPlaceId = null,
                currentVisitId = null,
                currentPlaceEntryTime = null,
                totalLocationsToday = 0,
                totalPlacesVisitedToday = 0,
                totalTimeTrackedToday = 0L,
                lastLocationUpdate = LocalDateTime.now(),
                currentSessionStartTime = LocalDateTime.now(),
                lastUpdated = LocalDateTime.now()
            )
            
            currentStateDao.updateCurrentState(defaultState)
            Log.d(TAG, "Created default state successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create default state", e)
        }
    }
    
    /**
     * Recover state by reinitializing with validation
     */
    private suspend fun recoverState() {
        try {
            Log.w(TAG, "Attempting state recovery...")
            createDefaultState()
            
            // Verify recovery
            val recoveredState = getCurrentStateSync()
            if (recoveredState != null) {
                Log.d(TAG, "State recovery successful")
            } else {
                Log.e(TAG, "State recovery failed - state is still null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "State recovery failed", e)
        }
    }
    
    /**
     * Validate state integrity and fix issues
     */
    suspend fun validateAndFixState(): Boolean {
        return try {
            val state = getCurrentStateSync()
            if (state == null) {
                Log.w(TAG, "State validation failed: null state")
                recoverState()
                return false
            }
            
            // Check for orphaned references
            state.currentPlace?.let { place ->
                val placeExists = placeDao.getPlaceById(place.id) != null
                if (!placeExists) {
                    Log.w(TAG, "Orphaned place reference found: ${place.id}, clearing...")
                    clearCurrentPlace()
                }
            }
            
            state.currentVisit?.let { visit ->
                val visitExists = visitDao.getVisitById(visit.id) != null
                if (!visitExists) {
                    Log.w(TAG, "Orphaned visit reference found: ${visit.id}, clearing...")
                    clearCurrentPlace() // This clears both place and visit
                }
            }
            
            Log.d(TAG, "State validation completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "State validation failed", e)
            false
        }
    }
}