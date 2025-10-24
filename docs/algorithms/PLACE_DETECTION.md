# Place Detection Algorithm Deep Dive

This guide explains Voyager's sophisticated place detection system that automatically identifies significant locations from GPS data using advanced clustering and pattern analysis.

## 🎯 Algorithm Overview

Voyager's place detection combines multiple techniques:

1. **DBSCAN Clustering** - Groups nearby GPS points
2. **Temporal Pattern Analysis** - Analyzes time-based usage patterns  
3. **Statistical Filtering** - Removes GPS noise and outliers
4. **Confidence Scoring** - Rates detection reliability
5. **Continuous Learning** - Improves accuracy over time

```
GPS Points → Filtering → Clustering → Pattern Analysis → Place Creation
    ↓           ↓           ↓              ↓              ↓
Raw Data → Quality Data → Location → Usage Patterns → Categorized
           (Accuracy,     Clusters    (Time, Duration)  Places
            Speed)                                      (Home, Work)
```

## 🔍 DBSCAN Clustering Algorithm

### Why DBSCAN?

**DBSCAN (Density-Based Spatial Clustering of Applications with Noise)** is ideal for location data because:

#### Advantages over Other Algorithms:

| Algorithm | Pros | Cons | Fit for GPS |
|-----------|------|------|-------------|
| **K-means** | Fast, simple | Requires cluster count, sensitive to outliers | ❌ Poor |
| **Hierarchical** | No cluster count needed | Slow O(n³), memory intensive | ⚠️ Okay |
| **DBSCAN** | Handles noise, arbitrary shapes, no cluster count | Sensitive to parameters | ✅ Excellent |

#### DBSCAN Benefits for GPS Data:

```kotlin
// 1. Handles GPS noise automatically
val noisyPoints = listOf(
    Point(37.7749, -122.4194), // Home cluster
    Point(37.7750, -122.4195), // Home cluster  
    Point(37.8000, -122.5000), // Noise (single erroneous reading)
    Point(37.7751, -122.4193)  // Home cluster
)
// DBSCAN automatically identifies the home cluster and discards noise

// 2. No need to specify number of places beforehand
// 3. Handles irregularly shaped clusters (e.g., linear routes)
// 4. Robust to outliers (GPS drift, urban canyon effects)
```

### DBSCAN Implementation

```kotlin
// File: utils/LocationUtils.kt
object LocationUtils {
    
    /**
     * DBSCAN clustering implementation for GPS coordinates
     * @param points List of (latitude, longitude) pairs
     * @param preferences User settings for clustering parameters
     * @return List of clusters, each containing grouped points
     */
    fun clusterLocationsWithPreferences(
        points: List<Pair<Double, Double>>,
        preferences: UserPreferences
    ): List<List<Pair<Double, Double>>> {
        
        val epsilon = preferences.clusteringDistanceMeters // meters
        val minPoints = preferences.minPointsForCluster    // minimum points per cluster
        
        return dbscan(points, epsilon, minPoints)
    }
    
    private fun dbscan(
        points: List<Pair<Double, Double>>,
        epsilon: Double,
        minPoints: Int
    ): List<List<Pair<Double, Double>>> {
        
        val visited = mutableSetOf<Int>()
        val clusters = mutableListOf<List<Pair<Double, Double>>>()
        
        for (i in points.indices) {
            if (i in visited) continue
            
            visited.add(i)
            val neighbors = getNeighbors(points, i, epsilon)
            
            if (neighbors.size >= minPoints) {
                // Core point - start new cluster
                val cluster = mutableListOf<Pair<Double, Double>>()
                expandCluster(points, i, neighbors, cluster, visited, epsilon, minPoints)
                clusters.add(cluster)
            }
            // Points with < minPoints neighbors are considered noise
        }
        
        return clusters
    }
    
    private fun expandCluster(
        points: List<Pair<Double, Double>>,
        pointIndex: Int,
        neighbors: MutableList<Int>,
        cluster: MutableList<Pair<Double, Double>>,
        visited: MutableSet<Int>,
        epsilon: Double,
        minPoints: Int
    ) {
        cluster.add(points[pointIndex])
        
        var i = 0
        while (i < neighbors.size) {
            val neighborIndex = neighbors[i]
            
            if (neighborIndex !in visited) {
                visited.add(neighborIndex)
                val neighborNeighbors = getNeighbors(points, neighborIndex, epsilon)
                
                if (neighborNeighbors.size >= minPoints) {
                    // This neighbor is also a core point
                    neighbors.addAll(neighborNeighbors)
                }
            }
            
            // Add to cluster if not already in any cluster
            if (!cluster.contains(points[neighborIndex])) {
                cluster.add(points[neighborIndex])
            }
            
            i++
        }
    }
    
    private fun getNeighbors(
        points: List<Pair<Double, Double>>,
        pointIndex: Int,
        epsilon: Double
    ): MutableList<Int> {
        val neighbors = mutableListOf<Int>()
        val currentPoint = points[pointIndex]
        
        for (i in points.indices) {
            if (i != pointIndex) {
                val distance = calculateDistance(
                    currentPoint.first, currentPoint.second,
                    points[i].first, points[i].second
                )
                
                if (distance <= epsilon) {
                    neighbors.add(i)
                }
            }
        }
        
        return neighbors
    }
    
    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     * @return Distance in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(deltaLat / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}
```

