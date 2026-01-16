# Domain Models & Entities Guide

This document explains the core data models that form the heart of Voyager's business logic and their relationships.

## 🎯 Domain Layer Overview

The domain layer contains pure business entities that represent the core concepts of location tracking:

```
Location ─────┐
              ├──► Place ───► Visit ───► Analytics
Geofence ─────┘              │
                              ▼
                        CurrentState
```

## 📍 Core Entities

### 1. Location Entity

The foundational entity representing a GPS coordinate point.

```kotlin
// File: domain/model/Location.kt
data class Location(
    val id: Long = 0L,
    val latitude: Double,        // GPS latitude (-90.0 to 90.0)
    val longitude: Double,       // GPS longitude (-180.0 to 180.0)
    val timestamp: LocalDateTime, // When location was recorded
    val accuracy: Float,         // GPS accuracy in meters
    val speed: Float? = null,    // Speed in m/s (if available)
    val altitude: Double? = null, // Altitude in meters (if available)
    val bearing: Float? = null   // Direction in degrees (if available)
)
```

#### Key Characteristics:
- **Immutable**: Once created, locations never change
- **Timestamped**: Every location has a precise timestamp
- **Quality Metadata**: Includes accuracy for filtering
- **Optional Data**: Speed, altitude, bearing may not always be available

#### Usage Example:
```kotlin
// Creating a location from GPS data
val location = Location(
    latitude = 37.7749,
    longitude = -122.4194,
    timestamp = LocalDateTime.now(),
    accuracy = 10.5f, // 10.5 meters accuracy
    speed = 5.2f      // 5.2 m/s (walking speed)
)
```

### 2. Place Entity

Represents a significant location where the user spends time.

```kotlin
// File: domain/model/Place.kt
data class Place(
    val id: Long = 0L,
    val name: String,                    // User-friendly name
    val category: PlaceCategory,         // Classified type
    val latitude: Double,                // Center coordinate
    val longitude: Double,               // Center coordinate
    val address: String? = null,         // Human-readable address
    val visitCount: Int = 0,             // Number of visits
    val totalTimeSpent: Long = 0L,       // Total milliseconds spent here
    val lastVisit: LocalDateTime? = null, // Most recent visit
    val isCustom: Boolean = false,       // User-created vs auto-detected
    val radius: Double = 100.0,          // Detection radius in meters
    val placeId: String? = null,         // Google Places ID (if available)
    val confidence: Float = 1.0f         // Detection confidence (0.0-1.0)
)

enum class PlaceCategory {
    HOME,           // Residence
    WORK,           // Workplace
    GYM,            // Fitness center
    RESTAURANT,     // Dining establishment
    SHOPPING,       // Retail stores
    ENTERTAINMENT,  // Movies, events, etc.
    HEALTHCARE,     // Hospitals, clinics
    EDUCATION,      // Schools, universities
    TRANSPORT,      // Airports, stations
    TRAVEL,         // Hotels, temporary stays
    OUTDOOR,        // Parks, hiking trails
    SOCIAL,         // Friends' homes, social venues
    SERVICES,       // Banks, services
    UNKNOWN,        // Unclassified
    CUSTOM          // User-defined category
}
```

#### Key Features:

##### **Automatic Categorization**
Places are automatically categorized based on usage patterns:

```kotlin
// Example categorization logic
fun categorizePlace(locations: List<Location>): PlaceCategory {
    val hourCounts = locations.groupBy { it.timestamp.hour }
    val nightHours = hourCounts.filterKeys { it >= 22 || it <= 6 }.values.sum()
    val workHours = hourCounts.filterKeys { it in 9..17 }.values.sum()
    
    return when {
        nightHours > locations.size * 0.6 -> PlaceCategory.HOME
        workHours > locations.size * 0.7 -> PlaceCategory.WORK
        else -> PlaceCategory.UNKNOWN
    }
}
```

##### **Confidence Scoring**
Each place has a confidence score indicating detection reliability:

