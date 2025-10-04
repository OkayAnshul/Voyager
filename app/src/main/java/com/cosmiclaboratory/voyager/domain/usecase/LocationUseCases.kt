package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import com.cosmiclaboratory.voyager.utils.LocationUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationServiceManager: LocationServiceManager
) {
    
    suspend fun startLocationTracking() {
        try {
            locationServiceManager.startLocationTracking()
        } catch (e: Exception) {
            android.util.Log.e("LocationUseCases", "Failed to start location tracking", e)
            throw e
        }
    }
    
    suspend fun stopLocationTracking() {
        try {
            locationServiceManager.stopLocationTracking()
        } catch (e: Exception) {
            android.util.Log.e("LocationUseCases", "Failed to stop location tracking", e)
            throw e
        }
    }
    
    fun isLocationTrackingActive(): Flow<Boolean> {
        return locationServiceManager.isServiceRunning
    }
    
    suspend fun getRecentLocations(limit: Int = 1000): List<Location> {
        return try {
            // Get the first emission from the Flow (current data)
            locationRepository.getRecentLocations(limit).first()
        } catch (e: Exception) {
            // Return empty list if there's an error or no data
            emptyList()
        }
    }
    
    suspend fun getLocationsSince(since: LocalDateTime): List<Location> {
        return locationRepository.getLocationsSince(since)
    }
    
    suspend fun getTotalLocationCount(): Int {
        return locationRepository.getLocationCount()
    }
    
    suspend fun getLocationsForDay(date: LocalDateTime): List<Location> {
        val startOfDay = date.toLocalDate().atStartOfDay()
        val endOfDay = startOfDay.plusDays(1)
        
        return locationRepository.getLocationsSince(startOfDay)
            .filter { it.timestamp.isBefore(endOfDay) }
    }
    
    suspend fun getLocationClusters(
        locations: List<Location>,
        maxDistanceMeters: Double = 50.0
    ): List<List<Location>> {
        val locationPairs = locations.map { it.latitude to it.longitude }
        val clusters = LocationUtils.clusterLocations(locationPairs, maxDistanceMeters)
        
        return clusters.map { cluster ->
            locations.filter { location ->
                cluster.contains(location.latitude to location.longitude)
            }
        }
    }
    
    suspend fun calculateDistanceTraveled(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Double {
        val locations = locationRepository.getLocationsSince(startTime)
            .filter { it.timestamp.isBefore(endTime) }
            .sortedBy { it.timestamp }
        
        if (locations.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until locations.size) {
            val prev = locations[i - 1]
            val current = locations[i]
            totalDistance += LocationUtils.calculateDistance(
                prev.latitude, prev.longitude,
                current.latitude, current.longitude
            )
        }
        
        return totalDistance
    }
    
    suspend fun getLocationAccuracyStats(): LocationAccuracyStats {
        val recentLocations = getRecentLocations(1000)
        
        if (recentLocations.isEmpty()) {
            return LocationAccuracyStats(0f, 0f, 0f, 0)
        }
        
        val accuracies = recentLocations.map { it.accuracy }
        return LocationAccuracyStats(
            averageAccuracy = accuracies.average().toFloat(),
            bestAccuracy = accuracies.minOrNull() ?: 0f,
            worstAccuracy = accuracies.maxOrNull() ?: 0f,
            totalPoints = recentLocations.size
        )
    }
}

data class LocationAccuracyStats(
    val averageAccuracy: Float,
    val bestAccuracy: Float,
    val worstAccuracy: Float,
    val totalPoints: Int
)