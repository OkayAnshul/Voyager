# VOYAGER - ARCHITECTURE GUIDE

**Last Updated**: 2025-11-12
**Audience**: Developers (Human & AI)
**Purpose**: Technical reference for understanding and extending the codebase

---

## TABLE OF CONTENTS
1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Data Flow](#data-flow)
4. [Key Design Patterns](#key-design-patterns)
5. [Dependency Injection](#dependency-injection)
6. [Database Schema](#database-schema)
7. [Background Processing](#background-processing)
8. [State Management](#state-management)
9. [How to Add Features](#how-to-add-features)
10. [Testing Strategy](#testing-strategy)

---

## ARCHITECTURE OVERVIEW

Voyager follows **Clean Architecture** principles with **MVVM** pattern for presentation layer.

### Layer Separation

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  (Jetpack Compose UI + ViewModels)                          │
│  - Screens: Dashboard, Map, Timeline, Insights, Settings    │
│  - ViewModels: State management, UI logic                   │
│  - Navigation: Compose Navigation                           │
└───────────────────────────┬─────────────────────────────────┘
                            │ calls
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  (Pure Kotlin - Business Logic)                             │
│  - Models: Place, Location, Visit, UserPreferences          │
│  - Use Cases: PlaceDetectionUseCases, AnalyticsUseCases     │
│  - Repository Interfaces: PlaceRepository, LocationRepo     │
│  - Validation: DataValidationService                        │
└───────────────────────────┬─────────────────────────────────┘
                            │ implements
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  (Android Framework Dependencies)                            │
│  - Repository Implementations                                │
│  - Database: Room + SQLCipher                                │
│  - Services: LocationTrackingService                         │
│  - Workers: PlaceDetectionWorker, GeofenceTransitionWorker  │
│  - APIs: Geocoding services (to be added)                   │
└─────────────────────────────────────────────────────────────┘
```

### Benefits of This Architecture

✅ **Testability**: Each layer can be tested independently
✅ **Maintainability**: Changes isolated to specific layers
✅ **Scalability**: Easy to add new features without affecting existing code
✅ **Separation of Concerns**: UI, business logic, and data access clearly separated

---

## PROJECT STRUCTURE

```
app/src/main/java/com/cosmiclaboratory/voyager/
│
├── presentation/                    # UI Layer (Jetpack Compose)
│   ├── screen/
│   │   ├── dashboard/              # Main overview screen
│   │   │   ├── DashboardScreen.kt
│   │   │   └── DashboardViewModel.kt
│   │   ├── map/                    # OpenStreetMap visualization
│   │   │   ├── MapScreen.kt
│   │   │   ├── MapViewModel.kt
│   │   │   └── OpenStreetMapView.kt
│   │   ├── timeline/               # Visit history by date
│   │   │   ├── TimelineScreen.kt
│   │   │   └── TimelineViewModel.kt
│   │   ├── insights/               # Analytics & statistics
│   │   │   ├── InsightsScreen.kt
│   │   │   └── InsightsViewModel.kt
│   │   ├── settings/               # App configuration
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── permission/             # Permission handling
│   │       └── PermissionScreen.kt
│   ├── navigation/                 # Navigation logic
│   │   └── VoyagerNavHost.kt
│   ├── components/                 # Reusable UI components
│   │   ├── PlaceCard.kt
│   │   ├── LocationMarker.kt
│   │   └── StatisticCard.kt
│   └── theme/                      # Material Design 3 theming
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── domain/                          # Business Logic Layer (Pure Kotlin)
│   ├── model/                      # Domain entities
│   │   ├── Place.kt                # Place entity
│   │   ├── Location.kt             # GPS coordinate
│   │   ├── Visit.kt                # Time tracking entity
│   │   ├── UserPreferences.kt      # User configuration
│   │   ├── PlaceCategory.kt        # Place types (Home, Work, etc.)
│   │   └── AppState.kt             # Application state
│   ├── repository/                 # Repository interfaces
│   │   ├── PlaceRepository.kt
│   │   ├── LocationRepository.kt
│   │   ├── VisitRepository.kt
│   │   ├── PreferencesRepository.kt
│   │   └── AnalyticsRepository.kt
│   ├── usecase/                    # Business logic use cases
│   │   ├── PlaceDetectionUseCases.kt    # DBSCAN clustering + categorization
│   │   ├── AnalyticsUseCases.kt         # Statistics calculation
│   │   ├── LocationTrackingUseCases.kt  # GPS management
│   │   └── GeofenceUseCases.kt          # Geofence setup/monitoring
│   ├── validation/                 # Data validation
│   │   └── DataValidationService.kt
│   └── exception/                  # Custom exceptions
│       └── VoyagerException.kt
│
├── data/                            # Data Layer (Android Dependencies)
│   ├── database/                   # Room + SQLCipher
│   │   ├── VoyagerDatabase.kt      # Database definition
│   │   ├── entity/                 # Database entities
│   │   │   ├── LocationEntity.kt
│   │   │   ├── PlaceEntity.kt
│   │   │   ├── VisitEntity.kt
│   │   │   ├── GeofenceEntity.kt
│   │   │   └── CurrentStateEntity.kt
│   │   ├── dao/                    # Data Access Objects
│   │   │   ├── LocationDao.kt
│   │   │   ├── PlaceDao.kt
│   │   │   ├── VisitDao.kt
│   │   │   ├── GeofenceDao.kt
│   │   │   └── CurrentStateDao.kt
│   │   ├── converter/              # Type converters
│   │   │   └── InstantConverter.kt
│   │   └── migration/              # Database migrations
│   │       └── Migrations.kt
│   ├── repository/                 # Repository implementations
│   │   ├── PlaceRepositoryImpl.kt
│   │   ├── LocationRepositoryImpl.kt
│   │   ├── VisitRepositoryImpl.kt
│   │   ├── PreferencesRepositoryImpl.kt
│   │   └── AnalyticsRepositoryImpl.kt
│   ├── service/                    # Android services
│   │   └── LocationTrackingService.kt   # Foreground GPS service
│   ├── worker/                     # WorkManager background tasks
│   │   ├── PlaceDetectionWorker.kt
│   │   ├── GeofenceTransitionWorker.kt
│   │   └── FallbackPlaceDetectionWorker.kt
│   ├── receiver/                   # Broadcast receivers
│   │   ├── GeofenceReceiver.kt
│   │   └── BootReceiver.kt
│   ├── processor/                  # Smart data processing
│   │   └── SmartDataProcessor.kt
│   ├── event/                      # Event system
│   │   └── EventDispatcher.kt
│   ├── state/                      # State management
│   │   └── AppStateManager.kt
│   ├── mapper/                     # Entity-Domain mappers
│   │   ├── PlaceMapper.kt
│   │   ├── LocationMapper.kt
│   │   └── VisitMapper.kt
│   └── api/                        # Network services (to be added)
│       ├── GeocodingService.kt     # Interface
│       ├── AndroidGeocoderService.kt
│       └── NominatimGeocodingService.kt
│
├── di/                              # Hilt Dependency Injection
│   ├── DatabaseModule.kt           # Room + SQLCipher setup
│   ├── LocationModule.kt           # FusedLocationProviderClient
│   ├── RepositoryModule.kt         # Repository bindings
│   ├── UseCasesModule.kt           # Use case providers
│   ├── StateModule.kt              # AppStateManager + EventDispatcher
│   ├── ValidationModule.kt         # Validation framework
│   ├── OrchestratorModule.kt       # Data flow orchestration
│   └── UtilsModule.kt              # Utility classes
│
├── utils/                           # Utility classes
│   ├── LocationUtils.kt            # DBSCAN, distance calculations
│   ├── SecurityUtils.kt            # Encryption key management
│   ├── PermissionUtils.kt          # Permission checking
│   ├── DateTimeUtils.kt            # Time formatting
│   └── ErrorHandler.kt             # Error handling
│
└── VoyagerApplication.kt           # Application class (Hilt setup)
```

---

## DATA FLOW

### Location Tracking Flow

```
User launches app
    ↓
PermissionScreen checks permissions
    ↓
User grants location permissions
    ↓
DashboardScreen starts LocationTrackingService
    ↓
LocationTrackingService.startLocationUpdates()
    ↓
FusedLocationProviderClient emits location updates
    ↓
LocationCallback receives new location
    ↓
shouldSaveLocation() filters location
    │   - Check accuracy (< 100m default)
    │   - Check movement (> 10m from last)
    │   - Check speed (< 200 km/h)
    │   - Stationary mode check
    ↓
SmartDataProcessor.processNewLocation()
    │   - Additional validation
    │   - Quality checks
    ↓
LocationRepository.insertLocation()
    ↓
LocationDao inserts to encrypted database
    ↓
EventDispatcher emits LocationUpdatedEvent
    ↓
AppStateManager updates state
    ↓
UI observes StateFlow and updates
```

### Place Detection Flow

```
PlaceDetectionWorker scheduled by WorkManager
    ↓
Worker checks constraints (battery, network, etc.)
    ↓
PlaceDetectionUseCases.detectNewPlaces()
    ↓
LocationRepository.getRecentLocations(5000)
    ↓
Filter locations (quality, recency)
    ↓
LocationUtils.clusterLocationsWithPreferences()
    │   (DBSCAN algorithm)
    │   - Group points within 50m
    │   - Require 3+ points per cluster
    ↓
For each cluster:
    │
    ├─► calculateClusterCenter()
    │   (centroid of all points)
    │
    ├─► categorizePlace()
    │   │   - Analyze time patterns
    │   │   - Calculate hour-of-day distribution
    │   │   - Apply ML categorization rules
    │   │   - Return PlaceCategory
    │
    ├─► generatePlaceName()  ⚠️ NEEDS GEOCODING
    │   │   Currently: Returns generic name
    │   │   Future: Call geocoding API
    │
    ├─► Check for duplicates
    │   (nearby existing places)
    │
    └─► PlaceRepository.insertPlace()
        ↓
        PlaceDao inserts to database
        ↓
        GeofenceUseCases.setupGeofence()
        │   (100m radius geofence)
        ↓
        GeofencingClient.addGeofences()
        ↓
        EventDispatcher emits PlaceDetectedEvent
        ↓
        UI updates with new place
```

### Visit Tracking Flow

```
User enters geofence radius
    ↓
GeofenceReceiver.onReceive()
    ↓
GeofenceTransitionWorker scheduled
    ↓
Worker checks transition type
    │
    ├─► GEOFENCE_TRANSITION_ENTER
    │   ↓
    │   VisitRepository.startVisit(placeId)
    │   ↓
    │   Create VisitEntity with entryTime
    │   ↓
    │   EventDispatcher emits VisitStartedEvent
    │   ↓
    │   UI shows "At [Place Name]"
    │
    └─► GEOFENCE_TRANSITION_EXIT
        ↓
        VisitRepository.endVisit(placeId)
        ↓
        Update VisitEntity with exitTime
        ↓
        Calculate duration
        ↓
        Update Place.totalTimeSpent
        ↓
        Update Place.visitCount
        ↓
        EventDispatcher emits VisitEndedEvent
        ↓
        UI shows visit summary
```

### Analytics Calculation Flow

```
DashboardScreen loads
    ↓
DashboardViewModel.loadStats()
    ↓
Check cache (30s TTL)
    │
    ├─► Cache valid → Return cached stats
    │
    └─► Cache expired → Calculate new stats
        ↓
        AnalyticsUseCases.getOverallStats()
        ↓
        Run parallel queries:
        │
        ├─► LocationRepository.getTotalLocationCount()
        ├─► PlaceRepository.getTotalPlaceCount()
        ├─► VisitRepository.getAllVisits()
        └─► AnalyticsRepository.getTimeByCategory()
        ↓
        Aggregate results
        ↓
        Calculate totals, averages, trends
        ↓
        Cache for 30 seconds
        ↓
        Return OverallStats
        ↓
        ViewModel updates StateFlow
        ↓
        UI recomposes with new data
```

---

## KEY DESIGN PATTERNS

### 1. Repository Pattern
**Purpose**: Abstract data sources from business logic

**Example**:
```kotlin
// Domain layer - Interface
interface PlaceRepository {
    suspend fun insertPlace(place: Place): Place
    fun getAllPlaces(): Flow<List<Place>>
    suspend fun getPlaceById(id: Long): Place?
}

// Data layer - Implementation
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val placeMapper: PlaceMapper
) : PlaceRepository {
    override suspend fun insertPlace(place: Place): Place {
        val entity = placeMapper.toEntity(place)
        val id = placeDao.insert(entity)
        return place.copy(id = id)
    }

    override fun getAllPlaces(): Flow<List<Place>> {
        return placeDao.getAllPlaces()
            .map { entities -> entities.map { placeMapper.toDomain(it) } }
    }
}
```

**Benefits**:
- Business logic doesn't depend on database implementation
- Easy to swap data sources (database, network, cache)
- Testable with mock repositories

---

### 2. Use Case Pattern
**Purpose**: Encapsulate single business operations

**Example**:
```kotlin
@Singleton
class DetectNewPlacesUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(): List<Place> {
        val preferences = preferencesRepository.getUserPreferences()
        val locations = locationRepository.getRecentLocations(5000)

        val filteredLocations = filterQualityLocations(locations, preferences)
        val clusters = clusterLocations(filteredLocations, preferences)

        return clusters.map { cluster ->
            createPlaceFromCluster(cluster, preferences)
        }
    }

    private fun filterQualityLocations(...): List<Location> { ... }
    private fun clusterLocations(...): List<Cluster> { ... }
    private fun createPlaceFromCluster(...): Place { ... }
}
```

**Benefits**:
- Single Responsibility Principle
- Reusable business logic
- Easy to test independently
- Clear naming (verb-based)

---

### 3. MVVM Pattern
**Purpose**: Separate UI from business logic

**Example**:
```kotlin
// ViewModel - Presentation Logic
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getOverallStatsUseCase: GetOverallStatsUseCase,
    private val getCurrentVisitUseCase: GetCurrentVisitUseCase
) : ViewModel() {

    // State exposed to UI
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            try {
                val stats = getOverallStatsUseCase()
                val currentVisit = getCurrentVisitUseCase()

                _uiState.value = DashboardUiState.Success(
                    stats = stats,
                    currentVisit = currentVisit
                )
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message)
            }
        }
    }

    // User actions
    fun onStartTrackingClicked() { ... }
    fun onStopTrackingClicked() { ... }
}

// UI State
sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val stats: OverallStats, val currentVisit: Visit?) : DashboardUiState()
    data class Error(val message: String?) : DashboardUiState()
}

