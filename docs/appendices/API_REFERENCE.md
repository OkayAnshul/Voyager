# Appendix A: API Reference & Function Wiring Map

**Last Updated:** December 11, 2025

This appendix provides a comprehensive reference for all use cases, repositories, and their function wiring throughout the Voyager application.

---

## Table of Contents

1. [Use Cases](#use-cases)
2. [Repositories](#repositories)
3. [Data Flow Diagrams](#data-flow-diagrams)
4. [Function Call Chains](#function-call-chains)

---

## Use Cases

### PlaceDetectionUseCases

**File:** `domain/usecase/PlaceDetectionUseCases.kt` (1,026 lines)

**Dependencies:**
- LocationRepository
- PlaceRepository
- VisitRepository
- PreferencesRepository
- ErrorHandler
- ValidationService
- EnrichPlaceWithDetailsUseCase
- AutoAcceptDecisionUseCase
- PlaceReviewUseCases
- CategoryLearningEngine

**Primary Functions:**

#### `detectNewPlaces(): List<Place>`
**Purpose:** Main place detection algorithm using DBSCAN clustering

**Wired To:**
- PlaceDetectionWorker.doWork()
- DashboardViewModel.triggerPlaceDetection()

**Call Chain:**
```
detectNewPlaces()
  ├─ locationRepository.getRecentLocations(5000)
  ├─ filterLocationsByQuality(locations)
  │   ├─ Check accuracy (< 100m)
  │   ├─ Check speed (< 150 km/h)
  │   └─ Check activity (not DRIVING)
  ├─ LocationUtils.clusterLocationsWithPreferences(filtered, prefs)
  │   └─ DBSCAN algorithm (eps=50m, minPts=3)
  ├─ For each cluster:
  │   ├─ createPlaceFromCluster(cluster)
  │   ├─ enrichPlaceWithDetailsUseCase(place)
  │   │   ├─ NominatimGeocodingService.geocode()
  │   │   └─ AndroidGeocoderService.geocode()
  │   ├─ autoAcceptDecisionUseCase.shouldAutoAccept(place)
  │   ├─ placeReviewUseCases.createPlaceReview(place)
  │   ├─ createInitialVisits(place, clusterLocations)
  │   └─ placeRepository.insertPlace(place)
  └─ Return detected places
```

**Returns:** List of newly detected Place objects with enriched data

#### `filterLocationsByQuality(locations: List<Location>): List<Location>`
**Purpose:** Remove poor GPS data before clustering

**Filters Applied:**
1. Accuracy filter: `location.accuracy <= 100m`
2. Speed filter: `location.speed <= 150 km/h`
3. Activity filter: Exclude `DRIVING` activity
4. Error filter: Remove locations with errors

**Returns:** Filtered list typically 40-60% of original size

#### `improveExistingPlaces(): Int`
**Purpose:** Refine existing places with new location data

**Wired To:**
- PlaceDetectionWorker.doWork() (called after detectNewPlaces)

**Process:**
1. Get all existing places
2. For each place, get recent nearby locations
3. Recalculate optimal radius
4. Update confidence score
5. Re-enrich with geocoding if outdated
6. Update in database

**Returns:** Count of places improved

---

### AnalyticsUseCases

**File:** `domain/usecase/AnalyticsUseCases.kt`

**Dependencies:**
- VisitRepository
- PlaceRepository
- LocationRepository

**Functions:**

#### `generateDayAnalytics(date: LocalDate): DayAnalytics`
**Purpose:** Generate analytics for a specific day

**Wired To:**
- DashboardViewModel.loadDashboardData()
- TimelineViewModel.loadTimelineForDate()
- StatisticsViewModel.loadStatistics()

**Process:**
```
generateDayAnalytics(date)
  ├─ visitRepository.getVisitsForDate(date)
  ├─ placeRepository.getPlacesByIds(visitIds)
  ├─ Calculate:
  │   ├─ totalVisits = visits.size
  │   ├─ totalDuration = visits.sumOf { it.duration }
  │   ├─ uniquePlaces = visits.distinctBy { it.placeId }.size
  │   ├─ categoryBreakdown = groupByCategory(visits, places)
  │   └─ topPlaces = sortByTimeSpent(places).take(5)
  └─ Return DayAnalytics(...)
```

**Returns:** DayAnalytics object with statistics

#### `generateWeekAnalytics(startDate: LocalDate): WeekAnalytics`
**Wired To:** StatisticsViewModel, InsightsViewModel

**Returns:** WeekAnalytics with 7-day summary

#### `getCurrentStateAnalytics(): CurrentStateAnalytics`
**Wired To:** DashboardViewModel (real-time updates)

**Returns:** Live analytics for current tracking session

---

### PlaceReviewUseCases

**File:** `domain/usecase/PlaceReviewUseCases.kt`

**Dependencies:**
- PlaceReviewRepository
- PlaceRepository
- CategoryLearningEngine
- UserCorrectionRepository

**Functions:**

#### `getPendingReviews(): Flow<List<PlaceReview>>`
**Purpose:** Get all places needing user review

**Wired To:**
- PlaceReviewViewModel.loadPendingReviews()
- DashboardViewModel.getPendingReviewCount()

**Filter:** `status = PENDING, ordered by priority DESC`

#### `approvePlace(reviewId: Long): Result<Unit>`
**Purpose:** User approves detected place

**Process:**
```
approvePlace(reviewId)
  ├─ placeReviewRepository.getReviewById(reviewId)
  ├─ Update review status to APPROVED
  ├─ categoryLearningEngine.learnFromAcceptance(place)
  │   └─ Increase confidence for detected category
  └─ Return Success
```

#### `editAndApprovePlace(reviewId: Long, newName: String, newCategory: PlaceCategory)`
**Purpose:** User edits and approves place

**Process:**
```
editAndApprovePlace(reviewId, newName, newCategory)
  ├─ Get review and place
  ├─ If category changed:
  │   └─ categoryLearningEngine.learnFromCorrection(
  │         originalCategory, newCategory, place)
  ├─ Update place with new name/category
  ├─ Approve review
  └─ Return Success
```

#### `rejectPlace(reviewId: Long): Result<Unit>`
**Purpose:** User rejects detected place (deletes it)

**Process:**
```
rejectPlace(reviewId)
  ├─ Get review and place
  ├─ categoryLearningEngine.learnFromRejection(place)
  ├─ placeRepository.deletePlace(placeId) [Cascade deletes visits]
  ├─ placeReviewRepository.deleteReview(reviewId)
  └─ Return Success
```

---

### LocationUseCases

**File:** `domain/usecase/LocationUseCases.kt`

**Dependencies:**
- LocationRepository
- LocationServiceManager
- PermissionManager

**Functions:**

#### `startLocationTracking(): Result<Unit>`
**Wired To:** DashboardViewModel.toggleLocationTracking()

**Process:**
```
startLocationTracking()
  ├─ Check permissions
  │   ├─ Fine location granted?
  │   └─ Background location granted?
  ├─ locationServiceManager.startService()
  │   └─ Starts LocationTrackingService (foreground)
  └─ Return Success/Error
```

#### `stopLocationTracking(): Result<Unit>`
**Wired To:** DashboardViewModel.toggleLocationTracking()

#### `getRecentLocations(limit: Int): Flow<List<Location>>`
**Wired To:** Multiple ViewModels, PlaceDetectionUseCases

---

## Repositories

### LocationRepository

**Interface:** `domain/repository/LocationRepository.kt`
**Implementation:** `data/repository/LocationRepositoryImpl.kt`

**Functions:**

#### `insertLocation(location: Location): Long`
**Called By:**
- LocationTrackingService.saveLocation()
- SmartDataProcessor.processNewLocation()

**Database:** `INSERT INTO locations VALUES (...)`

#### `getRecentLocations(limit: Int): Flow<List<Location>>`
**Called By:**
- PlaceDetectionUseCases.detectNewPlaces()
- AnalyticsUseCases.calculateDistanceTraveled()

**Database:** `SELECT * FROM locations ORDER BY timestamp DESC LIMIT ?`

#### `getLocationsByDateRange(start: Long, end: Long): Flow<List<Location>>`
**Called By:**
- TimelineViewModel (for route visualization)

**Database:** `SELECT * FROM locations WHERE timestamp BETWEEN ? AND ?`

---

### PlaceRepository

**Interface:** `domain/repository/PlaceRepository.kt`
**Implementation:** `data/repository/PlaceRepositoryImpl.kt`

**Functions:**

#### `insertPlace(place: Place): Long`
**Called By:**
- PlaceDetectionUseCases.detectNewPlaces()
- PlaceReviewUseCases.createManualPlace()

**Database:** `INSERT INTO places VALUES (...)`

**Side Effects:**
- Triggers geofence creation (if preferences.autoCreateGeofences)
- Dispatches PlaceEvent.Created

#### `getAllPlaces(): Flow<List<Place>>`
**Called By:**
- MapViewModel.loadPlaces()
- DashboardViewModel.loadRecentPlaces()

**Database:** `SELECT * FROM places ORDER BY visitCount DESC`

#### `updatePlace(place: Place): Int`
**Called By:**
- PlaceReviewUseCases.editAndApprovePlace()
- PlaceDetectionUseCases.improveExistingPlaces()

**Database:** `UPDATE places SET ... WHERE id = ?`

---

### VisitRepository

**Interface:** `domain/repository/VisitRepository.kt`
**Implementation:** `data/repository/VisitRepositoryImpl.kt`

**Functions:**

#### `createVisit(visit: Visit): Long`
**Called By:**
- GeofenceTransitionService (on place entry)
- SmartDataProcessor.detectPlaceEntry()

**Database:** `INSERT INTO visits VALUES (...)`

#### `updateVisit(visit: Visit): Int`
**Called By:**
- GeofenceTransitionService (on place exit)
- SmartDataProcessor.detectPlaceExit()

**Database:** `UPDATE visits SET exitTime = ?, duration = ? WHERE id = ?`

#### `getVisitsForDate(date: LocalDate): Flow<List<Visit>>`
**Called By:**
- AnalyticsUseCases.generateDayAnalytics()
- TimelineViewModel.loadTimelineSegments()

**Database:**
```sql
SELECT * FROM visits
WHERE date(entryTime/1000, 'unixepoch', 'localtime') = ?
ORDER BY entryTime ASC
```

---

## Data Flow Diagrams

### Location Tracking Flow

```
[User enables tracking]
      ↓
[MainActivity.toggleLocationTracking()]
      ↓
[DashboardViewModel.toggleLocationTracking()]
      ↓
[LocationUseCases.startLocationTracking()]
      ↓
[LocationServiceManager.startService()]
      ↓
[LocationTrackingService.onCreate()]
      ↓
[FusedLocationProviderClient.requestLocationUpdates()]
      ↓
[Every 30-60 seconds: LocationCallback.onLocationResult()]
      ↓
[LocationTrackingService.shouldSaveLocation()] ← Smart filtering
      ↓ [YES]
[SmartDataProcessor.processNewLocation()]
      ↓
[LocationRepositoryImpl.insertLocation()]
      ↓
[LocationDao.insert()]
      ↓
[SQLCipher encrypted database]
      ↓
[Flow emission to all observers]
      ↓
[DashboardViewModel updates location count]
```

### Place Detection Flow

```
[Trigger: Manual button or WorkManager (every 6 hours)]
      ↓
[PlaceDetectionWorker.doWork()]
      ↓
[PlaceDetectionUseCases.detectNewPlaces()]
      ↓
[LocationRepository.getRecentLocations(5000)]
      ↓
[filterLocationsByQuality()] → Removes 40-60% of locations
      ↓
[LocationUtils.clusterLocationsWithPreferences()]
      ├─ DBSCAN algorithm (eps=50m, minPts=3)
      └─ Returns List<Cluster>
      ↓
[For each cluster:]
      ├─ createPlaceFromCluster()
      │   ├─ Calculate center point
      │   ├─ Calculate radius (95th percentile distance)
      │   └─ Calculate confidence (point density)
      ├─ EnrichPlaceWithDetailsUseCase()
      │   ├─ Try Nominatim geocoding
      │   ├─ Fallback to Android Geocoder
      │   └─ Cache result (30-day TTL)
      ├─ AutoAcceptDecisionUseCase.shouldAutoAccept()
      │   ├─ Evaluate confidence
      │   ├─ Evaluate visit count
      │   └─ Return: AUTO_ACCEPT | NEEDS_REVIEW | REJECT
      ├─ If AUTO_ACCEPT or NEEDS_REVIEW:
      │   ├─ PlaceRepository.insertPlace()
      │   ├─ createInitialVisits()
      │   └─ createGeofenceForPlace()
      └─ PlaceReviewUseCases.createPlaceReview()
      ↓
[Return List<Place>]
      ↓
[PlaceDetectionWorker returns SUCCESS]
```

### Visit Tracking Flow

```
[User physically enters geofenced area]
      ↓
[Android OS GeofencingClient detects GEOFENCE_TRANSITION_ENTER]
      ↓
[GeofenceReceiver.onReceive()]
      ↓
[Enqueue GeofenceTransitionWorker]
      ↓
[GeofenceTransitionWorker.doWork()]
      ├─ Extract geofence ID
      ├─ Look up Place from database
      └─ Create Visit
      ↓
[VisitRepository.createVisit()]
      ├─ INSERT INTO visits (placeId, entryTime, exitTime=NULL)
      └─ Return visitId
      ↓
[AppStateManager.updateCurrentPlace(place, visit)]
      ├─ Update in-memory state
      ├─ Persist to CurrentStateRepository
      └─ Emit StateFlow update
      ↓
[StateEventDispatcher.dispatchPlaceEvent(PlaceEvent.Enter)]
      ↓
      ├─ DashboardViewModel observes → Updates UI
      ├─ StateSynchronizer observes → Syncs state
      └─ NotificationHelper observes → Shows notification

[User physically exits geofenced area]
      ↓
[Android OS GeofencingClient detects GEOFENCE_TRANSITION_EXIT]
      ↓
[GeofenceReceiver.onReceive()]
      ↓
[Enqueue GeofenceTransitionWorker]
      ↓
[GeofenceTransitionWorker.doWork()]
      ├─ Look up active Visit for this Place
      ├─ Calculate duration (exitTime - entryTime)
      └─ Update Visit
      ↓
[VisitRepository.updateVisit()]
      ├─ UPDATE visits SET exitTime=?, duration=? WHERE id=?
      └─ Return update count
      ↓
[PlaceRepository.updateStatistics()]
      ├─ INCREMENT visitCount
      ├─ ADD duration to totalTimeSpent
      └─ UPDATE lastVisitTime
      ↓
[AppStateManager.updateCurrentPlace(null)]
      ├─ Clear currentPlace
      └─ Clear currentVisit
      ↓
[StateEventDispatcher.dispatchPlaceEvent(PlaceEvent.Exit)]
      ↓
      ├─ TimelineViewModel observes → Adds to timeline
      └─ NotificationHelper observes → Shows "Visited [Place] for [Duration]"
```

---

## Function Call Chains

### Startup Sequence

```
VoyagerApplication.onCreate()
  ├─ Hilt initializes dependency graph
  ├─ runDataMigrations()
  │   └─ DataMigrationHelper.migrateAll()
  ├─ initializeCurrentState()
  │   └─ CurrentStateRepository.ensureCurrentStateExists()
  ├─ initializeStateSynchronizer()
  │   ├─ StateSynchronizer.initialize()
  │   └─ Register event listeners
  ├─ initializeDataFlowOrchestrator()
  │   └─ DataFlowOrchestrator.start()
  └─ WorkerManagementUseCases.initializeBackgroundWorkers()
      ├─ Enqueue PlaceDetectionWorker (periodic, 6 hours)
      ├─ Enqueue DailySummaryWorker (daily, 8 AM)
      └─ Enqueue DailyReviewSummaryWorker (daily, 6 PM)

MainActivity.onCreate()
  ├─ setContent { VoyagerApp() }
  └─ Request permissions if needed

VoyagerApp() [Composable]
  ├─ NavHost setup
  ├─ Bottom navigation bar
  └─ Navigate to DashboardScreen (default)

DashboardScreen()
  ├─ DashboardViewModel.loadDashboardData()
  │   ├─ AppStateManager.getCurrentState()
  │   ├─ AnalyticsUseCases.generateDayAnalytics(today)
  │   ├─ LocationRepository.getLocationCount()
  │   ├─ PlaceRepository.getPlaceCount()
  │   └─ PlaceReviewUseCases.getPendingReviewCount()
  └─ Render UI based on uiState
```

### User Starts Tracking

```
User taps "Start Tracking" toggle
  ↓
DashboardScreen.TrackingToggle.onClick()
  ↓
DashboardViewModel.toggleLocationTracking()
  ↓
LocationUseCases.startLocationTracking()
  ├─ PermissionManager.hasLocationPermission()
  │   └─ Returns true/false
  ├─ If false: Return Error("Missing permissions")
  └─ If true:
      └─ LocationServiceManager.startService()
          ├─ Create Intent(LocationTrackingService)
          ├─ ContextCompat.startForegroundService()
          └─ LocationTrackingService.onCreate()
              ├─ Build foreground notification
              ├─ startForeground(NOTIFICATION_ID, notification)
              ├─ FusedLocationProviderClient.requestLocationUpdates()
              ├─ ActivityRecognitionClient.requestUpdates() [Phase 2]
              └─ AppStateManager.updateTrackingStatus(true)
                  └─ StateEventDispatcher.dispatchTrackingEvent(STARTED)
                      └─ DashboardViewModel observes → UI shows "Tracking..."
```

### Place Review Flow

```
User navigates to Review tab
  ↓
PlaceReviewScreen()
  ↓
PlaceReviewViewModel.loadPendingReviews()
  ↓
PlaceReviewUseCases.getPendingReviews()
  ├─ PlaceReviewRepository.getPendingReviews()
  │   └─ SELECT * FROM place_reviews WHERE status='PENDING' ORDER BY priority DESC
  └─ Return Flow<List<PlaceReview>>
  ↓
PlaceReviewScreen displays list

User taps "Approve" on a place
  ↓
PlaceReviewViewModel.approvePlace(reviewId)
  ↓
PlaceReviewUseCases.approvePlace(reviewId)
  ├─ Get PlaceReview by ID
  ├─ Get associated Place
  ├─ CategoryLearningEngine.learnFromAcceptance(place)
  │   ├─ Get CategoryPreference for detected category
  │   ├─ Increase confidence by 0.1
  │   └─ Update in database
  ├─ Update review status to APPROVED
  └─ Return Success
  ↓
PlaceReviewViewModel updates UI (removes from list)
```

---

## Summary

This API reference documents the major use cases, repositories, and their wiring throughout the Voyager application. Key takeaways:

1. **Clean separation:** Domain layer (use cases) never directly accesses data layer (DAOs)
2. **Repository pattern:** All data access goes through repository interfaces
3. **Event-driven:** StateEventDispatcher enables loose coupling between components
4. **Flow-based:** Reactive UI updates using Kotlin Flow
5. **Single responsibility:** Each use case has one clear purpose

For more details on specific implementations, see the source code files referenced in this document.