### Parameter Tuning

**Critical Parameters:**

1. **Epsilon (ε) - Clustering Distance**
```kotlin
// Default: 100 meters
// Too small: Many small clusters (over-segmentation)  
// Too large: Few large clusters (under-segmentation)

val epsilon = when (environment) {
    Environment.URBAN -> 75.0      // Dense areas, smaller places
    Environment.SUBURBAN -> 100.0  // Standard residential
    Environment.RURAL -> 150.0     // Spread out locations
}
```

2. **MinPoints - Minimum Cluster Size**
```kotlin
// Default: 10 points
// Too small: Noise becomes clusters
// Too large: Miss smaller but significant places

val minPoints = when (visitFrequency) {
    Frequency.DAILY -> 5        // Regular places need fewer points
    Frequency.WEEKLY -> 10      // Standard threshold
    Frequency.MONTHLY -> 20     // Occasional places need more evidence
}
```

## 📊 GPS Data Quality Control

### Multi-Stage Filtering Pipeline

```kotlin
// File: domain/usecase/PlaceDetectionUseCases.kt
private fun filterLocationsByQuality(
    locations: List<Location>, 
    preferences: UserPreferences
): List<Location> {
    
    return locations
        .filter { accuracyFilter(it, preferences) }
        .filter { speedFilter(it, preferences) }
        .filter { temporalFilter(it, preferences) }
        .sortedBy { it.timestamp }
        .let { sequentialFilter(it, preferences) }
}

// 1. Accuracy Filter - Remove inaccurate GPS readings
private fun accuracyFilter(location: Location, preferences: UserPreferences): Boolean {
    return location.accuracy <= preferences.maxGpsAccuracyMeters
}

// 2. Speed Filter - Remove impossible speeds (GPS errors)
private fun speedFilter(location: Location, preferences: UserPreferences): Boolean {
    val previousLocation = getLastLocation()
    if (previousLocation == null) return true
    
    val distance = LocationUtils.calculateDistance(
        previousLocation.latitude, previousLocation.longitude,
        location.latitude, location.longitude
    )
    
    val timeDiff = Duration.between(previousLocation.timestamp, location.timestamp).seconds
    if (timeDiff <= 0) return false
    
    val speedMps = distance / timeDiff
    val speedKmh = speedMps * 3.6
    
    return speedKmh <= preferences.maxSpeedKmh
}

// 3. Temporal Filter - Remove stale data
private fun temporalFilter(location: Location, preferences: UserPreferences): Boolean {
    val ageHours = Duration.between(location.timestamp, LocalDateTime.now()).toHours()
    return ageHours <= preferences.maxLocationAgeHours
}

// 4. Sequential Filter - Remove GPS jitter when stationary
private fun sequentialFilter(
    locations: List<Location>, 
    preferences: UserPreferences
): List<Location> {
    if (locations.size < 2) return locations
    
    val filtered = mutableListOf<Location>()
    filtered.add(locations.first())
    
    for (i in 1 until locations.size) {
        val current = locations[i]
        val previous = filtered.last()
        
        val distance = LocationUtils.calculateDistance(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude
        )
        
        val timeDiff = Duration.between(previous.timestamp, current.timestamp).seconds
        
        // Keep if significant movement OR enough time passed
        if (distance >= preferences.minMovementMeters || 
            timeDiff >= preferences.minTimeBetweenUpdatesSeconds) {
            filtered.add(current)
        }
    }
    
    return filtered
}
```

