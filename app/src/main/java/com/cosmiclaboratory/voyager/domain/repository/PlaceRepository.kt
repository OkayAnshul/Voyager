package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.PlaceWithVisits
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    
    fun getAllPlaces(): Flow<List<Place>>
    
    fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>>
    
    fun getMostVisitedPlaces(limit: Int = 10): Flow<List<Place>>
    
    fun getPlacesWithMostTime(limit: Int = 10): Flow<List<Place>>
    
    suspend fun getPlacesNearLocation(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 1.0
    ): List<Place>
    
    suspend fun getPlaceById(id: Long): Place?
    
    suspend fun getPlaceByGoogleId(googlePlaceId: String): Place?
    
    suspend fun insertPlace(place: Place): Long
    
    suspend fun updatePlace(place: Place)
    
    suspend fun deletePlace(place: Place)
    
    suspend fun deletePlaceById(id: Long)
    
    suspend fun incrementVisitStats(id: Long, duration: Long)
    
    suspend fun getPlaceCountByCategory(category: PlaceCategory): Int
    
    suspend fun getPlaceWithVisits(placeId: Long): PlaceWithVisits?
    
    suspend fun findOrCreatePlace(
        latitude: Double,
        longitude: Double,
        name: String,
        category: PlaceCategory = PlaceCategory.UNKNOWN
    ): Place

    // Phase 3: Manual place naming
    suspend fun renamePlace(
        placeId: Long,
        newName: String,
        newCategory: PlaceCategory? = null
    )

    /**
     * Get places visited on a specific date
     * Returns only places that have visits on the given date
     * CRITICAL: Fixes map showing all places instead of date-specific places
     */
    suspend fun getPlacesVisitedOnDate(
        startOfDay: java.time.LocalDateTime,
        endOfDay: java.time.LocalDateTime
    ): List<Place>
}