// Composable - UI Layer
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is DashboardUiState.Loading -> LoadingIndicator()
        is DashboardUiState.Success -> DashboardContent(state.stats, state.currentVisit)
        is DashboardUiState.Error -> ErrorMessage(state.message)
    }
}
```

**Benefits**:
- UI only renders state, no business logic
- ViewModel survives configuration changes
- Testable without Android framework

---

### 4. Mapper Pattern
**Purpose**: Convert between entity and domain models

**Example**:
```kotlin
@Singleton
class PlaceMapper @Inject constructor() {

    fun toDomain(entity: PlaceEntity): Place {
        return Place(
            id = entity.id,
            name = entity.name,
            category = PlaceCategory.valueOf(entity.category),
            latitude = entity.latitude,
            longitude = entity.longitude,
            address = entity.address,
            visitCount = entity.visitCount,
            totalTimeSpent = entity.totalTimeSpent,
            radius = entity.radius,
            createdAt = entity.createdAt,
            lastVisitedAt = entity.lastVisitedAt
        )
    }

    fun toEntity(domain: Place): PlaceEntity {
        return PlaceEntity(
            id = domain.id,
            name = domain.name,
            category = domain.category.name,
            latitude = domain.latitude,
            longitude = domain.longitude,
            address = domain.address,
            visitCount = domain.visitCount,
            totalTimeSpent = domain.totalTimeSpent,
            radius = domain.radius,
            createdAt = domain.createdAt,
            lastVisitedAt = domain.lastVisitedAt
        )
    }
}
```

**Benefits**:
- Domain layer independent of database
- Easy to change database schema
- Type safety

---

### 5. Dependency Inversion Principle
**Purpose**: Depend on abstractions, not concretions

**Example**:
```kotlin
// ❌ BAD: Direct dependency on concrete class
class PlaceDetectionUseCases(
    private val locationDao: LocationDao  // Concrete implementation
) {
    suspend fun detectPlaces() {
        val locations = locationDao.getRecentLocations()  // Tightly coupled to Room
        // ...
    }
}