### Quality Metrics Dashboard

```kotlin
data class LocationQualityReport(
    val totalLocations: Int,
    val filteredByAccuracy: Int,
    val filteredBySpeed: Int,
    val filteredByAge: Int,
    val filteredByJitter: Int,
    val finalCount: Int,
    val qualityScore: Float
) {
    val retentionRate: Float = finalCount.toFloat() / totalLocations
    
    fun getQualityAssessment(): String = when {
        qualityScore >= 0.8f -> "Excellent GPS quality"
        qualityScore >= 0.6f -> "Good GPS quality"  
        qualityScore >= 0.4f -> "Fair GPS quality"
        else -> "Poor GPS quality - check settings"
    }
}
```

## 🧠 Pattern Analysis & Categorization

### Temporal Pattern Analysis

```kotlin
// File: domain/usecase/PlaceDetectionUseCases.kt
private suspend fun categorizePlace(
    locations: List<Location>, 
    preferences: UserPreferences
): PlaceCategory {
    
    val patterns = analyzeTemporalPatterns(locations)
    
    return when {
        isHomePattern(patterns, preferences) -> PlaceCategory.HOME
        isWorkPattern(patterns, preferences) -> PlaceCategory.WORK
        isGymPattern(patterns, preferences) -> PlaceCategory.GYM
        isRestaurantPattern(patterns, preferences) -> PlaceCategory.RESTAURANT
        isShoppingPattern(patterns, preferences) -> PlaceCategory.SHOPPING
        else -> PlaceCategory.UNKNOWN
    }
}

data class TemporalPatterns(
    val hourDistribution: Map<Int, Int>,        // Hour of day → visit count
    val dayOfWeekDistribution: Map<DayOfWeek, Int>, // Day → visit count
    val averageStayDuration: Long,              // Average time spent
    val visitFrequency: Float,                  // Visits per week
    val timeSpanDays: Int                       // Days between first and last visit
)

private fun analyzeTemporalPatterns(locations: List<Location>): TemporalPatterns {
    val hourCounts = locations.groupBy { it.timestamp.hour }
        .mapValues { it.value.size }
    
    val dayOfWeekCounts = locations.groupBy { it.timestamp.dayOfWeek }
        .mapValues { it.value.size }
    
    val timeSpan = Duration.between(
        locations.minOf { it.timestamp },
        locations.maxOf { it.timestamp }
    ).toDays()
    
    val visitFrequency = locations.size.toFloat() / (timeSpan / 7f)
    
    return TemporalPatterns(
        hourDistribution = hourCounts,
        dayOfWeekDistribution = dayOfWeekCounts,
        averageStayDuration = calculateAverageStayDuration(locations),
        visitFrequency = visitFrequency,
        timeSpanDays = timeSpan.toInt()
    )
}
```

### Home Detection Algorithm

```kotlin
private fun isHomePattern(patterns: TemporalPatterns, preferences: UserPreferences): Boolean {
    val nightHours = patterns.hourDistribution
        .filterKeys { it >= 22 || it <= 6 }  // 10 PM to 6 AM
        .values.sum()
    
    val eveningHours = patterns.hourDistribution
        .filterKeys { it in 18..21 }         // 6 PM to 9 PM
        .values.sum()
    
    val totalActivity = patterns.hourDistribution.values.sum()
    val nightActivity = (nightHours + eveningHours).toFloat() / totalActivity
    
    return nightActivity >= preferences.homeNightActivityThreshold &&
           patterns.averageStayDuration >= preferences.homeMinDurationHours * 3600000L &&
           patterns.visitFrequency >= preferences.homeMinVisitsPerWeek
}
```

### Work Detection Algorithm  

