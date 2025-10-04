package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.CurrentState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface CurrentStateRepository {
    
    /**
     * Get current state as a Flow for real-time updates
     */
    fun getCurrentState(): Flow<CurrentState?>
    
    /**
     * Get current state synchronously
     */
    suspend fun getCurrentStateSync(): CurrentState?
    
    /**
     * Update current place and visit information
     */
    suspend fun updateCurrentPlace(
        placeId: Long?,
        visitId: Long?,
        entryTime: LocalDateTime?
    )
    
    /**
     * Update location tracking status
     */
    suspend fun updateTrackingStatus(
        isActive: Boolean,
        startTime: LocalDateTime?
    )
    
    /**
     * Update daily statistics
     */
    suspend fun updateDailyStats(
        locationCount: Int,
        placeCount: Int,
        timeTracked: Long
    )
    
    /**
     * Update last location timestamp
     */
    suspend fun updateLastLocationTime(timestamp: LocalDateTime = LocalDateTime.now())
    
    /**
     * Clear current place (when leaving)
     */
    suspend fun clearCurrentPlace()
    
    /**
     * Reset daily statistics (call at midnight)
     */
    suspend fun resetDailyStats()
    
    /**
     * Initialize state if needed
     */
    suspend fun initializeState()
    
    /**
     * Get current visit duration (if any active visit)
     */
    suspend fun getCurrentVisitDuration(): Long
    
    /**
     * Check if user is currently at a place
     */
    suspend fun isCurrentlyAtPlace(): Boolean
}