// ✅ GOOD: Depend on interface
class PlaceDetectionUseCases @Inject constructor(
    private val locationRepository: LocationRepository  // Interface
) {
    suspend fun detectPlaces() {
        val locations = locationRepository.getRecentLocations()  // Abstracted
        // ...
    }
}
```

**Benefits**:
- Can swap implementations (Room, Firebase, in-memory)
- Testable with mocks
- Loose coupling

---

## DEPENDENCY INJECTION

Voyager uses **Hilt** (built on Dagger) for dependency injection.

### Module Structure

```kotlin
// 1. DatabaseModule - Provides database and DAOs
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVoyagerDatabase(
        @ApplicationContext context: Context
    ): VoyagerDatabase {
        val passphrase = SecurityUtils.getDatabasePassphrase(context)
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))

        return Room.databaseBuilder(
            context,
            VoyagerDatabase::class.java,
            "voyager_db"
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun providePlaceDao(database: VoyagerDatabase): PlaceDao {
        return database.placeDao()
    }

    // ... other DAOs
}

// 2. RepositoryModule - Binds repository implementations
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(
        impl: PlaceRepositoryImpl
    ): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository

    // ... other repositories
}

// 3. UseCasesModule - Provides use cases
@Module
@InstallIn(SingletonComponent::class)
object UseCasesModule {