```kotlin
fun calculateConfidence(
    locationCount: Int,
    averageAccuracy: Float,
    timeSpanDays: Int,
    category: PlaceCategory
): Float {
    val baseConfidence = when (category) {
        PlaceCategory.HOME -> 0.7f
        PlaceCategory.WORK -> 0.6f
        PlaceCategory.GYM -> 0.5f
        else -> 0.3f
    }
    
    val countBonus = minOf(locationCount / 30.0f, 0.25f)
    val accuracyBonus = if (averageAccuracy <= 25f) 0.1f else 0.0f
    val timeBonus = if (timeSpanDays >= 7) 0.1f else 0.0f
    
    return (baseConfidence + countBonus + accuracyBonus + timeBonus).coerceAtMost(0.95f)
}
```

### 3. Visit Entity

Represents a period of time spent at a specific place.

```kotlin
// File: domain/model/Visit.kt
data class Visit(
    val id: Long = 0L,
    val placeId: Long,                   // Reference to Place
    val entryTime: LocalDateTime,        // When visit started
    val exitTime: LocalDateTime? = null, // When visit ended (null = active)
    val _duration: Long = 0L,            // Stored duration in milliseconds
    val confidence: Float = 1.0f         // Visit detection confidence
) {
    // Computed property for duration
    val duration: Long 
        get() = when {
            _duration > 0L -> _duration
            exitTime != null -> Duration.between(entryTime, exitTime!!).toMillis()
            else -> 0L // Active visit, no duration yet
        }
    
    // Helper properties
    val isActive: Boolean get() = exitTime == null
    
    // Methods for visit management
    fun complete(exitTime: LocalDateTime): Visit {
        val calculatedDuration = Duration.between(entryTime, exitTime).toMillis()
        return copy(
            exitTime = exitTime,
            _duration = calculatedDuration
        )
    }
    
    fun getCurrentDuration(currentTime: LocalDateTime = LocalDateTime.now()): Long {
        return when {
            !isActive -> duration
            else -> Duration.between(entryTime, currentTime).toMillis()
        }
    }
}
```

#### Visit Lifecycle:

1. **Visit Start**: User enters place detection radius
```kotlin
// Starting a new visit
val visit = Visit(
    placeId = homePlace.id,
    entryTime = LocalDateTime.now(),
    confidence = 0.9f
)
```

2. **Active Visit**: Visit is ongoing, duration calculated in real-time
```kotlin
// Getting current duration of active visit
val activeDuration = visit.getCurrentDuration()
```

3. **Visit End**: User exits place detection radius
```kotlin
// Completing a visit
val completedVisit = visit.complete(LocalDateTime.now())
println("Visit duration: ${completedVisit.duration}ms")
```

### 4. Geofence Entity

Virtual boundaries around places for automatic visit detection.

```kotlin
// File: domain/model/Geofence.kt
data class Geofence(
    val id: Long = 0L,
    val name: String,               // Human-readable name
    val latitude: Double,           // Center latitude
    val longitude: Double,          // Center longitude
    val radius: Double,             // Radius in meters
    val isActive: Boolean = true,   // Whether geofence is monitoring
    val enterAlert: Boolean = false, // Notify on entry
    val exitAlert: Boolean = false, // Notify on exit
    val placeId: Long? = null       // Associated place (optional)
)

data class GeofenceEvent(
    val id: Long = 0L,
    val geofenceId: Long,
    val eventType: GeofenceEventType,
    val timestamp: LocalDateTime,
    val accuracy: Float
)

enum class GeofenceEventType {
    ENTER,  // User entered the geofence
    EXIT,   // User exited the geofence
    DWELL   // User stayed in geofence for extended time
}
```

#### Geofence Lifecycle:

```kotlin
// 1. Create geofence for a place
val geofence = Geofence(
    name = "Home Geofence",
    latitude = homePlace.latitude,
    longitude = homePlace.longitude,
    radius = homePlace.radius,
    placeId = homePlace.id
)

// 2. Register with Android system
geofenceRepository.registerGeofence(geofence)

// 3. Handle transitions
fun handleGeofenceTransition(event: GeofenceEvent) {
    when (event.eventType) {
        GeofenceEventType.ENTER -> startVisit(event.geofenceId)
        GeofenceEventType.EXIT -> endVisit(event.geofenceId)
        GeofenceEventType.DWELL -> updateVisitConfidence(event.geofenceId)
    }
}
```

