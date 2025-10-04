package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.CurrentStateEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface CurrentStateDao {
    
    /**
     * Get the current state as a Flow for real-time updates
     */
    @Query("SELECT * FROM current_state WHERE id = 1")
    fun getCurrentState(): Flow<CurrentStateEntity?>
    
    /**
     * Get the current state synchronously
     */
    @Query("SELECT * FROM current_state WHERE id = 1")
    suspend fun getCurrentStateSync(): CurrentStateEntity?
    
    /**
     * Insert or replace the current state
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCurrentState(state: CurrentStateEntity)
    
    /**
     * Update current place information
     */
    @Query("""
        UPDATE current_state 
        SET currentPlaceId = :placeId, 
            currentVisitId = :visitId,
            currentPlaceEntryTime = :entryTime,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun updateCurrentPlace(
        placeId: Long?,
        visitId: Long?,
        entryTime: LocalDateTime?,
        timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    /**
     * Update tracking status
     */
    @Query("""
        UPDATE current_state 
        SET isLocationTrackingActive = :isActive,
            trackingStartTime = :startTime,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun updateTrackingStatus(
        isActive: Boolean,
        startTime: LocalDateTime?,
        timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    /**
     * Update daily statistics
     */
    @Query("""
        UPDATE current_state 
        SET totalLocationsToday = :locationCount,
            totalPlacesVisitedToday = :placeCount,
            totalTimeTrackedToday = :timeTracked,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun updateDailyStats(
        locationCount: Int,
        placeCount: Int,
        timeTracked: Long,
        timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    /**
     * Update last location timestamp
     */
    @Query("""
        UPDATE current_state 
        SET lastLocationUpdate = :timestamp,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun updateLastLocationTime(timestamp: LocalDateTime = LocalDateTime.now())
    
    /**
     * Clear current place (when leaving)
     */
    @Query("""
        UPDATE current_state 
        SET currentPlaceId = NULL,
            currentVisitId = NULL,
            currentPlaceEntryTime = NULL,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun clearCurrentPlace(timestamp: LocalDateTime = LocalDateTime.now())
    
    /**
     * Reset daily statistics (call at midnight)
     */
    @Query("""
        UPDATE current_state 
        SET totalLocationsToday = 0,
            totalPlacesVisitedToday = 0,
            totalTimeTrackedToday = 0,
            currentSessionStartTime = :sessionStart,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun resetDailyStats(
        sessionStart: LocalDateTime = LocalDateTime.now(),
        timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    /**
     * Initialize state if it doesn't exist
     */
    @Query("INSERT OR IGNORE INTO current_state (id) VALUES (1)")
    suspend fun initializeState()
}