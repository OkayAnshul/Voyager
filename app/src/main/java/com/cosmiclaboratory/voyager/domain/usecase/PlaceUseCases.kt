package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceUseCases @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
    private val visitRepository: VisitRepository
) {
    
    suspend fun detectPlacesFromLocations(): List<Place> {
        val recentLocations = locationRepository.getRecentLocations(5000).first()
        val clusters = LocationUtils.clusterLocations(
            recentLocations.map { it.latitude to it.longitude },
            maxDistanceMeters = 100.0
        )
        
        val detectedPlaces = mutableListOf<Place>()
        
        clusters.forEach { cluster ->
            if (cluster.size >= 3) { // Only consider clusters with 3+ points
                val centerLat = cluster.map { it.first }.average()
                val centerLng = cluster.map { it.second }.average()
                
                // Check if place already exists nearby
                val nearbyPlaces = placeRepository.getPlacesNearLocation(
                    centerLat, centerLng, 0.1
                )
                
                if (nearbyPlaces.isEmpty()) {
                    val category = categorizePlace(centerLat, centerLng, cluster.size)
                    val place = Place(
                        name = generatePlaceName(category),
                        category = category,
                        latitude = centerLat,
                        longitude = centerLng,
                        visitCount = 1,
                        radius = 100.0,
                        isCustom = false
                    )
                    
                    val placeId = placeRepository.insertPlace(place)
                    detectedPlaces.add(place.copy(id = placeId))
                }
            }
        }
        
        return detectedPlaces
    }
    
    private suspend fun categorizePlace(
        latitude: Double,
        longitude: Double,
        visitCount: Int
    ): PlaceCategory {
        // Get all locations at this place grouped by hour
        val nearbyLocations = locationRepository.getLocationsInBounds(
            latitude - 0.001, latitude + 0.001,
            longitude - 0.001, longitude + 0.001
        )
        
        val hourCounts = nearbyLocations.groupBy { it.timestamp.hour }
            .mapValues { it.value.size }
        
        // Categorize based on time patterns
        return when {
            // Home: Most activity between 6PM and 8AM
            hourCounts.filterKeys { it >= 18 || it <= 8 }.values.sum() > 
            hourCounts.filterKeys { it in 9..17 }.values.sum() -> PlaceCategory.HOME
            
            // Work: Most activity between 9AM and 5PM on weekdays
            hourCounts.filterKeys { it in 9..17 }.values.sum() > 
            hourCounts.filterKeys { it >= 18 || it <= 8 }.values.sum() -> PlaceCategory.WORK
            
            // Default to unknown for now
            else -> PlaceCategory.UNKNOWN
        }
    }
    
    private fun generatePlaceName(category: PlaceCategory): String {
        return when (category) {
            PlaceCategory.HOME -> "Home"
            PlaceCategory.WORK -> "Work"
            PlaceCategory.GYM -> "Gym"
            PlaceCategory.RESTAURANT -> "Restaurant"
            PlaceCategory.SHOPPING -> "Store"
            else -> "Unknown Place"
        }
    }
    
    suspend fun getAllPlaces(): Flow<List<Place>> {
        return placeRepository.getAllPlaces()
    }
    
    suspend fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>> {
        return placeRepository.getPlacesByCategory(category)
    }
    
    suspend fun getMostVisitedPlaces(limit: Int = 10): Flow<List<Place>> {
        return placeRepository.getMostVisitedPlaces(limit)
    }
    
    suspend fun updatePlaceCategory(placeId: Long, newCategory: PlaceCategory) {
        placeRepository.getPlaceById(placeId)?.let { place ->
            val updatedPlace = place.copy(category = newCategory)
            placeRepository.updatePlace(updatedPlace)
        }
    }
    
    suspend fun calculatePlaceStatistics(placeId: Long): PlaceStatistics? {
        val place = placeRepository.getPlaceById(placeId) ?: return null
        val visits = visitRepository.getVisitsForPlace(placeId).first()
        
        if (visits.isEmpty()) {
            return PlaceStatistics(
                place = place,
                totalVisits = 0,
                totalTimeSpent = 0L,
                averageStayDuration = 0L,
                longestStay = 0L,
                shortestStay = 0L,
                lastVisit = null
            )
        }
        
        val completedVisits = visits.filter { it.exitTime != null }
        val durations = completedVisits.map { it.duration }
        
        return PlaceStatistics(
            place = place,
            totalVisits = visits.size,
            totalTimeSpent = durations.sum(),
            averageStayDuration = if (durations.isNotEmpty()) durations.average().toLong() else 0L,
            longestStay = durations.maxOrNull() ?: 0L,
            shortestStay = durations.minOrNull() ?: 0L,
            lastVisit = visits.maxByOrNull { it.entryTime }?.entryTime
        )
    }
    
    suspend fun getPlacesWithTimeFilter(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Place> {
        // Get all visits in the time range
        val visits = visitRepository.getVisitsBetween(startTime, endTime).first()
        val placeIds = visits.map { it.placeId }.distinct()
        
        return placeIds.mapNotNull { placeId ->
            placeRepository.getPlaceById(placeId)
        }
    }
}

data class PlaceStatistics(
    val place: Place,
    val totalVisits: Int,
    val totalTimeSpent: Long, // in milliseconds
    val averageStayDuration: Long,
    val longestStay: Long,
    val shortestStay: Long,
    val lastVisit: LocalDateTime?
)