### 5. CurrentState Entity

Represents the current state of the application and user location.

```kotlin
// File: domain/model/CurrentState.kt
data class CurrentState(
    val id: Int = 1, // Always 1 - singleton record
    val isLocationTrackingActive: Boolean = false,
    val locationTrackingStartTime: LocalDateTime? = null,
    val lastLocationUpdate: LocalDateTime? = null,
    val currentPlace: Place? = null,
    val currentVisitId: Long? = null,
    val visitEntryTime: LocalDateTime? = null,
    val todayLocationCount: Int = 0,
    val todayPlacesVisited: Int = 0,
    val todayTimeTracked: Long = 0L // milliseconds
) {
    // Computed properties
    val isAtPlace: Boolean get() = currentPlace != null
    
    val trackingDuration: Long? get() = 
        locationTrackingStartTime?.let { 
            Duration.between(it, LocalDateTime.now()).toMillis() 
        }
    
    val currentVisitDuration: Long? get() = 
        visitEntryTime?.let { 
            Duration.between(it, LocalDateTime.now()).toMillis() 
        }
}
```

## 🔗 Entity Relationships

### Primary Relationships

```kotlin
// One-to-Many: Place → Visits
class Place {
    suspend fun getVisits(): List<Visit> {
        return visitRepository.getVisitsForPlace(this.id)
    }
    
    suspend fun getTotalTimeSpent(): Long {
        return getVisits().sumOf { it.duration }
    }
}

// One-to-One: Place → Geofence
class Place {
    suspend fun getGeofence(): Geofence? {
        return geofenceRepository.getGeofenceForPlace(this.id)
    }
}

// Many-to-One: Locations → Place (via clustering)
class PlaceDetectionUseCases {
    suspend fun detectPlaceFromLocations(locations: List<Location>): Place? {
        val center = calculateCenter(locations)
        val category = categorizeFromPatterns(locations)
        
        return Place(
            latitude = center.latitude,
            longitude = center.longitude,
            category = category,
            radius = calculateOptimalRadius(locations)
        )
    }
}
```

### Aggregation Examples

```kotlin
// Place summary with visit statistics
data class PlaceWithVisits(
    val place: Place,
    val visits: List<Visit>
) {
    val totalDuration: Long = visits.sumOf { it.duration }
    val averageDuration: Long = if (visits.isNotEmpty()) totalDuration / visits.size else 0L
    val lastVisit: LocalDateTime? = visits.maxByOrNull { it.entryTime }?.entryTime
}

// Visit summary for analytics
data class VisitSummary(
    val place: Place,
    val totalDuration: Long,
    val visitCount: Int,
    val averageDuration: Long,
    val lastVisit: LocalDateTime?
)

// Daily analytics aggregation
data class DayStats(
    val date: LocalDate,
    val locationCount: Int,
    val placesVisited: Set<Place>,
    val visits: List<Visit>,
    val totalTimeTracked: Long
)
```

## 🎯 Business Rules & Validation

### Location Validation

```kotlin
object LocationValidator {
    fun isValid(location: Location): Boolean {
        return location.latitude in -90.0..90.0 &&
               location.longitude in -180.0..180.0 &&
               location.accuracy > 0f &&
               location.accuracy <= 10000f // Max 10km accuracy
    }
    
    fun hasGoodAccuracy(location: Location, threshold: Float = 50f): Boolean {
        return location.accuracy <= threshold
    }
    
    fun isRecentEnough(location: Location, maxAgeHours: Int = 24): Boolean {
        val ageHours = Duration.between(location.timestamp, LocalDateTime.now()).toHours()
        return ageHours <= maxAgeHours
    }
}
```

### Visit Business Rules

