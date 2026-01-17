# Activity-First Implementation Plan
**Project:** Voyager Location Analytics
**Date:** 2025-12-02
**Status:** Implementation Ready

---

## Executive Summary

This plan transforms Voyager from a **location-first** to an **activity-first** application by:

1. **Enriching location data** with activity context (walking, driving, stationary, working out, eating, etc.)
2. **Real place names** from OSM/geocoding + user customization
3. **Intelligent place detection** based on what you're doing, not just where you are
4. **Better insights** - "You worked out for 45 min" vs "You were at coordinates (X,Y)"

**Key Insight:** Knowing "You went to Starbucks for coffee" is more meaningful than "You were at (12.9716, 77.5946) categorized as Restaurant"

---

## Current State Analysis

### ✅ What Already Exists

1. **Activity Recognition Infrastructure** (`HybridActivityRecognitionManager`)
   - Google Play Services Activity Recognition API
   - Fallback to motion sensors
   - Detects: DRIVING, WALKING, STATIONARY, CYCLING, UNKNOWN
   - Already injected into `LocationTrackingService`

2. **Place Name Fields** (`Place.kt`)
   - `osmSuggestedName` - ready for OSM data
   - `osmSuggestedCategory` - ready for OSM categories
   - `osmPlaceType` - ready for raw OSM types
   - `isUserRenamed` - tracks custom names
   - `needsUserNaming` - flags places needing user input

3. **Geocoding Infrastructure**
   - `EnrichPlaceWithDetailsUseCase` exists
   - `GeocodingRepository` already implemented
   - Android Geocoder + OSM Nominatim integration ready
   - Just needs to be connected to place detection

### ❌ What's Missing

1. **Activity data NOT stored with locations**
   - Location entity has no activity field
   - Can't query "show me all locations where I was working out"
   - Can't analyze "how much time spent walking vs driving"

2. **Activity NOT used in place detection**
   - DBSCAN just clusters coordinates
   - Doesn't know if you were driving through or stationary
   - Creates false places when you're stuck in traffic

3. **Place names are generic**
   - Shows "Restaurant" instead of "Starbucks Coffee"
   - Shows "Gym" instead of "Gold's Gym"
   - No user customization flow

4. **No activity-based insights**
   - Can't see "You ran 5km this week"
   - Can't differentiate "outdoor walk" from "indoor shopping"
   - No workout detection, meal detection, commute detection

---

## Phase 1: Store Activity Context with Locations (Week 1)

### Goal
Every GPS point should know what activity was happening when it was recorded.

### 1.1 Extend Location Model

**File:** `domain/model/Location.kt`

```kotlin
data class Location(
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val timestamp: LocalDateTime,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,

    // NEW: Activity context
    val userActivity: UserActivity = UserActivity.UNKNOWN,
    val activityConfidence: Float = 0f, // 0.0 - 1.0

    // NEW: Semantic context (derived from activity + time + patterns)
    val semanticContext: SemanticContext? = null
)

/**
 * What the user was likely doing at this location
 * Inferred from activity + time patterns + place category
 */
enum class SemanticContext {
    // Work-related
    WORKING,           // At work, stationary, work hours
    COMMUTING,         // Driving/transit, between home-work
    WORK_MEETING,      // At non-work location during work hours

    // Health & Fitness
    WORKING_OUT,       // Gym, high movement, workout hours
    OUTDOOR_EXERCISE,  // Walking/cycling, sustained movement

    // Daily Activities
    EATING,            // Restaurant, meal times, stationary
    SHOPPING,          // Shopping area, walking, 30-120 min
    RUNNING_ERRANDS,   // Multiple short stops, varied activities

    // Social & Leisure
    SOCIALIZING,       // Restaurant/cafe, evening/weekend
    ENTERTAINMENT,     // Cinema/mall, leisure hours
    RELAXING_HOME,     // Home, stationary, evening

    // Transit
    IN_TRANSIT,        // Driving/cycling between places
    TRAVELING,         // Far from home, exploring new areas

    UNKNOWN            // Can't determine context
}
```

**File:** `data/database/entity/LocationEntity.kt`

```kotlin
@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["userActivity"]),  // NEW: Query by activity
        Index(value = ["semanticContext"]) // NEW: Query by context
    ]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val timestamp: LocalDateTime,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,

    // NEW: Activity tracking
    val userActivity: String = UserActivity.UNKNOWN.name,
    val activityConfidence: Float = 0f,
    val semanticContext: String? = null
)
```