    @Provides
    @Singleton
    fun providePlaceDetectionUseCases(
        locationRepository: LocationRepository,
        placeRepository: PlaceRepository,
        preferencesRepository: PreferencesRepository
    ): PlaceDetectionUseCases {
        return PlaceDetectionUseCases(
            locationRepository,
            placeRepository,
            preferencesRepository
        )
    }

    // ... other use cases
}
```

### Scopes

- **@Singleton**: One instance for app lifetime (repositories, database, use cases)
- **@ViewModelScoped**: One instance per ViewModel lifecycle
- **@ActivityRetainedScoped**: Survives configuration changes
- **Unscoped**: New instance every injection

### Injection Points

```kotlin
// 1. ViewModels - @HiltViewModel
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getOverallStatsUseCase: GetOverallStatsUseCase
) : ViewModel()

// 2. Composables - hiltViewModel()
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) { ... }

// 3. Workers - @HiltWorker
@HiltWorker
class PlaceDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDetectionUseCases: PlaceDetectionUseCases
) : CoroutineWorker(context, params)

// 4. Services - @AndroidEntryPoint
@AndroidEntryPoint
class LocationTrackingService : Service() {
    @Inject lateinit var locationClient: FusedLocationProviderClient
    @Inject lateinit var locationRepository: LocationRepository
}
```

---

## DATABASE SCHEMA

### Current Schema (Version 3)

```sql
-- Locations Table
CREATE TABLE locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    accuracy REAL NOT NULL,
    altitude REAL,
    speed REAL,
    bearing REAL,
    timestamp INTEGER NOT NULL,  -- Instant (epoch milliseconds)
    provider TEXT NOT NULL
);

