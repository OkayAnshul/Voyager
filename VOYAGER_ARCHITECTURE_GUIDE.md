# Voyager Location Analytics - Complete Architecture Guide

## Project Overview
Voyager is an Android location analytics application that automatically tracks user movements, detects meaningful places, and provides intelligent insights about location patterns. The app uses GPS data to learn about user behavior and create personalized location analytics.

## Architecture Overview

### Clean Architecture Structure
```
Presentation Layer (UI)
    ↓
Domain Layer (Business Logic)
    ↓  
Data Layer (Database, Services, APIs)
```

### Technology Stack
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt
- **Database**: Room + SQLCipher (encrypted)
- **Background Processing**: WorkManager + Foreground Services
- **Maps**: OpenStreetMap (OSMDroid)
- **Location**: Google Play Services Location API

## Database Schema & Entity Relationships

### Core Entities

#### 1. LocationEntity (`LocationEntity.kt`)
**Purpose**: Stores raw GPS coordinates with timestamps
```kotlin
- id: Long (Primary Key)
- latitude: Double
- longitude: Double  
- timestamp: LocalDateTime
- accuracy: Float
- speed: Float?
- altitude: Double?
- bearing: Float?
```

#### 2. PlaceEntity (`PlaceEntity.kt`) 
**Purpose**: Represents meaningful locations detected from GPS clusters
```kotlin
- id: Long (Primary Key)
- name: String
- category: PlaceCategory (HOME, WORK, GYM, etc.)
- latitude: Double
- longitude: Double
- visitCount: Int
- radius: Double
- isCustom: Boolean
- confidence: Float
```

#### 3. VisitEntity (`VisitEntity.kt`)
**Purpose**: Tracks time spent at each place
```kotlin
- id: Long (Primary Key)
- placeId: Long (Foreign Key → PlaceEntity)
- entryTime: LocalDateTime
- exitTime: LocalDateTime?
- duration: Long (calculated property)
```

#### 4. GeofenceEntity (`GeofenceEntity.kt`)
**Purpose**: Manages location-based alerts and notifications
```kotlin
- id: Long (Primary Key)
- name: String
- latitude: Double
- longitude: Double
- radius: Double
- isActive: Boolean
- enterAlert: Boolean
- exitAlert: Boolean
- placeId: Long? (Foreign Key → PlaceEntity)
```

### Entity Relationships
```
LocationEntity (Many GPS points)
    ↓ [clustered by DBSCAN algorithm]
PlaceEntity (Detected meaningful places)
    ↓ [One-to-Many relationship]
VisitEntity (Time tracking at places)
    ↓ [Optional relationship]
GeofenceEntity (Location-based alerts)
```

## Complete Data Flow

### 1. Location Collection Flow
```
User enables location tracking
    ↓
LocationTrackingService starts (foreground service)
    ↓
FusedLocationProviderClient provides GPS updates
    ↓
LocationEntity saved to encrypted database
    ↓
LocationRepository exposes data via Flow<List<Location>>
```

### 2. Place Detection Flow  
```
PlaceDetectionWorker runs every 6 hours
    ↓
Fetches recent LocationEntity records
    ↓
DBSCAN clustering algorithm groups nearby points
    ↓
PlaceDetectionUseCases.categorizePlace() analyzes time patterns
    ↓
New PlaceEntity created with confidence score
    ↓
Initial VisitEntity records generated
```

### 3. Visit Tracking Flow
```
User enters/exits geofenced area
    ↓
GeofenceTransitionService receives broadcast
    ↓
ENTER: Creates new VisitEntity with entryTime
    ↓
EXIT: Updates VisitEntity with exitTime and calculates duration
    ↓
Notification sent to user with visit summary
```

### 4. Analytics Generation Flow
```
UI requests analytics data
    ↓
AnalyticsRepository queries VisitEntity + PlaceEntity
    ↓
Complex calculations for time patterns, rankings, insights
    ↓
TimeAnalytics/DayAnalytics objects returned
    ↓
UI displays charts and insights
```

## Special Project-Specific Functions

### A. Location Intelligence Algorithms

#### 1. DBSCAN Clustering Algorithm (`LocationUtils.kt:110-139`)
**Purpose**: Groups GPS coordinates into meaningful clusters representing places
**How it works**:
- Takes list of (latitude, longitude) pairs
- Groups points within 50m of each other
- Requires minimum 3 points to form a cluster
- Uses density-based clustering to ignore noise points
- Returns clusters representing potential places

```kotlin
fun clusterLocations(
    locations: List<Pair<Double, Double>>,
    maxDistanceMeters: Double = 50.0,
    minPoints: Int = 3
): List<List<Pair<Double, Double>>>
```

