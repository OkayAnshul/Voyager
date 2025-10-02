package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.LocationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface LocationDao {
    
    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(limit: Int = 1000): Flow<List<LocationEntity>>
    
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
}