-- Places Table
CREATE TABLE places (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    category TEXT NOT NULL,        -- Enum as string
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    address TEXT,                   -- ⚠️ Currently always null
    visitCount INTEGER NOT NULL,
    totalTimeSpent INTEGER NOT NULL, -- Milliseconds
    radius REAL NOT NULL,
    createdAt INTEGER NOT NULL,      -- Instant
    lastVisitedAt INTEGER            -- Instant, nullable
);

-- Visits Table
CREATE TABLE visits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    placeId INTEGER NOT NULL,
    entryTime INTEGER NOT NULL,      -- Instant
    exitTime INTEGER,                 -- Instant, nullable (active visit)
    FOREIGN KEY (placeId) REFERENCES places(id) ON DELETE CASCADE
);

-- Geofences Table
CREATE TABLE geofences (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    placeId INTEGER NOT NULL,
    geofenceId TEXT NOT NULL UNIQUE,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radius REAL NOT NULL,
    isActive INTEGER NOT NULL,        -- Boolean as int
    createdAt INTEGER NOT NULL,
    FOREIGN KEY (placeId) REFERENCES places(id) ON DELETE CASCADE
);

-- Current State Table (Singleton)
CREATE TABLE current_state (
    id INTEGER PRIMARY KEY CHECK (id = 1), -- Only one row
    isTrackingLocation INTEGER NOT NULL,
    currentPlaceId INTEGER,
    currentVisitId INTEGER,
    lastLocationTimestamp INTEGER,
    FOREIGN KEY (currentPlaceId) REFERENCES places(id),
    FOREIGN KEY (currentVisitId) REFERENCES visits(id)
);
```

### Planned Schema (Version 4) - After Geocoding Implementation

```sql
-- Geocoding Cache Table (NEW)
CREATE TABLE geocoding_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    formattedAddress TEXT NOT NULL,
    streetName TEXT,
    locality TEXT,                     -- City/town
    subLocality TEXT,                  -- Neighborhood/area
    postalCode TEXT,
    countryCode TEXT,
    cachedAt INTEGER NOT NULL,         -- Instant
    UNIQUE (latitude, longitude)
);