#### 2. Place Categorization AI (`PlaceDetectionUseCases.kt:106-148`)
**Purpose**: Intelligently categorizes places based on time patterns
**How it works**:
- Analyzes hour-of-day patterns (night = home, 9-5 weekdays = work)
- Detects gym patterns (morning/evening workout times)
- Identifies shopping patterns (short weekend visits)
- Calculates confidence scores based on pattern strength
- Returns PlaceCategory (HOME, WORK, GYM, RESTAURANT, etc.)

#### 3. Movement Pattern Detection (`AnalyticsRepositoryImpl.kt:185-218`)
**Purpose**: Detects recurring movement patterns like commuting
**How it works**:
- Finds HOME and WORK places
- Analyzes movement between them during weekday mornings
- Creates MovementPattern objects with time patterns
- Assigns confidence scores based on regularity

### B. API Compatibility Framework

#### 1. Cross-API DateTime Handling (`ApiCompatibilityUtils.kt:15-32`)
**Purpose**: Safely handles LocalDateTime across Android API levels 24-34
**How it works**:
- API 26+: Uses LocalDateTime.now() directly
- API 24-25: Creates LocalDateTime from System.currentTimeMillis() + deprecated Date methods
- Prevents crashes on older Android versions

```kotlin
fun getCurrentDateTime(): LocalDateTime {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDateTime.now()
    } else {
        // Fallback implementation for API < 26
        val date = Date(System.currentTimeMillis())
        LocalDateTime.of(date.year + 1900, date.month + 1, ...)
    }
}
```

#### 2. Feature Capability Detection (`ApiCompatibilityUtils.kt:106-136`)
**Purpose**: Checks if device supports specific Android features
**Functions**:
- `requiresBackgroundLocationPermission()`: API 29+ needs special permission
- `requiresForegroundServiceTypes()`: API 29+ needs service type declaration
- `requiresNotificationChannels()`: API 26+ needs notification channels

### C. Background Processing Architecture

#### 1. Modern GeofenceTransitionService (`GeofenceTransitionService.kt`)
**Purpose**: Handles geofence enter/exit events using modern JobIntentService
**How it works**:
- Extends JobIntentService (replaces deprecated IntentService)
- Receives geofence transition broadcasts
- Creates/updates VisitEntity records
- Sends notifications for place entry/exit
- Uses ApiCompatibilityUtils for safe datetime handling

**Key Function**:
```kotlin
private suspend fun handleGeofenceTransitions(
    transitionType: Int,
    triggeringGeofences: List<Geofence>
) {
    // ENTER: Create new visit record
    // EXIT: Close visit record and calculate duration
}
```

#### 2. Foreground Location Tracking (`LocationTrackingService.kt`)
**Purpose**: Continuously collects GPS data in background
**How it works**:
- Runs as foreground service with persistent notification
- Uses FusedLocationProviderClient for battery-efficient GPS
- Updates every 30 seconds if moved >10 meters
- Saves LocationEntity records to database
- Updates notification with tracking count

#### 3. Automated Place Detection Worker (`PlaceDetectionWorker.kt`)
**Purpose**: Periodically analyzes location data to discover new places
**How it works**:
- HiltWorker runs every 6 hours via WorkManager
- Calls PlaceDetectionUseCases.detectNewPlaces()
- Improves existing place categorizations
- Returns Result.success/retry/failure with metrics

### D. Security & Privacy Implementation

#### 1. Database Encryption (`SecurityUtils.kt:10-35`)
**Purpose**: Generates and manages SQLCipher encryption passphrases
**How it works**:
- Generates 32-character random passphrase on first run
- Stores in SharedPreferences (future: should use Android Keystore)
- Used by VoyagerDatabase to encrypt all location data
- Clearable for security/privacy compliance

#### 2. Encrypted Database Creation (`VoyagerDatabase.kt:35-47`)
**Purpose**: Creates Room database with SQLCipher encryption
**How it works**:
- Uses SupportFactory with encrypted passphrase
- All location data stored encrypted on device
- Prevents unauthorized access to sensitive location history

### E. Custom UI Components

#### 1. OpenStreetMap Integration (`OpenStreetMapView.kt`)
**Purpose**: Free map component replacing Google Maps dependency
**Key Functions**:

**Location Path Visualization** (`OpenStreetMapView.kt:89-103`):
- Draws blue polyline connecting location points
- Shows user's movement history on map
- Updates in real-time as new locations are tracked