**Database Migration:**
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE locations
            ADD COLUMN userActivity TEXT NOT NULL DEFAULT 'UNKNOWN'
        """)
        database.execSQL("""
            ALTER TABLE locations
            ADD COLUMN activityConfidence REAL NOT NULL DEFAULT 0.0
        """)
        database.execSQL("""
            ALTER TABLE locations
            ADD COLUMN semanticContext TEXT
        """)
        database.execSQL("""
            CREATE INDEX index_locations_userActivity
            ON locations(userActivity)
        """)
        database.execSQL("""
            CREATE INDEX index_locations_semanticContext
            ON locations(semanticContext)
        """)
    }
}
```

### 1.2 Capture Activity in Real-Time

**File:** `data/service/LocationTrackingService.kt` (modify existing)

```kotlin
// Around line 278-354 (shouldSaveLocation function)
private suspend fun shouldSaveLocation(newLocation: AndroidLocation): Boolean {
    // ... existing checks ...

    // NEW: Get current activity from activity recognition
    val activityDetection = activityRecognitionManager.getCurrentActivity()

    // NEW: Skip location saves while moving with high confidence
    // (prevents false place detection while driving/cycling)
    if (activityDetection.isMoving(confidenceThreshold = 0.75f)) {
        logger.logLocationProcessing(
            message = "Skipping location: user is moving (${activityDetection.activity})",
            data = mapOf(
                "activity" to activityDetection.activity.name,
                "confidence" to activityDetection.confidence
            )
        )
        return false
    }

    // ... rest of existing logic ...
    return true
}

// Modify saveLocation to include activity
private suspend fun saveLocation(location: AndroidLocation) {
    try {
        // Get current activity
        val activityDetection = activityRecognitionManager.getCurrentActivity()

        // Infer semantic context (basic version, will enhance later)
        val semanticContext = inferSemanticContext(
            activity = activityDetection.activity,
            timestamp = LocalDateTime.now(),
            speed = location.speed
        )

        val locationData = Location(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = LocalDateTime.now(),
            accuracy = location.accuracy,
            speed = location.speed,
            altitude = location.altitude,
            bearing = location.bearing,
            userActivity = activityDetection.activity,
            activityConfidence = activityDetection.confidence,
            semanticContext = semanticContext
        )

        locationRepository.saveLocation(locationData)

        // ... rest of existing logic ...
    } catch (e: Exception) {
        // ... error handling ...
    }
}

/**
 * Infer what the user is doing based on activity + time + speed
 * Phase 1: Basic inference, will enhance in Phase 2
 */
private fun inferSemanticContext(
    activity: UserActivity,
    timestamp: LocalDateTime,
    speed: Float?
): SemanticContext? {
    val hour = timestamp.hour
    val isWeekday = timestamp.dayOfWeek.value in 1..5
    val isWorkHours = hour in 9..17
    val isMealTime = hour in listOf(7, 8, 12, 13, 18, 19, 20)
    val isWorkoutTime = hour in 5..9 || hour in 17..21

    return when (activity) {
        UserActivity.DRIVING -> {
            if (isWeekday && (hour in 7..9 || hour in 17..19)) {
                SemanticContext.COMMUTING
            } else {
                SemanticContext.IN_TRANSIT
            }
        }

        UserActivity.WALKING -> {
            val speedKmh = (speed ?: 0f) * 3.6
            when {
                speedKmh > 6 && isWorkoutTime -> SemanticContext.OUTDOOR_EXERCISE
                speedKmh < 3 -> SemanticContext.SHOPPING // Slow walking = browsing
                else -> null // Will be refined with place context
            }
        }

        UserActivity.CYCLING -> SemanticContext.OUTDOOR_EXERCISE

        UserActivity.STATIONARY -> {
            // Will be refined when we know the place
            null
        }

        UserActivity.UNKNOWN -> null
    }
}
```

### 1.3 Query Locations by Activity

**File:** `data/database/dao/LocationDao.kt` (add new queries)

```kotlin
@Dao
interface LocationDao {
    // ... existing queries ...

    // NEW: Query by activity
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

    // NEW: Get activity statistics
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

    // NEW: Get locations by semantic context
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
}

data class ActivityStatistic(
    val userActivity: String,
    val count: Int,
    val avgConfidence: Float
)
```

---

## Phase 2: Activity-Aware Place Detection (Week 1-2)

### Goal
Places should be detected based on WHAT you're doing, not just WHERE you are.

### 2.1 Enhanced Place Model

**File:** `domain/model/Place.kt` (extend existing)

```kotlin
data class Place(
    val id: Long = 0L,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,

    // EXISTING: Geocoding fields
    val address: String? = null,
    val streetName: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
    val isUserRenamed: Boolean = false,
    val needsUserNaming: Boolean = false,
    val osmSuggestedName: String? = null,
    val osmSuggestedCategory: PlaceCategory? = null,
    val osmPlaceType: String? = null,

    // NEW: Activity-based insights
    val dominantActivity: UserActivity? = null,           // Most common activity here
    val activityDistribution: Map<UserActivity, Float> = emptyMap(), // % time per activity
    val dominantSemanticContext: SemanticContext? = null, // What you usually do here
    val contextDistribution: Map<SemanticContext, Float> = emptyMap(),

    // NEW: Real place name (priority: user custom > OSM > geocoded > category)
    val displayName: String = name, // Computed property would be better

    // EXISTING fields...
    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0L,
    val lastVisit: LocalDateTime? = null,
    val isCustom: Boolean = false,
    val radius: Double = 100.0,
    val placeId: String? = null,
    val confidence: Float = 1.0f
) {
    /**
     * Get the best available name for this place
     * Priority: User custom name > OSM name > Geocoded address > Category
     */
    fun getBestName(): String {
        return when {
            isUserRenamed && name.isNotBlank() -> name
            !osmSuggestedName.isNullOrBlank() -> osmSuggestedName
            !address.isNullOrBlank() -> {
                // Extract business name from address if present
                address.split(",").firstOrNull()?.trim() ?: address
            }
            else -> category.displayName
        }
    }

    /**
     * Check if this place needs user to provide a name
     */
    fun requiresNaming(): Boolean {
        return needsUserNaming ||
               (osmSuggestedName.isNullOrBlank() && address.isNullOrBlank())
    }
}
```

### 2.2 Activity-Aware DBSCAN Clustering

**File:** `domain/usecase/PlaceDetectionUseCases.kt` (modify existing)

```kotlin
private suspend fun detectNewPlacesInternal(preferences: UserPreferences): List<Place> {
    // ... existing location fetching code ...

    // MODIFIED: Fetch locations with activity data
    val recentLocations = locationRepository.getRecentLocations(
        limit = preferences.maxLocationsToProcess
    ).first()

    Log.i(TAG, "Total locations fetched: ${recentLocations.size}")

    // NEW: Filter out locations captured while moving (high-confidence driving/cycling)
    val stationaryLocations = recentLocations.filter { location ->
        location.userActivity !in setOf(UserActivity.DRIVING, UserActivity.CYCLING) ||
        location.activityConfidence < 0.75f
    }

    Log.i(TAG, "Stationary locations (after activity filter): ${stationaryLocations.size}")
    Log.i(TAG, "Filtered out ${recentLocations.size - stationaryLocations.size} moving locations")

    // Quality filter (existing)
    val qualityLocations = stationaryLocations.filter { location ->
        location.accuracy <= preferences.maxGpsAccuracyMeters &&
        (location.speed == null || location.speed * 3.6 <= preferences.maxSpeedKmh)
    }

    Log.i(TAG, "Quality locations (after accuracy/speed filter): ${qualityLocations.size}")

    // DBSCAN clustering (existing algorithm)
    val clusters = LocationUtils.dbscan(
        points = qualityLocations.map { Pair(it.latitude, it.longitude) },
        epsilon = preferences.clusteringDistanceMeters,
        minPoints = preferences.minPointsForCluster
    )

    Log.i(TAG, "DBSCAN clusters detected: ${clusters.size}")

    // NEW: Enhanced place creation with activity analysis
    val newPlaces = mutableListOf<Place>()

    for ((index, cluster) in clusters.withIndex()) {
        Log.i(TAG, "Processing cluster $index: ${cluster.size} points")

        // Get all locations in this cluster
        val clusterLocations = cluster.map { idx -> qualityLocations[idx] }

        // Calculate cluster centroid
        val centerLat = clusterLocations.map { it.latitude }.average()
        val centerLng = clusterLocations.map { it.longitude }.average()

        // NEW: Analyze activities at this cluster
        val activityCounts = clusterLocations
            .groupBy { it.userActivity }
            .mapValues { it.value.size }

        val dominantActivity = activityCounts.maxByOrNull { it.value }?.key

        val activityDistribution = activityCounts.mapValues {
            it.value.toFloat() / clusterLocations.size
        }

        Log.i(TAG, "Cluster $index activity distribution: $activityDistribution")

        // NEW: Analyze semantic contexts
        val contextCounts = clusterLocations
            .mapNotNull { it.semanticContext }
            .groupBy { it }
            .mapValues { it.value.size }

        val dominantContext = contextCounts.maxByOrNull { it.value }?.key

        val contextDistribution = contextCounts.mapValues {
            it.value.toFloat() / clusterLocations.size
        }

        Log.i(TAG, "Cluster $index context distribution: $contextDistribution")

        // NEW: Category inference using BOTH time patterns AND activity
        val category = inferPlaceCategoryFromActivityAndTime(
            locations = clusterLocations,
            dominantActivity = dominantActivity,
            dominantContext = dominantContext,
            activityDistribution = activityDistribution
        )

        Log.i(TAG, "Inferred category for cluster $index: $category")

        // NEW: Get real place name from geocoding
        val placeDetails = try {
            enrichPlaceWithDetailsUseCase.invoke(
                latitude = centerLat,
                longitude = centerLng,
                inferredCategory = category
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich place: ${e.message}")
            null
        }

        // Create place with real name
        val placeName = placeDetails?.osmSuggestedName
            ?: placeDetails?.address?.split(",")?.firstOrNull()?.trim()
            ?: generatePlaceName(category, centerLat, centerLng)

        val needsUserNaming = placeDetails?.osmSuggestedName.isNullOrBlank() &&
                             placeDetails?.address.isNullOrBlank()

        val newPlace = Place(
            name = placeName,
            category = category,
            latitude = centerLat,
            longitude = centerLng,
            address = placeDetails?.address,
            streetName = placeDetails?.streetName,
            locality = placeDetails?.locality,
            subLocality = placeDetails?.subLocality,
            postalCode = placeDetails?.postalCode,
            countryCode = placeDetails?.countryCode,
            osmSuggestedName = placeDetails?.osmSuggestedName,
            osmSuggestedCategory = placeDetails?.osmSuggestedCategory,
            osmPlaceType = placeDetails?.osmPlaceType,
            needsUserNaming = needsUserNaming,
            dominantActivity = dominantActivity,
            activityDistribution = activityDistribution,
            dominantSemanticContext = dominantContext,
            contextDistribution = contextDistribution,
            visitCount = 1,
            radius = preferences.placeDetectionRadius,
            confidence = calculateConfidence(clusterLocations, category)
        )

        Log.i(TAG, "Created place: ${newPlace.name} at ($centerLat, $centerLng)")

        newPlaces.add(newPlace)
    }

    // Save new places
    newPlaces.forEach { place ->
        placeRepository.savePlace(place)
        Log.i(TAG, "Saved place: ${place.name} (${place.category})")
    }

    return newPlaces
}

/**
 * NEW: Infer place category using activity data + time patterns
 * Much more accurate than time patterns alone
 */
private fun inferPlaceCategoryFromActivityAndTime(
    locations: List<Location>,
    dominantActivity: UserActivity?,
    dominantContext: SemanticContext?,
    activityDistribution: Map<UserActivity, Float>
): PlaceCategory {

    // Priority 1: Use semantic context if available (most reliable)
    dominantContext?.let { context ->
        return when (context) {
            SemanticContext.WORKING -> PlaceCategory.WORK
            SemanticContext.WORKING_OUT -> PlaceCategory.GYM
            SemanticContext.OUTDOOR_EXERCISE -> PlaceCategory.OUTDOOR
            SemanticContext.EATING -> PlaceCategory.RESTAURANT
            SemanticContext.SHOPPING,
            SemanticContext.RUNNING_ERRANDS -> PlaceCategory.SHOPPING
            SemanticContext.SOCIALIZING -> PlaceCategory.SOCIAL
            SemanticContext.ENTERTAINMENT -> PlaceCategory.ENTERTAINMENT
            SemanticContext.RELAXING_HOME -> PlaceCategory.HOME
            else -> PlaceCategory.UNKNOWN
        }
    }

    // Priority 2: Use activity patterns
    val stationaryPercent = activityDistribution[UserActivity.STATIONARY] ?: 0f
    val walkingPercent = activityDistribution[UserActivity.WALKING] ?: 0f

    // High stationary + work hours = likely WORK or HOME
    if (stationaryPercent > 0.8f) {
        val nightHours = locations.count { it.timestamp.hour in 22..23 || it.timestamp.hour in 0..6 }
        val nightPercent = nightHours.toFloat() / locations.size

        if (nightPercent > 0.6f) return PlaceCategory.HOME

        val workHours = locations.count {
            it.timestamp.hour in 9..17 &&
            it.timestamp.dayOfWeek.value in 1..5
        }
        val workPercent = workHours.toFloat() / locations.size

        if (workPercent > 0.5f) return PlaceCategory.WORK
    }

    // Moderate walking + stationary = SHOPPING or RESTAURANT
    if (walkingPercent in 0.2f..0.6f && stationaryPercent in 0.3f..0.7f) {
        val avgDurationMinutes = calculateAverageDuration(locations)

        return when {
            avgDurationMinutes in 30..120 -> PlaceCategory.SHOPPING
            avgDurationMinutes in 45..90 -> PlaceCategory.RESTAURANT
            else -> PlaceCategory.UNKNOWN
        }
    }

    // Fallback to existing time-based inference
    return inferPlaceCategoryFromTimePatterns(locations)
}

private fun calculateAverageDuration(locations: List<Location>): Int {
    if (locations.size < 2) return 0

    val sorted = locations.sortedBy { it.timestamp }
    val duration = java.time.Duration.between(
        sorted.first().timestamp,
        sorted.last().timestamp
    )

    return duration.toMinutes().toInt()
}
```

---

## Phase 3: Real Place Names from OSM & User Input (Week 2)

### Goal
Show "Starbucks Coffee" instead of "Restaurant" and let users customize names.

### 3.1 Automatic Place Naming

**File:** `domain/usecase/EnrichPlaceWithDetailsUseCase.kt` (verify existing implementation)

This already exists! Just ensure it's called in place detection (done in Phase 2.2 above).

```kotlin
// Current implementation should do:
// 1. Query OSM Nominatim for place name
// 2. Fallback to Android Geocoder for address
// 3. Return structured data with name, category, address
```

### 3.2 User Place Naming UI

**File:** `presentation/screen/places/PlaceNameDialog.kt` (NEW)

```kotlin
@Composable
fun PlaceNameDialog(
    place: Place,
    onDismiss: () -> Unit,
    onSave: (String, PlaceCategory) -> Unit
) {
    var customName by remember { mutableStateOf(place.name) }
    var selectedCategory by remember { mutableStateOf(place.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name This Place") },
        text = {
            Column {
                // Show suggested names if available
                if (!place.osmSuggestedName.isNullOrBlank()) {
                    Text(
                        "Suggested: ${place.osmSuggestedName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!place.address.isNullOrBlank()) {
                    Text(
                        "Address: ${place.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Place Name") },
                    placeholder = { Text("e.g., Joe's Coffee Shop") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category selector
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow {
                    items(PlaceCategory.values()) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.displayName) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(customName, selectedCategory)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

### 3.3 Show "Needs Naming" Badge

**File:** `presentation/screen/timeline/TimelineScreen.kt` (modify existing)

```kotlin
@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    onNameClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = place.getBestName(),
                        style = MaterialTheme.typography.titleMedium
                    )

                    // NEW: Show "Name This" badge if needs naming
                    if (place.requiresNaming()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.clickable { onNameClick() }
                        ) {
                            Text(
                                "Name This",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Show address if available
                if (!place.address.isNullOrBlank()) {
                    Text(
                        text = place.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = place.category.displayName,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View details"
            )
        }
    }
}
```

---

## Phase 4: Activity-Based Insights (Week 2-3)

### Goal
Show insights like "You worked out 3 times this week" instead of just location data.

### 4.1 Activity Analytics Use Case

**File:** `domain/usecase/ActivityAnalyticsUseCases.kt` (NEW)

```kotlin
@Singleton
class ActivityAnalyticsUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository
) {

    /**
     * Get time spent on each activity type
     */
    suspend fun getActivityTimeSummary(
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<UserActivity, Duration> {
        val locations = locationRepository.getLocationsBetween(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        ).first()

        // Group consecutive locations by activity
        val activitySessions = groupIntoSessions(locations)

        // Calculate total time per activity
        return activitySessions
            .groupBy { it.activity }
            .mapValues { (_, sessions) ->
                sessions.fold(Duration.ZERO) { acc, session ->
                    acc + session.duration
                }
            }
    }

    /**
     * Detect workout sessions (running, cycling, gym visits)
     */
    suspend fun detectWorkoutSessions(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WorkoutSession> {
        val startTime = startDate.atStartOfDay()
        val endTime = endDate.atTime(23, 59, 59)

        // Get locations with workout-related activities
        val workoutLocations = locationRepository.getRecentLocations(Int.MAX_VALUE)
            .first()
            .filter {
                it.timestamp in startTime..endTime &&
                (it.semanticContext in setOf(
                    SemanticContext.WORKING_OUT,
                    SemanticContext.OUTDOOR_EXERCISE
                ) || it.userActivity in setOf(
                    UserActivity.CYCLING,
                    UserActivity.WALKING
                ) && it.speed != null && it.speed * 3.6 > 5) // Fast walking/running
            }

        // Group into workout sessions
        val sessions = mutableListOf<WorkoutSession>()
        var currentSession: MutableList<Location>? = null

        for (location in workoutLocations.sortedBy { it.timestamp }) {
            if (currentSession == null) {
                currentSession = mutableListOf(location)
            } else {
                val lastLocation = currentSession.last()
                val timeDiff = Duration.between(lastLocation.timestamp, location.timestamp)

                if (timeDiff.toMinutes() < 10) {
                    // Same session
                    currentSession.add(location)
                } else {
                    // New session - save previous
                    sessions.add(createWorkoutSession(currentSession))
                    currentSession = mutableListOf(location)
                }
            }
        }

        // Add last session
        currentSession?.let {
            if (it.size > 1) {
                sessions.add(createWorkoutSession(it))
            }
        }

        return sessions
    }

    /**
     * Get commute pattern analysis
     */
    suspend fun analyzeCommutePatterns(
        startDate: LocalDate,
        endDate: LocalDate
    ): CommuteAnalysis {
        val locations = locationRepository.getLocationsBetween(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        ).first()

        val commuteSessions = locations
            .filter { it.semanticContext == SemanticContext.COMMUTING }
            .groupBy { it.timestamp.toLocalDate() }

        val avgCommuteTime = commuteSessions.values
            .mapNotNull { dayLocations ->
                if (dayLocations.size < 2) return@mapNotNull null
                Duration.between(
                    dayLocations.first().timestamp,
                    dayLocations.last().timestamp
                )
            }
            .takeIf { it.isNotEmpty() }
            ?.fold(Duration.ZERO) { acc, duration -> acc + duration }
            ?.dividedBy(commuteSessions.size.toLong())
            ?: Duration.ZERO

        return CommuteAnalysis(
            totalCommutes = commuteSessions.size,
            averageCommuteTime = avgCommuteTime,
            longestCommute = commuteSessions.values.maxOfOrNull { dayLocations ->
                Duration.between(
                    dayLocations.first().timestamp,
                    dayLocations.last().timestamp
                )
            } ?: Duration.ZERO
        )
    }

    private fun groupIntoSessions(locations: List<Location>): List<ActivitySession> {
        val sessions = mutableListOf<ActivitySession>()
        var currentActivity: UserActivity? = null
        var sessionStart: LocalDateTime? = null
        var sessionEnd: LocalDateTime? = null

        for (location in locations.sortedBy { it.timestamp }) {
            if (currentActivity == null) {
                currentActivity = location.userActivity
                sessionStart = location.timestamp
                sessionEnd = location.timestamp
            } else if (location.userActivity == currentActivity) {
                sessionEnd = location.timestamp
            } else {
                // Activity changed - save session
                sessions.add(
                    ActivitySession(
                        activity = currentActivity,
                        startTime = sessionStart!!,
                        endTime = sessionEnd!!,
                        duration = Duration.between(sessionStart, sessionEnd)
                    )
                )
                currentActivity = location.userActivity
                sessionStart = location.timestamp
                sessionEnd = location.timestamp
            }
        }

        // Add last session
        if (currentActivity != null && sessionStart != null && sessionEnd != null) {
            sessions.add(
                ActivitySession(
                    activity = currentActivity,
                    startTime = sessionStart,
                    endTime = sessionEnd,
                    duration = Duration.between(sessionStart, sessionEnd)
                )
            )
        }

        return sessions
    }

    private fun createWorkoutSession(locations: List<Location>): WorkoutSession {
        val sortedLocations = locations.sortedBy { it.timestamp }
        val start = sortedLocations.first()
        val end = sortedLocations.last()

        val duration = Duration.between(start.timestamp, end.timestamp)

        // Calculate distance if speed available
        val totalDistance = locations
            .mapNotNull { it.speed }
            .sum() * duration.seconds / 1000.0 // meters

        // Determine workout type
        val workoutType = when {
            locations.any { it.userActivity == UserActivity.CYCLING } -> WorkoutType.CYCLING
            locations.any { it.semanticContext == SemanticContext.WORKING_OUT } -> WorkoutType.GYM
            totalDistance > 1000 && duration.toMinutes() > 10 -> WorkoutType.RUNNING
            else -> WorkoutType.WALKING
        }

        return WorkoutSession(
            startTime = start.timestamp,
            endTime = end.timestamp,
            duration = duration,
            type = workoutType,
            distanceMeters = totalDistance,
            locations = sortedLocations.size
        )
    }
}

data class ActivitySession(
    val activity: UserActivity,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val duration: Duration
)

data class WorkoutSession(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val duration: Duration,
    val type: WorkoutType,
    val distanceMeters: Double,
    val locations: Int
)

enum class WorkoutType {
    RUNNING, CYCLING, WALKING, GYM, UNKNOWN
}

data class CommuteAnalysis(
    val totalCommutes: Int,
    val averageCommuteTime: Duration,
    val longestCommute: Duration
)
```

### 4.2 Activity Insights UI

**File:** `presentation/screen/insights/ActivityInsightsScreen.kt` (NEW)

```kotlin
@Composable
fun ActivityInsightsScreen(
    viewModel: ActivityInsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Activity Time Summary
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "This Week's Activities",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                uiState.activityTimeSummary.forEach { (activity, duration) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = getActivityIcon(activity),
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(activity.name.lowercase().capitalize())
                        }

                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Workout Sessions
        if (uiState.workoutSessions.isNotEmpty()) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Workouts This Week",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "${uiState.workoutSessions.size} workouts, " +
                        "${formatDuration(uiState.totalWorkoutTime)} total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    uiState.workoutSessions.forEach { session ->
                        WorkoutSessionCard(session)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Commute Analysis
        if (uiState.commuteAnalysis.totalCommutes > 0) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Commute Analysis",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val analysis = uiState.commuteAnalysis

                    InfoRow(
                        "Total Commutes",
                        "${analysis.totalCommutes} trips"
                    )
                    InfoRow(
                        "Average Time",
                        formatDuration(analysis.averageCommuteTime)
                    )
                    InfoRow(
                        "Longest Commute",
                        formatDuration(analysis.longestCommute)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutSessionCard(session: WorkoutSession) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    session.type.name.lowercase().capitalize(),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    session.startTime.format(
                        DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDuration(session.duration),
                    style = MaterialTheme.typography.titleMedium
                )
                if (session.distanceMeters > 100) {
                    Text(
                        String.format("%.1f km", session.distanceMeters / 1000),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

fun getActivityIcon(activity: UserActivity): ImageVector {
    return when (activity) {
        UserActivity.WALKING -> Icons.Default.DirectionsWalk
        UserActivity.DRIVING -> Icons.Default.DirectionsCar
        UserActivity.CYCLING -> Icons.Default.DirectionsBike
        UserActivity.STATIONARY -> Icons.Default.Chair
        UserActivity.UNKNOWN -> Icons.Default.QuestionMark
    }
}

fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${duration.seconds}s"
    }
}
```

---

## Phase 5: Integration & Testing (Week 3)

### 5.1 Update DI Module

**File:** `di/UseCasesModule.kt`

```kotlin
@Provides
@Singleton
fun provideActivityAnalyticsUseCases(
    locationRepository: LocationRepository,
    visitRepository: VisitRepository,
    placeRepository: PlaceRepository
): ActivityAnalyticsUseCases {
    return ActivityAnalyticsUseCases(
        locationRepository,
        visitRepository,
        placeRepository
    )
}
```

### 5.2 Add to Navigation

**File:** `presentation/navigation/VoyagerNavHost.kt`

```kotlin
composable(VoyagerDestination.ActivityInsights.route) {
    ActivityInsightsScreen()
}
```

### 5.3 Update Preferences

**File:** `domain/model/UserPreferences.kt`

```kotlin
data class UserPreferences(
    // ... existing fields ...

    // NEW: Activity recognition settings
    val enableActivityRecognition: Boolean = true,
    val activityConfidenceThreshold: Float = 0.75f,
    val skipLocationsWhileMoving: Boolean = true, // Prevent false places while driving

    // NEW: Place naming preferences
    val autoAcceptOsmNames: Boolean = true,
    val promptForCustomNames: Boolean = true,
    val preferLocalLanguage: Boolean = true
)
```

---

## Expected Improvements

### Before Activity-First Implementation

```
Dashboard:
- "You visited 5 places today"
- "Total time: 8 hours"
- Places: "Home", "Work", "Restaurant", "Unknown Place"

Timeline:
8:00 AM - Arrived at Work
5:00 PM - Left Work
6:00 PM - Arrived at Restaurant
8:00 PM - Arrived at Home
```

### After Activity-First Implementation

```
Dashboard:
- "You worked for 8h 30m today"
- "You worked out for 45 minutes"
- "You commuted for 1h 20m"
- Places: "Home", "Tech Corp Office", "Gold's Gym", "Starbucks Coffee"

Timeline:
7:00 AM - Left Home
7:30 AM - Commuting (driving, 30 min)
8:00 AM - Arrived at Tech Corp Office (working)
5:00 PM - Left Tech Corp Office
5:15 PM - Working out at Gold's Gym (45 min workout)
6:00 PM - Eating at Starbucks Coffee (30 min)
6:30 PM - Commuting home (driving, 20 min)
7:00 PM - Arrived at Home (relaxing)

Activity Insights:
This Week:
- 🏃 3 workouts (2h 15m total)
  - Monday: Running, 45 min, 5.2 km
  - Wednesday: Gym session, 1h
  - Friday: Cycling, 30 min, 8 km

- 🚗 10 commutes (avg 35 min)
- 💼 Working: 42h 30m
- 🏠 Home: 68h
- 🍽️ Dining out: 3h 30m
```

---

## Implementation Checklist

### Week 1: Core Activity Tracking
- [ ] Add activity fields to Location model
- [ ] Migrate database schema (v1 → v2)
- [ ] Modify LocationTrackingService to capture activity
- [ ] Add basic semantic context inference
- [ ] Test activity data is being saved correctly

### Week 2: Activity-Aware Place Detection
- [ ] Modify DBSCAN to filter moving locations
- [ ] Add activity analysis to clusters
- [ ] Implement activity-based category inference
- [ ] Integrate geocoding for real place names
- [ ] Add PlaceNameDialog UI component
- [ ] Update Timeline to show "Name This" badges

### Week 3: Activity Insights & Polish
- [ ] Implement ActivityAnalyticsUseCases
- [ ] Create ActivityInsightsScreen UI
- [ ] Add workout session detection
- [ ] Add commute analysis
- [ ] Wire up DI modules
- [ ] Add to navigation
- [ ] End-to-end testing

### Week 4: Testing & Refinement
- [ ] Field test for 3-5 days
- [ ] Verify activity recognition accuracy
- [ ] Verify place naming quality
- [ ] Fix any bugs found
- [ ] Optimize battery usage
- [ ] Polish UI/UX

---

## Success Metrics

**Activity Recognition Accuracy:**
- Target: >85% correct activity detection
- Measure: Manual verification against daily activities

**Place Naming Quality:**
- Target: >70% places have real names (not "Unknown Place")
- Target: >90% user satisfaction with place names
- Measure: % of places users manually rename

**Battery Impact:**
- Target: <5% additional battery drain from activity recognition
- Measure: Compare battery usage before/after implementation

**User Insights Value:**
- Target: Users can answer "How much did I work out this week?" in <5 seconds
- Target: Users find activity insights more valuable than location data alone
- Measure: User surveys, feature usage analytics

---

## Risk Mitigation

**Risk 1: Activity Recognition Inaccuracy**
- Mitigation: Use hybrid approach (Google API + motion sensors)
- Mitigation: Adjustable confidence thresholds in settings
- Mitigation: Don't filter locations if activity unknown

**Risk 2: Battery Drain**
- Mitigation: Use 30-second detection interval (not real-time)
- Mitigation: Pause activity recognition during sleep hours
- Mitigation: Monitor battery stats and optimize

**Risk 3: OSM API Rate Limiting**
- Mitigation: Aggressive caching (GeocodingCacheDao)
- Mitigation: Batch geocoding requests
- Mitigation: Fallback to Android Geocoder

**Risk 4: User Rejects Activity Tracking**
- Mitigation: Make activity recognition opt-in with clear benefits
- Mitigation: Show privacy-focused messaging (all data local)
- Mitigation: Allow disabling in settings

---

## Future Enhancements (Post-MVP)

1. **Machine Learning for Context**
   - Train on-device ML model to predict semantic context
   - Learn user-specific patterns (your gym routine vs others)

2. **Social Context Detection**
   - Detect when you're with others (co-location)
   - "You met with John at Starbucks for 1 hour"

3. **Routine Detection**
   - "Your Tuesday routine: Work → Gym → Groceries → Home"
   - Anomaly detection: "You skipped the gym this week"

4. **Goal Tracking**
   - Set goals: "Work out 3x per week"
   - Track progress: "2/3 workouts done this week"

5. **Integration with Fitness Apps**
   - Export workout sessions to Google Fit / Apple Health
   - Import existing fitness data to enrich insights

---

## Summary

This implementation plan transforms Voyager from a passive location tracker into an **intelligent activity journal** that understands what you're doing, not just where you are. By combining activity recognition with location tracking, we can:

1. **Filter noise** - Ignore locations captured while driving
2. **Improve accuracy** - Categorize places based on activities
3. **Show real names** - Use OSM/geocoding with user customization
4. **Generate insights** - "You worked out 3 times this week"
5. **Tell stories** - "You explored 5 new cafes this month"

The key innovation is **activity-first thinking**: instead of asking "Where was I?", we ask "What was I doing?" This makes the data far more meaningful and actionable.