```kotlin
private fun isWorkPattern(patterns: TemporalPatterns, preferences: UserPreferences): Boolean {
    val workHours = patterns.hourDistribution
        .filterKeys { it in 9..17 }          // 9 AM to 5 PM
        .values.sum()
    
    val weekdayActivity = patterns.dayOfWeekDistribution
        .filterKeys { it.value in 1..5 }     // Monday to Friday
        .values.sum()
    
    val weekendActivity = patterns.dayOfWeekDistribution
        .filterKeys { it.value in 6..7 }     // Saturday to Sunday
        .values.sum()
    
    val totalActivity = patterns.hourDistribution.values.sum()
    val workDayActivity = workHours.toFloat() / totalActivity
    val weekdayRatio = weekdayActivity.toFloat() / (weekdayActivity + weekendActivity)
    
    return workDayActivity >= preferences.workHoursActivityThreshold &&
           weekdayRatio >= 0.7f &&  // 70% of activity on weekdays
           patterns.averageStayDuration >= preferences.workMinDurationHours * 3600000L
}
```

### Gym Detection Algorithm

```kotlin
private fun isGymPattern(patterns: TemporalPatterns, preferences: UserPreferences): Boolean {
    // Gyms typically have:
    // 1. Regular schedule (same times/days)
    // 2. Medium duration visits (1-3 hours)
    // 3. Early morning or evening visits
    
    val morningWorkout = patterns.hourDistribution
        .filterKeys { it in 6..9 }           // 6 AM to 9 AM
        .values.sum()
    
    val eveningWorkout = patterns.hourDistribution
        .filterKeys { it in 17..20 }         // 5 PM to 8 PM
        .values.sum()
    
    val totalActivity = patterns.hourDistribution.values.sum()
    val workoutTimeActivity = (morningWorkout + eveningWorkout).toFloat() / totalActivity
    
    val durationInHours = patterns.averageStayDuration / 3600000L
    val isTypicalWorkoutDuration = durationInHours in 0.5..4.0  // 30 min to 4 hours
    
    return workoutTimeActivity >= preferences.gymActivityThreshold &&
           isTypicalWorkoutDuration &&
           patterns.visitFrequency >= 1.0f  // At least once per week
}
```

### Restaurant Detection Algorithm

```kotlin
private fun isRestaurantPattern(patterns: TemporalPatterns, preferences: UserPreferences): Boolean {
    // Restaurants typically have:
    // 1. Meal time visits (lunch/dinner)
    // 2. Short to medium duration (30 min - 3 hours)
    // 3. Social patterns (weekends, evenings)
    
    val lunchHours = patterns.hourDistribution
        .filterKeys { it in 11..14 }         // 11 AM to 2 PM
        .values.sum()
    
    val dinnerHours = patterns.hourDistribution
        .filterKeys { it in 18..21 }         // 6 PM to 9 PM
        .values.sum()
    
    val totalActivity = patterns.hourDistribution.values.sum()
    val mealTimeActivity = (lunchHours + dinnerHours).toFloat() / totalActivity
    
    val durationInMinutes = patterns.averageStayDuration / 60000L
    val isTypicalMealDuration = durationInMinutes in preferences.restaurantMinDurationMinutes..
                                                    preferences.restaurantMaxDurationMinutes
    
    return mealTimeActivity >= preferences.restaurantMealTimeThreshold &&
           isTypicalMealDuration
}
```

## 🎯 Confidence Scoring System

### Multi-Factor Confidence Calculation

