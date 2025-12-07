package com.cosmiclaboratory.voyager.data.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.LocationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface LocationDao {

    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(limit: Int = 1000): Flow<List<LocationEntity>>

    /**
     * Paginated version for large datasets
     * Use with Paging 3 library for efficient loading
     */
    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    fun getRecentLocationsPaged(): PagingSource<Int, LocationEntity>
    
    @Query("SELECT * FROM locations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp")
    fun getLocationsBetween(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getLocationsSince(since: LocalDateTime): List<LocationEntity>
    
    @Query("SELECT COUNT(*) FROM locations")
    suspend fun getLocationCount(): Int
    
    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLocation(): LocationEntity?
    
    @Insert
    suspend fun insertLocation(location: LocationEntity): Long
    
    @Insert
    suspend fun insertLocations(locations: List<LocationEntity>)
    
    @Delete
    suspend fun deleteLocation(location: LocationEntity)
    
    @Query("DELETE FROM locations WHERE timestamp < :beforeDate")
    suspend fun deleteLocationsBefore(beforeDate: LocalDateTime): Int
    
    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
    
    @Query("""
        SELECT * FROM locations
        WHERE latitude BETWEEN :minLat AND :maxLat
        AND longitude BETWEEN :minLng AND :maxLng
        ORDER BY timestamp DESC
    """)
    suspend fun getLocationsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<LocationEntity>

    // Phase 1: Activity-First queries

    /**
     * Get locations filtered by activity type
     */
    @Query("""
        SELECT * FROM locations
        WHERE userActivity = :activity
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun getLocationsByActivity(
        activity: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flow<List<LocationEntity>>

    /**
     * Get activity statistics for a time period
     */
    @Query("""
        SELECT
            userActivity,
            COUNT(*) as count,
            AVG(activityConfidence) as avgConfidence
        FROM locations
        WHERE timestamp BETWEEN :startTime AND :endTime
        GROUP BY userActivity
    """)
    suspend fun getActivityStatistics(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<ActivityStatistic>

    /**
     * Get locations by semantic context
     */
    @Query("""
        SELECT * FROM locations
        WHERE semanticContext = :context
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun getLocationsByContext(
        context: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flow<List<LocationEntity>>

    /**
     * Get count of locations by activity
     */
    @Query("""
        SELECT COUNT(*) FROM locations
        WHERE userActivity = :activity
        AND timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getLocationCountByActivity(
        activity: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Int
}

/**
 * Phase 1: Activity statistics result
 */
data class ActivityStatistic(
    val userActivity: String,
    val count: Int,
    val avgConfidence: Float
)