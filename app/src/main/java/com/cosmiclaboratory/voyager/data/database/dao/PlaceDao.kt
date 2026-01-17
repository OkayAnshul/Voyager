package com.cosmiclaboratory.voyager.data.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY visitCount DESC")
    fun getAllPlaces(): Flow<List<PlaceEntity>>

    /**
     * Paginated version for large datasets
     * Sorted by most visited places first
     */
    @Query("SELECT * FROM places ORDER BY visitCount DESC")
    fun getAllPlacesPaged(): PagingSource<Int, PlaceEntity>
    
    @Query("SELECT * FROM places WHERE category = :category ORDER BY visitCount DESC")
    fun getPlacesByCategory(category: PlaceCategory): Flow<List<PlaceEntity>>
    
    @Query("SELECT * FROM places ORDER BY visitCount DESC LIMIT :limit")
    fun getMostVisitedPlaces(limit: Int = 10): Flow<List<PlaceEntity>>
    
    @Query("SELECT * FROM places ORDER BY totalTimeSpent DESC LIMIT :limit")
    fun getPlacesWithMostTime(limit: Int = 10): Flow<List<PlaceEntity>>
    
    @Query("""
        SELECT * FROM places 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLng AND :maxLng
    """)
    suspend fun getPlacesNearLocation(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<PlaceEntity>
    
    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlaceById(id: Long): PlaceEntity?
    
    @Query("SELECT * FROM places WHERE placeId = :googlePlaceId")
    suspend fun getPlaceByGoogleId(googlePlaceId: String): PlaceEntity?
    
    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long
    
    @Update
    suspend fun updatePlace(place: PlaceEntity)
    
    @Delete
    suspend fun deletePlace(place: PlaceEntity)
    
    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlaceById(id: Long)
    
    @Query("UPDATE places SET visitCount = visitCount + 1, totalTimeSpent = totalTimeSpent + :duration WHERE id = :id")
    suspend fun incrementVisitStats(id: Long, duration: Long)
    
    @Query("SELECT COUNT(*) FROM places WHERE category = :category")
    suspend fun getPlaceCountByCategory(category: PlaceCategory): Int

    /**
     * Get places visited on a specific date
     * Filters places by JOIN with visits table to show only places visited on the selected date
     * CRITICAL: Fixes map showing all places instead of date-specific places
     */
    @Query("""
        SELECT DISTINCT p.* FROM places p
        INNER JOIN visits v ON p.id = v.placeId
        WHERE v.entryTime >= :startOfDay AND v.entryTime < :endOfDay
        ORDER BY p.lastVisit DESC
    """)
    suspend fun getPlacesVisitedOnDate(
        startOfDay: String,
        endOfDay: String
    ): List<PlaceEntity>
}