package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.Location
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface LocationRepository {
    
    fun getRecentLocations(limit: Int = 1000): Flow<List<Location>>
    
    fun getLocationsBetween(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<Location>>
    
    suspend fun getLocationsSince(since: LocalDateTime): List<Location>
    
    suspend fun getLocationCount(): Int
    
    suspend fun getLastLocation(): Location?
    
    suspend fun insertLocation(location: Location): Long
    
    suspend fun insertLocations(locations: List<Location>)
    
    suspend fun deleteLocation(location: Location)
    
    suspend fun deleteLocationsBefore(beforeDate: LocalDateTime): Int
    
    suspend fun deleteAllLocations()
    
    suspend fun getLocationsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<Location>
}