-- Places Table (UPDATED)
CREATE TABLE places (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,

    -- NEW: Geocoding columns
    address TEXT,
    streetName TEXT,
    locality TEXT,
    subLocality TEXT,
    postalCode TEXT,
    countryCode TEXT,
    isUserRenamed INTEGER NOT NULL DEFAULT 0,  -- Track manual edits

    visitCount INTEGER NOT NULL,
    totalTimeSpent INTEGER NOT NULL,
    radius REAL NOT NULL,
    createdAt INTEGER NOT NULL,
    lastVisitedAt INTEGER
);
```

### Indexes (Planned)

```sql
-- Performance indexes
CREATE INDEX idx_locations_timestamp ON locations(timestamp);
CREATE INDEX idx_locations_lat_lng ON locations(latitude, longitude);
CREATE INDEX idx_places_lat_lng ON places(latitude, longitude);
CREATE INDEX idx_places_category ON places(category);
CREATE INDEX idx_visits_entry_time ON visits(entryTime);
CREATE INDEX idx_visits_exit_time ON visits(exitTime);
CREATE INDEX idx_geocoding_cache_lat_lng ON geocoding_cache(latitude, longitude);
```

---

## BACKGROUND PROCESSING

### WorkManager Architecture

```
WorkManager
    ├── PlaceDetectionWorker (Periodic)
    │   ├── Frequency: Every 6 hours (configurable 1-24h)
    │   ├── Constraints:
    │   │   ├── Battery: ANY (was NOT_LOW, fixed)
    │   │   ├── Network: Not required
    │   │   └── Device Idle: Not required
    │   ├── Input: Last detection timestamp
    │   └── Output: List of new places detected
    │
    ├── GeofenceTransitionWorker (On-Demand)
    │   ├── Trigger: Geofence ENTER/EXIT event
    │   ├── Constraints: None (time-sensitive)
    │   ├── Input: Geofence ID, transition type
    │   └── Output: Visit started/ended
    │
    └── FallbackPlaceDetectionWorker (Periodic)
        ├── Frequency: Every 24 hours
        ├── Purpose: Backup if primary worker fails
        └── Constraints: Same as PlaceDetectionWorker
