package com.cosmiclaboratory.voyager.domain.usecase

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
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
    private val visitRepository: VisitRepository,
    private val preferencesRepository: PreferencesRepository
) {
    
    companion object {
        private const val TAG = "PlaceDetectionUseCases"
    }
    
    suspend fun detectNewPlaces(): List<Place> {
        val preferences = preferencesRepository.getCurrentPreferences()
        Log.d(TAG, "Starting place detection with preferences: " +
            "placeDetectionEnabled=${preferences.enablePlaceDetection}, " +
            "maxAccuracy=${preferences.maxGpsAccuracyMeters}m, " +
            "maxSpeed=${preferences.maxSpeedKmh}km/h")
        
        if (!preferences.enablePlaceDetection) {
            Log.d(TAG, "Place detection is disabled in preferences")
            return emptyList()
        }
        
        // Limit processing to prevent OOM on older devices
        val maxLocationsToProcess = minOf(preferences.maxLocationsToProcess, 5000)
        val recentLocations = locationRepository.getRecentLocations(maxLocationsToProcess).first()
        Log.d(TAG, "Retrieved ${recentLocations.size} recent locations for processing (limited to $maxLocationsToProcess)")
        
        if (recentLocations.isEmpty()) {
            Log.d(TAG, "No recent locations found")
            return emptyList()
        }
        
        // Process in batches to manage memory usage
        val batchSize = preferences.dataProcessingBatchSize.coerceIn(100, 1000)
        val allFilteredLocations = mutableListOf<Location>()
        
        recentLocations.chunked(batchSize).forEach { batch ->
            val filteredBatch = filterLocationsByQuality(batch, preferences)
            allFilteredLocations.addAll(filteredBatch)
        }
        
        Log.d(TAG, "Filtered to ${allFilteredLocations.size} quality locations (${recentLocations.size - allFilteredLocations.size} removed)")
        
        if (allFilteredLocations.isEmpty()) {
            Log.d(TAG, "No locations passed quality filtering")
            return emptyList()
        }
        
        // Limit clustering to prevent performance issues
        val locationsToCluster = if (allFilteredLocations.size > 2000) {
            Log.d(TAG, "Limiting locations for clustering from ${allFilteredLocations.size} to 2000 for performance")
            allFilteredLocations.takeLast(2000) // Use most recent locations
        } else {
            allFilteredLocations
        }
        
        val locationPairs = locationsToCluster.map { it.latitude to it.longitude }
        val clusters = LocationUtils.clusterLocationsWithPreferences(locationPairs, preferences)
        Log.d(TAG, "Found ${clusters.size} location clusters using DBSCAN with " +
            "distance=${preferences.clusteringDistanceMeters}m, " +
            "minPoints=${preferences.minPointsForCluster}")
        
        val newPlaces = mutableListOf<Place>()
        
        // Process clusters in batches to manage memory and database operations
        clusters.chunked(10).forEach { clusterBatch ->
            clusterBatch.forEach { cluster ->
                try {
                    val centerLat = cluster.map { it.first }.average()
                    val centerLng = cluster.map { it.second }.average()
                    
                    val nearbyPlaces = placeRepository.getPlacesNearLocation(
                        centerLat, centerLng, preferences.placeDetectionRadius / 1000.0 // Convert to km
                    )
                    
                    if (nearbyPlaces.isEmpty()) {
                        val locationsInCluster = allFilteredLocations.filter { location ->
                            cluster.any { clusterPoint ->
                                LocationUtils.calculateDistance(
                                    location.latitude, location.longitude,
                                    clusterPoint.first, clusterPoint.second
                                ) <= preferences.placeDetectionRadius
                            }
                        }
                        
                        if (locationsInCluster.size >= preferences.minPointsForCluster) {
                            val category = categorizePlace(locationsInCluster, preferences)
                            val placeName = generatePlaceName(category, centerLat, centerLng)
                            
                            val place = Place(
                                name = placeName,
                                category = category,
                                latitude = centerLat,
                                longitude = centerLng,
                                visitCount = 1,
                                radius = calculateOptimalRadius(cluster),
                                isCustom = false,
                                confidence = calculateConfidence(locationsInCluster, category, preferences)
                            )
                            
                            val placeId = placeRepository.insertPlace(place)
                            newPlaces.add(place.copy(id = placeId))
                            Log.d(TAG, "Created new place: $placeName at ($centerLat, $centerLng) " +
                                "with confidence ${String.format("%.2f", place.confidence)} and ${locationsInCluster.size} locations")
                            
                            // Create initial visit records for this place
                            createInitialVisits(placeId, locationsInCluster, preferences)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cluster: ${e.message}", e)
                    // Continue with next cluster instead of failing completely
                }
            }
        }
        
        Log.d(TAG, "Place detection completed: created ${newPlaces.size} new places")
        return newPlaces
    }
    
    suspend fun improveExistingPlaces() {
        val preferences = preferencesRepository.getCurrentPreferences()
        val allPlaces = placeRepository.getAllPlaces().first()
        
        allPlaces.forEach { place ->
            if (place.confidence < preferences.autoConfidenceThreshold) {
                val recentLocations = locationRepository.getLocationsInBounds(
                    place.latitude - 0.002, place.latitude + 0.002,
                    place.longitude - 0.002, place.longitude + 0.002
                )
                
                if (recentLocations.isNotEmpty()) {
                    val improvedCategory = categorizePlace(recentLocations, preferences)
                    val improvedConfidence = calculateConfidence(recentLocations, improvedCategory, preferences)
                    
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
    
    private suspend fun categorizePlace(locations: List<Location>, preferences: UserPreferences): PlaceCategory {
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
            (nightHours + eveningHours) > totalCount * preferences.homeNightActivityThreshold -> PlaceCategory.HOME
            
            // Work: Most activity during work hours on weekdays
            workHours > totalCount * preferences.workHoursActivityThreshold && weekdayCount > weekendCount * 1.5 -> PlaceCategory.WORK
            
            // Gym: Activity patterns suggest regular workout times
            isGymPattern(hourCounts, dayOfWeekCounts, preferences) -> PlaceCategory.GYM
            
            // Shopping: Short visits during day hours, especially weekends
            isShoppingPattern(locations, preferences) -> PlaceCategory.SHOPPING
            
            // Restaurant: Meal time patterns
            isRestaurantPattern(hourCounts, preferences) -> PlaceCategory.RESTAURANT
            
            else -> PlaceCategory.UNKNOWN
        }
    }
    
    private fun isGymPattern(hourCounts: Map<Int, Int>, dayOfWeekCounts: Map<DayOfWeek, Int>, preferences: UserPreferences): Boolean {
        val morningWorkout = hourCounts.filterKeys { it in 6..9 }.values.sum()
        val eveningWorkout = hourCounts.filterKeys { it in 17..20 }.values.sum()
        val totalCount = hourCounts.values.sum()
        
        return (morningWorkout + eveningWorkout) > totalCount * preferences.gymActivityThreshold
    }
    
    private suspend fun isShoppingPattern(locations: List<Location>, preferences: UserPreferences): Boolean {
        if (locations.isEmpty()) return false
        
        val avgDuration = calculateAverageStayDuration(locations, preferences)
        return avgDuration in preferences.shoppingMinDurationMinutes..preferences.shoppingMaxDurationMinutes
    }
    
    private fun isRestaurantPattern(hourCounts: Map<Int, Int>, preferences: UserPreferences): Boolean {
        val mealTimes = hourCounts.filterKeys { it in 11..14 || it in 18..21 }.values.sum()
        val totalCount = hourCounts.values.sum()
        
        return mealTimes > totalCount * preferences.restaurantMealTimeThreshold
    }
    
    private suspend fun calculateAverageStayDuration(locations: List<Location>, preferences: UserPreferences): Long {
        if (locations.size < 2) return 0
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        val durations = mutableListOf<Long>()
        
        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()
            
            if (timeDiff > preferences.sessionBreakTimeMinutes) { // Session break based on user preference
                val sessionDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
                if (sessionDuration > preferences.minVisitDurationMinutes) { // Only count sessions longer than minimum duration
                    durations.add(sessionDuration)
                }
                sessionStart = current.timestamp
            }
            lastLocation = current
        }
        
        // Add final session
        val finalDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
        if (finalDuration > preferences.minVisitDurationMinutes) {
            durations.add(finalDuration)
        }
        
        return if (durations.isNotEmpty()) durations.average().toLong() else 0
    }
    
    private fun calculateConfidence(locations: List<Location>, category: PlaceCategory, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0.0f
        
        val baseConfidence = when (category) {
            PlaceCategory.HOME -> 0.7f
            PlaceCategory.WORK -> 0.6f
            PlaceCategory.GYM -> 0.5f
            PlaceCategory.RESTAURANT -> 0.4f
            PlaceCategory.SHOPPING -> 0.4f
            else -> 0.2f
        }
        
        val locationCount = locations.size
        val countBonus = minOf(locationCount / 30.0f, 0.25f) // Up to 0.25 bonus for many locations
        
        // Factor in location accuracy - better accuracy increases confidence
        val avgAccuracy = locations.map { it.accuracy }.average()
        val accuracyBonus = when {
            avgAccuracy <= 10f -> 0.15f // Very accurate GPS
            avgAccuracy <= 25f -> 0.1f  // Good GPS
            avgAccuracy <= 50f -> 0.05f // Moderate GPS
            else -> 0.0f // Poor GPS gets no bonus
        }
        
        // Factor in time span - places visited over longer periods are more confident
        val timeSpanHours = if (locations.size > 1) {
            val sortedLocations = locations.sortedBy { it.timestamp }
            java.time.Duration.between(
                sortedLocations.first().timestamp,
                sortedLocations.last().timestamp
            ).toHours()
        } else 0L
        
        val timeSpanBonus = when {
            timeSpanHours >= 24 * 7 -> 0.1f  // Week or more
            timeSpanHours >= 24 -> 0.05f     // Day or more
            else -> 0.0f
        }
        
        val finalConfidence = baseConfidence + countBonus + accuracyBonus + timeSpanBonus
        return minOf(finalConfidence, 0.95f) // Cap at 95% to leave room for improvement
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
        
        if (distances.isEmpty()) return 50.0
        
        // Use 90th percentile as radius to include most points, but handle edge cases better
        distances.sort()
        val index = maxOf(0, minOf(distances.size - 1, (distances.size * 0.9).toInt()))
        val radius = distances[index]
        
        // Ensure reasonable radius bounds: minimum 25m, maximum 200m
        return radius.coerceIn(25.0, 200.0)
    }
    
    private suspend fun createInitialVisits(placeId: Long, locations: List<Location>, preferences: UserPreferences) {
        if (locations.isEmpty()) return
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        val visits = mutableListOf<Visit>()
        
        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()
            
            if (timeDiff > preferences.sessionBreakTimeMinutes) { // Session break based on user preference
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
        
        // Insert visits with minimum duration filter based on user preference
        visits.filter { 
            java.time.Duration.between(it.entryTime, it.exitTime).toMinutes() >= preferences.minVisitDurationMinutes 
        }.forEach { visit ->
            visitRepository.insertVisit(visit)
        }
    }
    
    /**
     * Filter locations by GPS accuracy and remove outliers
     */
    private fun filterLocationsByQuality(locations: List<Location>, preferences: UserPreferences): List<Location> {
        if (locations.isEmpty()) return emptyList()
        
        Log.d(TAG, "Filtering ${locations.size} locations with user preferences: " +
            "maxAccuracy=${preferences.maxGpsAccuracyMeters}m, " +
            "maxSpeed=${preferences.maxSpeedKmh}km/h, " +
            "minTimeGap=${preferences.minTimeBetweenUpdatesSeconds}s")
        
        val maxAccuracyMeters = preferences.maxGpsAccuracyMeters
        val maxSpeedKmh = preferences.maxSpeedKmh
        
        // First filter by accuracy
        val accurateLocations = locations.filter { location ->
            location.accuracy <= maxAccuracyMeters
        }
        
        if (accurateLocations.size < 2) return accurateLocations
        
        // Sort by timestamp for sequential processing
        val sortedLocations = accurateLocations.sortedBy { it.timestamp }
        val filteredLocations = mutableListOf<Location>()
        
        filteredLocations.add(sortedLocations.first())
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val previous = filteredLocations.last()
            
            // Calculate time difference in seconds
            val timeDiffSeconds = java.time.Duration.between(previous.timestamp, current.timestamp).seconds
            
            if (timeDiffSeconds > 0) {
                // Calculate distance and speed
                val distanceMeters = LocationUtils.calculateDistance(
                    previous.latitude, previous.longitude,
                    current.latitude, current.longitude
                )
                
                val speedMps = distanceMeters / timeDiffSeconds // meters per second
                val speedKmh = speedMps * 3.6 // Convert m/s to km/h
                
                // Filter out locations with impossible speeds (likely GPS errors)
                if (speedKmh <= maxSpeedKmh) {
                    // Also filter out locations that are too close in time and space (GPS jitter)
                    val minMovement = 5.0 // meters (keep this fixed as it's for GPS jitter)
                    val minTimeGap = preferences.minTimeBetweenUpdatesSeconds // Use user preference
                    
                    if (distanceMeters >= minMovement || timeDiffSeconds >= minTimeGap) {
                        filteredLocations.add(current)
                    }
                } else {
                    Log.d(TAG, "Location filtered out: impossible speed ${speedKmh}km/h > ${maxSpeedKmh}km/h")
                }
            }
        }
        
        return filteredLocations
    }
}