```kotlin
object VisitBusinessRules {
    const val MIN_VISIT_DURATION_MINUTES = 5
    const val MAX_VISIT_DURATION_HOURS = 24
    
    fun isValidVisit(visit: Visit): Boolean {
        if (visit.isActive) return true // Active visits are always valid
        
        val durationMinutes = visit.duration / (1000 * 60)
        return durationMinutes >= MIN_VISIT_DURATION_MINUTES &&
               durationMinutes <= MAX_VISIT_DURATION_HOURS * 60
    }
    
    fun shouldAutoComplete(visit: Visit, lastLocationTime: LocalDateTime): Boolean {
        val hoursSinceEntry = Duration.between(visit.entryTime, lastLocationTime).toHours()
        return hoursSinceEntry > MAX_VISIT_DURATION_HOURS
    }
}
```

### Place Detection Rules

```kotlin
object PlaceDetectionRules {
    const val MIN_LOCATIONS_FOR_PLACE = 10
    const val MIN_TIME_SPAN_HOURS = 2
    const val MAX_RADIUS_METERS = 500.0
    
    fun canCreatePlace(locations: List<Location>): Boolean {
        if (locations.size < MIN_LOCATIONS_FOR_PLACE) return false
        
        val timeSpan = Duration.between(
            locations.minOf { it.timestamp },
            locations.maxOf { it.timestamp }
        ).toHours()
        
        return timeSpan >= MIN_TIME_SPAN_HOURS
    }
    
    fun calculateOptimalRadius(locations: List<Location>): Double {
        val center = calculateCenter(locations)
        val distances = locations.map { 
            calculateDistance(center, it) 
        }
        
        // Use 90th percentile as radius
        val radius = distances.sorted()[distances.size * 0.9]
        return radius.coerceIn(25.0, MAX_RADIUS_METERS)
    }
}
```

## 🔄 Data Flow Examples

### Complete Location-to-Visit Flow

```kotlin
// 1. GPS location received
val location = Location(
    latitude = 37.7749,
    longitude = -122.4194,
    timestamp = LocalDateTime.now(),
    accuracy = 15.0f
)

// 2. Process through Smart Data Processor
smartDataProcessor.processNewLocation(location)

// 3. Check for nearby places
val nearbyPlace = placeRepository.getPlacesNearLocation(
    location.latitude, 
    location.longitude, 
    100.0 // meters
).firstOrNull()

// 4. Start or continue visit
if (nearbyPlace != null) {
    val currentVisit = visitRepository.getCurrentVisit()
    if (currentVisit == null || currentVisit.placeId != nearbyPlace.id) {
        // Start new visit
        val visitId = visitRepository.startVisit(nearbyPlace.id, location.timestamp)
        currentStateRepository.updateCurrentPlace(nearbyPlace.id, visitId, location.timestamp)
    }
} else {
    // End current visit if exists
    visitRepository.getCurrentVisit()?.let { visit ->
        val completedVisit = visit.complete(location.timestamp)
        visitRepository.updateVisit(completedVisit)
        currentStateRepository.clearCurrentPlace()
    }
}
```

### Analytics Query Examples

```kotlin
// Get today's stats
suspend fun getTodayStats(): DayStats {
    val today = LocalDate.now()
    val startOfDay = today.atStartOfDay()
    val endOfDay = today.plusDays(1).atStartOfDay()
    
    val visits = visitRepository.getVisitsBetween(startOfDay, endOfDay)
    val places = visits.map { it.placeId }.distinct()
        .let { placeRepository.getPlacesByIds(it) }
    val locationCount = locationRepository.getLocationCountSince(startOfDay)
    
    return DayStats(
        date = today,
        locationCount = locationCount,
        placesVisited = places.toSet(),
        visits = visits,
        totalTimeTracked = visits.sumOf { it.duration }
    )
}

// Get place frequency over time
suspend fun getPlaceFrequency(days: Int = 30): Map<Place, Int> {
    val cutoff = LocalDateTime.now().minusDays(days.toLong())
    val visits = visitRepository.getVisitsSince(cutoff)
    
    return visits.groupBy { it.placeId }
        .mapKeys { (placeId, _) -> placeRepository.getPlaceById(placeId)!! }
        .mapValues { (_, visits) -> visits.size }
}
```

---

*Next: [Dependency Injection with Hilt](./DEPENDENCY_INJECTION.md)*