```

### Worker Implementation Pattern

```kotlin
@HiltWorker
class PlaceDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDetectionUseCases: PlaceDetectionUseCases,
    private val appStateManager: AppStateManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Check if enough new locations
            if (!shouldRunDetection()) {
                return Result.success()
            }

            // Run detection
            val newPlaces = placeDetectionUseCases.detectNewPlaces()

            // Update state
            appStateManager.updatePlaceDetectionState(
                lastDetectionTime = Instant.now(),
                placesDetected = newPlaces.size
            )

            // Return success with output data
            Result.success(
                workDataOf("places_detected" to newPlaces.size)
            )
        } catch (e: Exception) {
            Log.e("PlaceDetectionWorker", "Detection failed", e)

            // Retry with exponential backoff
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun shouldRunDetection(): Boolean { ... }
}
```

### Scheduling Workers

```kotlin
// In Application.onCreate() or on-demand
fun schedulePlaceDetection(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(false)  // Changed from true
        .build()

    val request = PeriodicWorkRequestBuilder<PlaceDetectionWorker>(
        repeatInterval = 6,
        repeatIntervalTimeUnit = TimeUnit.HOURS
    )
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            15,
            TimeUnit.MINUTES
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "place_detection",
        ExistingPeriodicWorkPolicy.KEEP,  // Don't restart if already scheduled
        request
    )
}
```

---

## STATE MANAGEMENT

### AppStateManager

Centralized state management using Kotlin Flow.

```kotlin
@Singleton
class AppStateManager @Inject constructor(
    private val currentStateDao: CurrentStateDao,
    private val eventDispatcher: EventDispatcher
) {
    // Reactive state flows
    private val _appState = MutableStateFlow<AppState>(AppState.Initial)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _trackingState = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _trackingState.asStateFlow()

    private val _currentVisit = MutableStateFlow<Visit?>(null)
    val currentVisit: StateFlow<Visit?> = _currentVisit.asStateFlow()

    // State mutations
    suspend fun startTracking() {
        _trackingState.value = true
        updateDatabaseState { it.copy(isTrackingLocation = true) }
        eventDispatcher.dispatch(TrackingStartedEvent)
    }

    suspend fun stopTracking() {
        _trackingState.value = false
        updateDatabaseState { it.copy(isTrackingLocation = false) }
        eventDispatcher.dispatch(TrackingStoppedEvent)
    }

    suspend fun updateCurrentVisit(visit: Visit?) {
        _currentVisit.value = visit
        updateDatabaseState { it.copy(currentVisitId = visit?.id) }
    }

    private suspend fun updateDatabaseState(update: (CurrentStateEntity) -> CurrentStateEntity) {
        val currentState = currentStateDao.getCurrentState() ?: CurrentStateEntity()
        val newState = update(currentState)
        currentStateDao.updateState(newState)
    }
}
```

### Event System

```kotlin
sealed class VoyagerEvent {
    // Location events
    object TrackingStartedEvent : VoyagerEvent()
    object TrackingStoppedEvent : VoyagerEvent()
    data class LocationUpdatedEvent(val location: Location) : VoyagerEvent()

    // Place events
    data class PlaceDetectedEvent(val place: Place) : VoyagerEvent()
    data class PlaceUpdatedEvent(val place: Place) : VoyagerEvent()
    data class PlaceDeletedEvent(val placeId: Long) : VoyagerEvent()

    // Visit events
    data class VisitStartedEvent(val visit: Visit) : VoyagerEvent()
    data class VisitEndedEvent(val visit: Visit) : VoyagerEvent()

    // Error events
    data class ErrorEvent(val error: Throwable) : VoyagerEvent()
}

@Singleton
class EventDispatcher @Inject constructor() {
    private val _events = MutableSharedFlow<VoyagerEvent>()
    val events: SharedFlow<VoyagerEvent> = _events.asSharedFlow()

    suspend fun dispatch(event: VoyagerEvent) {
        _events.emit(event)
    }
}
```

---

## HOW TO ADD FEATURES

### Example: Adding a New Feature (Geocoding)

#### Step 1: Define Domain Model
```kotlin
// domain/model/Address.kt
data class Address(
    val formattedAddress: String,
    val streetName: String?,
    val locality: String?,
    val postalCode: String?
)
```

#### Step 2: Create Repository Interface
```kotlin
// domain/repository/GeocodingRepository.kt
interface GeocodingRepository {
    suspend fun getAddress(latitude: Double, longitude: Double): Address?
}
```

#### Step 3: Implement Repository
```kotlin
// data/repository/GeocodingRepositoryImpl.kt
@Singleton
class GeocodingRepositoryImpl @Inject constructor(
    private val androidGeocoder: AndroidGeocoderService,
    private val nominatimService: NominatimGeocodingService
) : GeocodingRepository {

    override suspend fun getAddress(latitude: Double, longitude: Double): Address? {
        // Try Android Geocoder first
        androidGeocoder.getAddress(latitude, longitude)?.let { return it }

        // Fallback to Nominatim
        return nominatimService.getAddress(latitude, longitude)
    }
}
```

#### Step 4: Create Use Case
```kotlin
// domain/usecase/GetAddressForPlaceUseCase.kt
@Singleton
class GetAddressForPlaceUseCase @Inject constructor(
    private val geocodingRepository: GeocodingRepository
) {
    suspend operator fun invoke(place: Place): Address? {
        return geocodingRepository.getAddress(place.latitude, place.longitude)
    }
}
```

#### Step 5: Update DI Module
```kotlin
// di/RepositoryModule.kt
@Binds
@Singleton
abstract fun bindGeocodingRepository(
    impl: GeocodingRepositoryImpl
): GeocodingRepository
```

#### Step 6: Use in ViewModel
```kotlin
// presentation/screen/place/PlaceDetailsViewModel.kt
@HiltViewModel
class PlaceDetailsViewModel @Inject constructor(
    private val getAddressForPlaceUseCase: GetAddressForPlaceUseCase
) : ViewModel() {

    fun loadPlaceAddress(place: Place) {
        viewModelScope.launch {
            val address = getAddressForPlaceUseCase(place)
            _uiState.value = _uiState.value.copy(address = address)
        }
    }
}
```

---

## TESTING STRATEGY

### Unit Tests (Domain Layer)

```kotlin
// Test use cases with mock repositories
class PlaceDetectionUseCasesTest {