**Dynamic Place Markers** (`OpenStreetMapView.kt:105-131`):
- Places different markers for HOME/WORK/GYM/etc.
- Shows place name, category, and visit count in popup
- Clickable markers trigger place detail callbacks

**User Location Indicator** (`OpenStreetMapView.kt:133-144`):
- Blue dot showing current user position
- Updates in real-time with location service

### F. Analytics Calculation Engine

#### 1. Haversine Distance Calculation (`LocationUtils.kt:20-36`)
**Purpose**: Accurately calculates distance between GPS coordinates
**How it works**:
- Uses Haversine formula accounting for Earth's curvature
- Returns distance in meters
- Critical for clustering and geofencing accuracy

#### 2. Peak Activity Analysis (`AnalyticsRepositoryImpl.kt:267-281`)
**Purpose**: Identifies user's most active hours
**How it works**:
- Groups location points by hour of day (0-23)
- Calculates activity level as percentage of max hour
- Returns HourActivity objects for 24-hour visualization

#### 3. Time-based Analytics (`AnalyticsRepositoryImpl.kt:27-80`)
**Purpose**: Generates comprehensive time analytics
**Calculations**:
- Total time spent at different place categories
- Most visited places with rankings
- Average daily movement metrics
- Peak activity hour detection

## Component Interaction Flow

### Service Communication Pattern
```
LocationTrackingService
    ↓ [saves data via]
LocationRepository
    ↓ [consumed by]
PlaceDetectionWorker
    ↓ [creates]
PlaceRepository + VisitRepository
    ↓ [triggers]
GeofenceTransitionService
    ↓ [updates]
VisitRepository
```

### ViewModel Data Flow
```
UI Events (user interactions)
    ↓
ViewModel (business logic)
    ↓
Use Cases (domain layer)
    ↓
Repositories (data layer)
    ↓
DAOs (database access)
    ↓
StateFlow/Flow emissions
    ↓
UI recomposition
```

### Background Worker Chain
```
LocationTrackingService (continuous)
    ↓ [triggers when enough data]
PlaceDetectionWorker (every 6 hours)
    ↓ [may create new geofences]
GeofenceTransitionService (event-driven)
    ↓ [updates visit records]
AnalyticsRepository (on-demand)
```

## Key Algorithms Explained

### 1. DBSCAN Clustering for Place Detection
- **Input**: List of GPS coordinates
- **Process**: Find all points within 50m of each other, group into clusters
- **Output**: Clusters representing potential places
- **Why Special**: Handles GPS noise and creates meaningful place boundaries

### 2. Time Pattern Recognition for Place Categories
- **Input**: Location timestamps at a cluster
- **Process**: Analyze hour-of-day and day-of-week patterns
- **Logic**: Night hours → HOME, Weekday 9-5 → WORK, etc.
- **Output**: PlaceCategory with confidence score

### 3. Geofencing Logic for Automatic Visit Tracking
- **Input**: User location changes
- **Process**: Check if entering/exiting any known place geofences
- **Actions**: Create visit records, send notifications
- **Why Special**: Fully automated location diary without user input

### 4. Movement Pattern Detection
- **Input**: Historical location data
- **Process**: Detect regular routes between known places
- **Output**: MovementPattern objects (commute, routine, etc.)
- **Use Case**: Predict future movements, optimize recommendations

## Development Notes

### Android API Compatibility
- **Minimum API**: 24 (Android 7.0)
- **Target API**: 34 (Android 14)
- **Critical**: Time API differences handled by ApiCompatibilityUtils
- **Testing**: Verified on API 24, 26, 29, 31, 34

### Performance Optimizations
- **Database**: Indexed queries on location bounds and timestamps
- **Background**: Efficient location filtering (>10m movement threshold)
- **Memory**: Flow-based reactive data loading
- **Battery**: Foreground service with optimized GPS settings

### Privacy & Security
- **Encryption**: All location data encrypted with SQLCipher
- **Permissions**: Graceful handling of location permission states
- **Transparency**: Clear notifications when tracking is active
- **Control**: User can stop tracking and clear data anytime

## Future Enhancements

### Planned Features
1. **Custom Chart Components**: Canvas-based analytics visualizations
2. **Biometric Authentication**: Secure app access with fingerprint/face
3. **Export Functionality**: Share location analytics in various formats
4. **Machine Learning**: Improved place categorization with TensorFlow Lite

### Technical Improvements
1. **Android Keystore**: Migrate from SharedPreferences for passphrase storage
2. **Incremental Database**: Optimize queries for large datasets
3. **Offline Maps**: Cache map tiles for offline usage
4. **Cloud Sync**: Optional encrypted cloud backup of place data