```kotlin
private fun calculateConfidence(
    locations: List<Location>,
    category: PlaceCategory,
    preferences: UserPreferences
): Float {
    
    val factors = ConfidenceFactors(
        baseConfidence = getBaseCategoryConfidence(category),
        locationCountFactor = calculateLocationCountFactor(locations.size),
        accuracyFactor = calculateAccuracyFactor(locations),
        timeSpanFactor = calculateTimeSpanFactor(locations),
        consistencyFactor = calculateConsistencyFactor(locations),
        patternMatchFactor = calculatePatternMatchFactor(locations, category)
    )
    
    return combineConfidenceFactors(factors).coerceIn(0.0f, 0.95f)
}

data class ConfidenceFactors(
    val baseConfidence: Float,      // Category-specific base confidence
    val locationCountFactor: Float, // More locations = higher confidence
    val accuracyFactor: Float,     // Better GPS accuracy = higher confidence  
    val timeSpanFactor: Float,     // Longer observation = higher confidence
    val consistencyFactor: Float,  // Regular patterns = higher confidence
    val patternMatchFactor: Float  // Strong pattern match = higher confidence
)

private fun getBaseCategoryConfidence(category: PlaceCategory): Float = when (category) {
    PlaceCategory.HOME -> 0.7f        // Strong night/sleep patterns
    PlaceCategory.WORK -> 0.6f        // Clear weekday/work hour patterns
    PlaceCategory.GYM -> 0.5f         // Moderate workout patterns
    PlaceCategory.RESTAURANT -> 0.4f  // Meal time patterns
    PlaceCategory.SHOPPING -> 0.4f    // Weekend/leisure patterns
    PlaceCategory.UNKNOWN -> 0.2f     // No clear pattern
    else -> 0.3f
}

private fun calculateLocationCountFactor(count: Int): Float {
    // More observations = higher confidence, with diminishing returns
    return minOf(count / 50.0f, 0.25f) // Max 0.25 bonus for 50+ locations
}

private fun calculateAccuracyFactor(locations: List<Location>): Float {
    val avgAccuracy = locations.map { it.accuracy }.average()
    
    return when {
        avgAccuracy <= 10f -> 0.15f   // Excellent GPS (within 10m)
        avgAccuracy <= 25f -> 0.10f   // Good GPS (within 25m)
        avgAccuracy <= 50f -> 0.05f   // Fair GPS (within 50m)
        else -> 0.0f                  // Poor GPS gets no bonus
    }
}

private fun calculateTimeSpanFactor(locations: List<Location>): Float {
    if (locations.size < 2) return 0.0f
    
    val timeSpanHours = Duration.between(
        locations.minOf { it.timestamp },
        locations.maxOf { it.timestamp }
    ).toHours()
    
    return when {
        timeSpanHours >= 24 * 7 -> 0.10f  // Week+ of data
        timeSpanHours >= 24 -> 0.05f      // Day+ of data
        else -> 0.0f
    }
}

private fun calculateConsistencyFactor(locations: List<Location>): Float {
    // Measure how consistent visit patterns are
    val hourGroups = locations.groupBy { it.timestamp.hour }
    val dayGroups = locations.groupBy { it.timestamp.dayOfWeek }
    
    // Shannon entropy to measure pattern consistency
    val hourEntropy = calculateEntropy(hourGroups.values.map { it.size })
    val dayEntropy = calculateEntropy(dayGroups.values.map { it.size })
    
    // Lower entropy = more consistent patterns = higher confidence
    val maxEntropy = ln(24.0) // Maximum possible entropy for hours
    val consistencyScore = 1.0f - (hourEntropy / maxEntropy).toFloat()
    
    return consistencyScore * 0.10f // Max 0.10 bonus
}

private fun calculateEntropy(frequencies: List<Int>): Double {
    val total = frequencies.sum().toDouble()
    return frequencies
        .filter { it > 0 }
        .sumOf { freq ->
            val p = freq / total
            -p * ln(p)
        }
}
```

## 🔄 Continuous Learning & Improvement

### Feedback Loop Integration

```kotlin
class PlaceImprovementService @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val userFeedbackRepository: UserFeedbackRepository
) {
    
    suspend fun improveExistingPlaces() {
        val lowConfidencePlaces = placeRepository.getPlacesWithLowConfidence(threshold = 0.6f)
        
        lowConfidencePlaces.forEach { place ->
            val recentVisits = visitRepository.getVisitsForPlace(place.id, days = 30)
            val userFeedback = userFeedbackRepository.getFeedbackForPlace(place.id)
            
            val improvedPlace = enhancePlaceWithNewData(place, recentVisits, userFeedback)
            
            if (improvedPlace.confidence > place.confidence) {
                placeRepository.updatePlace(improvedPlace)
            }
        }
    }
    
    private suspend fun enhancePlaceWithNewData(
        place: Place,
        recentVisits: List<Visit>,
        feedback: List<UserFeedback>
    ): Place {
        
        // Re-analyze with more data
        val newCategory = recategorizeWithMoreData(place, recentVisits)
        val newConfidence = recalculateConfidence(place, recentVisits, feedback)
        val newName = generateImprovedName(newCategory, place.latitude, place.longitude)
        
        return place.copy(
            category = newCategory,
            confidence = newConfidence,
            name = if (!place.isCustom) newName else place.name,
            visitCount = place.visitCount + recentVisits.size,
            lastVisit = recentVisits.maxByOrNull { it.entryTime }?.entryTime ?: place.lastVisit
        )
    }
}
```