    @Mock private lateinit var locationRepository: LocationRepository
    @Mock private lateinit var placeRepository: PlaceRepository
    @Mock private lateinit var preferencesRepository: PreferencesRepository

    private lateinit var placeDetectionUseCases: PlaceDetectionUseCases

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        placeDetectionUseCases = PlaceDetectionUseCases(
            locationRepository,
            placeRepository,
            preferencesRepository
        )
    }

    @Test
    fun `detectNewPlaces should return empty list when no locations`() = runTest {
        // Given
        `when`(locationRepository.getRecentLocations(any())).thenReturn(emptyList())

        // When
        val result = placeDetectionUseCases.detectNewPlaces()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectNewPlaces should cluster nearby locations`() = runTest {
        // Given
        val locations = listOf(
            createLocation(lat = 37.7749, lng = -122.4194),
            createLocation(lat = 37.7750, lng = -122.4195),  // 10m away
            createLocation(lat = 37.7751, lng = -122.4196)   // 20m away
        )
        `when`(locationRepository.getRecentLocations(any())).thenReturn(locations)

        // When
        val result = placeDetectionUseCases.detectNewPlaces()

        // Then
        assertEquals(1, result.size)  // Should form one cluster
    }
}
```

### Integration Tests (Data Layer)

```kotlin
// Test database operations
@RunWith(AndroidJUnit4::class)
class PlaceDaoTest {

    private lateinit var database: VoyagerDatabase
    private lateinit var placeDao: PlaceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VoyagerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        placeDao = database.placeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrievePlace() = runTest {
        // Given
        val place = PlaceEntity(
            name = "Test Place",
            category = "HOME",
            latitude = 37.7749,
            longitude = -122.4194,
            radius = 100.0,
            createdAt = Instant.now()
        )

        // When
        val id = placeDao.insert(place)
        val retrieved = placeDao.getPlaceById(id)

        // Then
        assertNotNull(retrieved)
        assertEquals("Test Place", retrieved?.name)
    }
}
```

### UI Tests (Presentation Layer)

```kotlin
// Test Compose UI
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardDisplaysStats() {
        // Given
        val mockStats = OverallStats(
            totalLocations = 100,
            totalPlaces = 5,
            totalTimeTracked = 3600000L  // 1 hour
        )

        // When
        composeTestRule.setContent {
            DashboardScreen(
                stats = mockStats,
                currentVisit = null
            )
        }

        // Then
        composeTestRule.onNodeWithText("100").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 places").assertIsDisplayed()
    }
}
```

---

## CONCLUSION

This architecture guide provides the foundation for understanding and extending Voyager. Key principles:

✅ **Clean Architecture** - Clear separation of concerns
✅ **Dependency Injection** - Hilt for testability
✅ **Reactive State** - Flow/StateFlow for UI updates
✅ **Background Processing** - WorkManager for reliability
✅ **Database Encryption** - SQLCipher for privacy

**For Implementation**: See `IMPLEMENTATION_ROADMAP.md`
**For Current Status**: See `VOYAGER_PROJECT_STATUS.md`
**For Usage Strategy**: See `USAGE_OPTIMIZATION_STRATEGY.md`
