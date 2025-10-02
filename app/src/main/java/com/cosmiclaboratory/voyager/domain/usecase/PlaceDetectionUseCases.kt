package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceDetectionUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository
) {
    
    suspend fun detectNewPlaces(): List<Place> {
        val recentLocations = locationRepository.getRecentLocations(10000).first()
        
        if (recentLocations.isEmpty()) return emptyList()
        
        val locationPairs = recentLocations.map { it.latitude to it.longitude }
        val clusters = LocationUtils.clusterLocations(
            locationPairs,
            maxDistanceMeters = 100.0,
            minPoints = 5
        )
        
        val newPlaces = mutableListOf<Place>()
        
        clusters.forEach { cluster ->
            val centerLat = cluster.map { it.first }.average()
            val centerLng = cluster.map { it.second }.average()
            
            val nearbyPlaces = placeRepository.getPlacesNearLocation(
                centerLat, centerLng, 0.15 // 150m radius
            )
            
            if (nearbyPlaces.isEmpty()) {
                val locationsInCluster = recentLocations.filter { location ->
                    cluster.any { clusterPoint ->
                        LocationUtils.calculateDistance(
                            location.latitude, location.longitude,
                            clusterPoint.first, clusterPoint.second
                        ) <= 100.0
                    }
                }
                
                val category = categorizePlace(locationsInCluster)
                val placeName = generatePlaceName(category, centerLat, centerLng)
                
                val place = Place(
                    name = placeName,
                    category = category,
                    latitude = centerLat,
                    longitude = centerLng,
                    visitCount = 1,
                    radius = calculateOptimalRadius(cluster),
                    isCustom = false,
                    confidence = calculateConfidence(locationsInCluster, category)
                )
                
                val placeId = placeRepository.insertPlace(place)
                newPlaces.add(place.copy(id = placeId))
                
                // Create initial visit records for this place
                createInitialVisits(placeId, locationsInCluster)
            }
        }
        
        return newPlaces
    }
    
    suspend fun improveExistingPlaces() {
        val allPlaces = placeRepository.getAllPlaces().first()
        
        allPlaces.forEach { place ->
            if (place.confidence < 0.8) {
                val recentLocations = locationRepository.getLocationsInBounds(
                    place.latitude - 0.002, place.latitude + 0.002,
                    place.longitude - 0.002, place.longitude + 0.002
                )
                
                if (recentLocations.isNotEmpty()) {
                    val improvedCategory = categorizePlace(recentLocations)
                    val improvedConfidence = calculateConfidence(recentLocations, improvedCategory)
                    
                    if (improvedConfidence > place.confidence) {
                        placeRepository.updatePlace(
                            place.copy(
                                category = improvedCategory,
                                confidence = improvedConfidence,
                                name = if (place.isCustom) place.name else generatePlaceName(improvedCategory, place.latitude, place.longitude)
                            )
                        )
                    }
                }
            }
        }
    }
    
    private suspend fun categorizePlace(locations: List<Location>): PlaceCategory {
        if (locations.isEmpty()) return PlaceCategory.UNKNOWN
        
        val hourCounts = locations.groupBy { it.timestamp.hour }
            .mapValues { it.value.size }
        
        val dayOfWeekCounts = locations.groupBy { it.timestamp.dayOfWeek }
            .mapValues { it.value.size }
        
        val nightHours = hourCounts.filterKeys { it >= 22 || it <= 6 }.values.sum()
        val morningHours = hourCounts.filterKeys { it in 7..9 }.values.sum()
        val workHours = hourCounts.filterKeys { it in 9..17 }.values.sum()
        val eveningHours = hourCounts.filterKeys { it in 18..21 }.values.sum()
        
        val weekdayCount = dayOfWeekCounts.filterKeys { 
            it in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        }.values.sum()
        
        val weekendCount = dayOfWeekCounts.filterKeys { 
            it in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }.values.sum()
        
        val totalCount = locations.size
        
        return when {
            // Home: Most activity during night/evening hours
            (nightHours + eveningHours) > totalCount * 0.6 -> PlaceCategory.HOME
            
            // Work: Most activity during work hours on weekdays
            workHours > totalCount * 0.5 && weekdayCount > weekendCount * 1.5 -> PlaceCategory.WORK
            
            // Gym: Activity patterns suggest regular workout times
            isGymPattern(hourCounts, dayOfWeekCounts) -> PlaceCategory.GYM
            
            // Shopping: Short visits during day hours, especially weekends
            isShoppingPattern(locations) -> PlaceCategory.SHOPPING
            
            // Restaurant: Meal time patterns
            isRestaurantPattern(hourCounts) -> PlaceCategory.RESTAURANT
            
            else -> PlaceCategory.UNKNOWN
        }
    }
    
    private fun isGymPattern(hourCounts: Map<Int, Int>, dayOfWeekCounts: Map<DayOfWeek, Int>): Boolean {
        val morningWorkout = hourCounts.filterKeys { it in 6..9 }.values.sum()
        val eveningWorkout = hourCounts.filterKeys { it in 17..20 }.values.sum()
        val totalCount = hourCounts.values.sum()
        
        return (morningWorkout + eveningWorkout) > totalCount * 0.7
    }
    
    private suspend fun isShoppingPattern(locations: List<Location>): Boolean {
        if (locations.isEmpty()) return false
        
        val avgDuration = calculateAverageStayDuration(locations)
        return avgDuration in 30..120 // 30 minutes to 2 hours typical shopping duration
    }
    
    private fun isRestaurantPattern(hourCounts: Map<Int, Int>): Boolean {
        val mealTimes = hourCounts.filterKeys { it in 11..14 || it in 18..21 }.values.sum()
        val totalCount = hourCounts.values.sum()
        
        return mealTimes > totalCount * 0.6
    }
    
    private suspend fun calculateAverageStayDuration(locations: List<Location>): Long {
        if (locations.size < 2) return 0
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        val durations = mutableListOf<Long>()
        
        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()
            
            if (timeDiff > 30) { // Session break if more than 30 minutes gap
                val sessionDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
                if (sessionDuration > 5) { // Only count sessions longer than 5 minutes
                    durations.add(sessionDuration)
                }
                sessionStart = current.timestamp
            }
            lastLocation = current
        }
        
        // Add final session
        val finalDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
        if (finalDuration > 5) {
            durations.add(finalDuration)
        }
        
        return if (durations.isNotEmpty()) durations.average().toLong() else 0
    }
    
    private fun calculateConfidence(locations: List<Location>, category: PlaceCategory): Float {
        if (locations.isEmpty()) return 0.0f
        
        val baseConfidence = when (category) {
            PlaceCategory.HOME -> 0.8f
            PlaceCategory.WORK -> 0.7f
            PlaceCategory.GYM -> 0.6f
            PlaceCategory.RESTAURANT -> 0.5f
            PlaceCategory.SHOPPING -> 0.5f
            else -> 0.3f
        }
        
        val locationCount = locations.size
        val countBonus = minOf(locationCount / 50.0f, 0.2f) // Up to 0.2 bonus for many locations
        
        return minOf(baseConfidence + countBonus, 1.0f)
    }
    
    private fun generatePlaceName(category: PlaceCategory, lat: Double, lng: Double): String {
        val locationHash = ((lat + lng) * 1000).toInt().toString().takeLast(3)
        
        return when (category) {
            PlaceCategory.HOME -> "Home"
            PlaceCategory.WORK -> "Work"
            PlaceCategory.GYM -> "Gym $locationHash"
            PlaceCategory.RESTAURANT -> "Restaurant $locationHash"
            PlaceCategory.SHOPPING -> "Store $locationHash"
            else -> "Place $locationHash"
        }
    }
    
    private fun calculateOptimalRadius(cluster: List<Pair<Double, Double>>): Double {
        if (cluster.size < 2) return 50.0
        
        val distances = mutableListOf<Double>()
        val center = cluster.map { it.first }.average() to cluster.map { it.second }.average()
        
        cluster.forEach { point ->
            val distance = LocationUtils.calculateDistance(
                center.first, center.second,
                point.first, point.second
            )
            distances.add(distance)
        }
        
        // Use 95th percentile as radius to include most points
        distances.sort()
        val index = (distances.size * 0.95).toInt()
        return maxOf(distances.getOrElse(index) { 50.0 }, 30.0) // Minimum 30m radius
    }
    
    private suspend fun createInitialVisits(placeId: Long, locations: List<Location>) {
        if (locations.isEmpty()) return
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        val visits = mutableListOf<Visit>()
        
        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()
            
            if (timeDiff > 30) { // Session break
                val visit = Visit(
                    placeId = placeId,
                    entryTime = sessionStart,
                    exitTime = lastLocation.timestamp
                )
                visits.add(visit)
                sessionStart = current.timestamp
            }
            lastLocation = current
        }
        
        // Add final visit
        val finalVisit = Visit(
            placeId = placeId,
            entryTime = sessionStart,
            exitTime = lastLocation.timestamp
        )
        visits.add(finalVisit)
        
        // Insert visits with minimum duration filter
        visits.filter { 
            java.time.Duration.between(it.entryTime, it.exitTime).toMinutes() >= 5 
        }.forEach { visit ->
            visitRepository.insertVisit(visit)
        }
    }
}