### User Feedback Integration

```kotlin
data class UserFeedback(
    val placeId: Long,
    val feedbackType: FeedbackType,
    val correctedCategory: PlaceCategory?,
    val correctedName: String?,
    val timestamp: LocalDateTime
)

enum class FeedbackType {
    CORRECT_CATEGORY,     // User confirmed category is correct
    WRONG_CATEGORY,       // User corrected the category
    WRONG_NAME,          // User corrected the name
    MERGE_PLACES,        // Two places should be one
    SPLIT_PLACE          // One place should be multiple
}

suspend fun incorporateUserFeedback(feedback: UserFeedback) {
    when (feedback.feedbackType) {
        FeedbackType.CORRECT_CATEGORY -> {
            // Boost confidence for this category detection
            val place = placeRepository.getPlaceById(feedback.placeId)!!
            placeRepository.updatePlace(
                place.copy(confidence = minOf(place.confidence + 0.1f, 0.95f))
            )
        }
        
        FeedbackType.WRONG_CATEGORY -> {
            // Update category and learn from the mistake
            val place = placeRepository.getPlaceById(feedback.placeId)!!
            placeRepository.updatePlace(
                place.copy(
                    category = feedback.correctedCategory!!,
                    isCustom = true, // Mark as user-corrected
                    confidence = 0.9f // High confidence due to user input
                )
            )
            
            // Update ML model with this correction
            updateCategoryModel(place, feedback.correctedCategory!!)
        }
    }
}
```

## 📈 Performance Optimization

### Batch Processing Strategy

```kotlin
// Process locations in batches to prevent memory issues
suspend fun detectNewPlacesWithBatching(preferences: UserPreferences): List<Place> {
    val batchSize = preferences.dataProcessingBatchSize.coerceIn(100, 1000)
    val maxLocations = preferences.maxLocationsToProcess
    
    val allLocations = locationRepository.getRecentLocations(maxLocations).first()
    val newPlaces = mutableListOf<Place>()
    
    allLocations.chunked(batchSize).forEach { batch ->
        val batchPlaces = processBatch(batch, preferences)
        newPlaces.addAll(batchPlaces)
        
        // Allow other operations between batches
        yield()
    }
    
    // Merge nearby places from different batches
    return mergeNearbyPlaces(newPlaces, preferences.placeDetectionRadius)
}
```

### Memory Management

```kotlin
// Use sequences for large data processing
fun processLargeLocationDataset(locations: List<Location>): List<Place> {
    return locations.asSequence()
        .filter { it.accuracy <= 50f }
        .filter { isValidGpsPoint(it) }
        .chunked(500)
        .map { batch -> processLocationBatch(batch) }
        .flatten()
        .toList()
}
```

## 🐛 Common Issues & Solutions

### 1. Over-clustering in Dense Areas

**Problem**: Too many small places in cities
```kotlin
// Solution: Adaptive epsilon based on density
fun calculateAdaptiveEpsilon(locations: List<Location>): Double {
    val density = calculateLocationDensity(locations)
    
    return when {
        density > 0.001 -> 50.0  // High density (urban) - smaller epsilon
        density > 0.0001 -> 100.0 // Medium density (suburban)  
        else -> 150.0            // Low density (rural) - larger epsilon
    }
}
```

### 2. Under-clustering in Sparse Areas

**Problem**: Missing places in rural areas
```kotlin
// Solution: Lower minPoints threshold for sparse areas
fun calculateAdaptiveMinPoints(density: Double): Int {
    return when {
        density < 0.0001 -> 5   // Rural - fewer points needed
        density < 0.001 -> 10   // Suburban - standard
        else -> 15              // Urban - more points for confidence
    }
}
```

### 3. GPS Drift Causing False Places

**Problem**: GPS inaccuracy creates fake places
```kotlin
// Solution: Enhanced accuracy filtering
fun filterGpsDrift(locations: List<Location>): List<Location> {
    return locations.filter { location ->
        val nearbyAccurateReadings = locations
            .filter { other -> 
                calculateDistance(location, other) <= 100.0 &&
                other.accuracy < location.accuracy 
            }
        
        // Keep if no better readings nearby
        nearbyAccurateReadings.isEmpty()
    }
}
```

---

*Next: [GPS Tracking System](./GPS_TRACKING.md)*