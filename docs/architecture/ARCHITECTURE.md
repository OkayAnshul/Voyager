# Voyager Architecture Guide

## 🏗️ Clean Architecture Principles

Voyager follows Uncle Bob's Clean Architecture principles, organizing code into distinct layers with clear boundaries and dependencies.

### Layer Structure

```
┌─────────────────────────────────────────────────────────────┐
│                 🎨 Presentation Layer                       │
│                    (app/presentation/)                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Compose UI     │  │   ViewModels    │  │ Navigation   │ │
│  │  - Screens      │  │  - UI State     │  │ - Routing    │ │
│  │  - Components   │  │  - User Actions │  │ - Arguments  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │ (Uses)
┌─────────────────────────▼───────────────────────────────────┐
│                 🧠 Domain Layer                             │
│                   (app/domain/)                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Use Cases     │  │     Models      │  │ Repositories │ │
│  │  - Business     │  │   - Entities    │  │ - Interfaces │ │
│  │    Logic        │  │   - Value Objs  │  │ - Contracts  │ │
│  │  - Validation   │  │   - Enums       │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │ (Implemented by)
┌─────────────────────────▼───────────────────────────────────┐
│                 💾 Data Layer                               │
│                    (app/data/)                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Repositories   │  │     Database    │  │   Services   │ │
│  │  - Impl Classes │  │   - Room Entities│  │ - Location   │ │
│  │  - Data Sources │  │   - DAOs        │  │ - Geofence   │ │
│  │  - Mappers      │  │   - Migrations  │  │ - Workers    │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Dependency Rule

**The Dependency Rule**: Source code dependencies must point inward toward higher-level policies.

- **Presentation** depends on **Domain** (but not Data)
- **Data** depends on **Domain** (implements interfaces)
- **Domain** depends on nothing (pure business logic)

## 🎨 MVVM Pattern Implementation

### What is MVVM?

Model-View-ViewModel (MVVM) is an architectural pattern that separates business logic from UI logic, making applications more testable and maintainable.

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│    View     │───▶│ ViewModel   │───▶│    Model    │
│ (Compose UI)│    │(State Mgmt) │    │(Repository) │
│             │◀───│             │◀───│             │
└─────────────┘    └─────────────┘    └─────────────┘
```

### Components Breakdown

#### 1. **View (Compose UI)**
```kotlin
// Example: DashboardScreen.kt
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }
    
    Column {
        when (uiState) {
            is DashboardUiState.Loading -> LoadingIndicator()
            is DashboardUiState.Success -> {
                LocationTrackingCard(
                    isTracking = uiState.isTracking,
                    onToggleTracking = viewModel::toggleTracking
                )
                StatsCard(stats = uiState.stats)
            }
            is DashboardUiState.Error -> ErrorMessage(uiState.message)
        }
    }
}
```

#### 2. **ViewModel (State Management)**
```kotlin
// Example: DashboardViewModel.kt
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val locationUseCases: LocationUseCases,
    private val analyticsUseCases: AnalyticsUseCases
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    fun refreshData() {
        viewModelScope.launch {
            try {
                _uiState.value = DashboardUiState.Loading
                
                val isTracking = locationUseCases.isTrackingActive()
                val stats = analyticsUseCases.getTodayStats()
                
                _uiState.value = DashboardUiState.Success(
                    isTracking = isTracking,
                    stats = stats
                )
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun toggleTracking() {
        viewModelScope.launch {
            locationUseCases.toggleLocationTracking()
            refreshData()
        }
    }
}
```

#### 3. **Model (Repository & Use Cases)**
```kotlin
// Example: LocationUseCases.kt
@Singleton
class LocationUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun isTrackingActive(): Boolean {
        return locationRepository.isTrackingActive()
    }
    
    suspend fun toggleLocationTracking() {
        if (isTrackingActive()) {
            locationRepository.stopTracking()
        } else {
            locationRepository.startTracking()
        }
    }
}
```

### MVVM Benefits in Voyager

#### 1. **Reactive UI Updates**
```kotlin
// Automatic UI updates when data changes
locationRepository.getCurrentState().collect { state ->
    _uiState.value = _uiState.value.copy(
        currentPlace = state.currentPlace,
        isAtPlace = state.isAtPlace
    )
}
```

#### 2. **Configuration Change Survival**
```kotlin
// ViewModels survive screen rotations
class MapViewModel : ViewModel() {
    // This state persists through rotation
    private val _mapState = MutableStateFlow(MapState.default)
    val mapState = _mapState.asStateFlow()
}
```

#### 3. **Testable Business Logic**
```kotlin
// Easy to unit test ViewModels
@Test
fun `when toggle tracking called, should start tracking if stopped`() = runTest {
    // Given
    every { locationUseCases.isTrackingActive() } returns false
    
    // When
    viewModel.toggleTracking()
    
    // Then
    verify { locationUseCases.startTracking() }
}
```

## 🏛️ Repository Pattern

### Purpose
The Repository pattern provides a uniform interface to access data from multiple sources (database, network, cache).

### Implementation Structure

```kotlin
// Domain Layer - Interface
interface LocationRepository {
    suspend fun insertLocation(location: Location): Long
    fun getRecentLocations(limit: Int): Flow<List<Location>>
    suspend fun getLocationCount(): Int
    suspend fun startTracking()
    suspend fun stopTracking()
}

// Data Layer - Implementation
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val locationService: LocationTrackingService,
    private val mapper: LocationMapper
) : LocationRepository {
    
    override suspend fun insertLocation(location: Location): Long {
        return locationDao.insertLocation(location.toEntity())
    }
    
    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return locationDao.getRecentLocations(limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    // ... other implementations
}
```

### Benefits

#### 1. **Single Source of Truth**
```kotlin
// All location data access goes through the repository
class PlaceDetectionUseCases @Inject constructor(
    private val locationRepository: LocationRepository // Not DAO directly
) {
    suspend fun detectPlaces() {
        val locations = locationRepository.getRecentLocations(1000).first()
        // Process locations...
    }
}
```

#### 2. **Easy Testing with Mocks**
```kotlin
// Can easily mock repository for testing
@Test
fun `detect places with sufficient locations`() = runTest {
    // Given
    val mockLocations = listOf(/* test data */)
    every { locationRepository.getRecentLocations(any()) } returns flowOf(mockLocations)
    
    // When
    val places = placeDetectionUseCases.detectNewPlaces()
    
    // Then
    assertThat(places).hasSize(2)
}
```

#### 3. **Data Source Flexibility**
```kotlin
// Can switch between local and remote data
class LocationRepositoryImpl : LocationRepository {
    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return if (isOnline()) {
            // Sync with server
            remoteDataSource.getLocations().also { 
                localDataSource.cache(it) 
            }
        } else {
            // Use local cache
            localDataSource.getLocations()
        }
    }
}
```

## 🎯 Use Cases (Interactors)

Use Cases contain the business logic of the application and orchestrate data flow between repositories.

### Structure

```kotlin
@Singleton
class PlaceDetectionUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val preferencesRepository: PreferencesRepository
) {
    
    suspend fun detectNewPlaces(): List<Place> {
        // 1. Get user preferences
        val preferences = preferencesRepository.getCurrentPreferences()
        
        // 2. Validate if detection is enabled
        if (!preferences.enablePlaceDetection) {
            return emptyList()
        }
        
        // 3. Get recent locations
        val locations = locationRepository
            .getRecentLocations(preferences.maxLocationsToProcess)
            .first()
        
        // 4. Apply business logic
        return detectPlacesFromLocations(locations, preferences)
    }
    
    private suspend fun detectPlacesFromLocations(
        locations: List<Location>,
        preferences: UserPreferences
    ): List<Place> {
        // Complex business logic here...
    }
}
```

### Benefits

#### 1. **Reusable Business Logic**
```kotlin
// Use cases can be used by multiple ViewModels
class DashboardViewModel @Inject constructor(
    private val placeDetectionUseCases: PlaceDetectionUseCases
)

class SettingsViewModel @Inject constructor(
    private val placeDetectionUseCases: PlaceDetectionUseCases
)
```

#### 2. **Clear Business Rules**
```kotlin
suspend fun shouldTriggerPlaceDetection(): Boolean {
    val locationCount = locationRepository.getLocationCount()
    val preferences = preferencesRepository.getCurrentPreferences()
    
    return locationCount >= preferences.autoDetectTriggerCount &&
           preferences.enablePlaceDetection &&
           timeSinceLastDetection() > preferences.detectionInterval
}
```

## 🔗 Dependency Flow Example

Here's how a typical feature flows through all layers:

### Example: Get Today's Visit Statistics

```kotlin
// 1. Presentation Layer - ViewModel requests data
class DashboardViewModel @Inject constructor(
    private val analyticsUseCases: AnalyticsUseCases
) {
    fun loadTodayStats() {
        viewModelScope.launch {
            val stats = analyticsUseCases.getTodayStats()
            _uiState.value = DashboardUiState.Success(stats)
        }
    }
}

// 2. Domain Layer - Use Case orchestrates business logic
class AnalyticsUseCases @Inject constructor(
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository
) {
    suspend fun getTodayStats(): TodayStats {
        val today = LocalDate.now()
        val visits = visitRepository.getVisitsForDate(today)
        val places = visits.map { it.placeId }.distinct()
            .let { placeRepository.getPlacesByIds(it) }
        
        return TodayStats(
            totalTimeTracked = visits.sumOf { it.duration },
            placesVisited = places.size,
            visitCount = visits.size
        )
    }
}

// 3. Data Layer - Repository provides data access
class VisitRepositoryImpl @Inject constructor(
    private val visitDao: VisitDao
) : VisitRepository {
    override suspend fun getVisitsForDate(date: LocalDate): List<Visit> {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay()
        
        return visitDao.getVisitsBetween(startOfDay, endOfDay)
            .map { it.toDomainModel() }
    }
}
```

## 🎨 Architecture Benefits

### 1. **Testability**
- Each layer can be tested independently
- Easy to mock dependencies
- Business logic isolated from Android framework

### 2. **Maintainability**
- Clear separation of concerns
- Changes in one layer don't affect others
- Easy to locate and fix bugs

### 3. **Scalability**
- Easy to add new features
- Can change UI framework without affecting business logic
- Can switch databases without changing use cases

### 4. **Team Collaboration**
- Different teams can work on different layers
- Clear contracts between layers
- Reduced merge conflicts

## 🚀 Best Practices

### 1. **Dependency Direction**
```kotlin
// ✅ Good - Domain interface, Data implementation
interface LocationRepository // Domain
class LocationRepositoryImpl : LocationRepository // Data

// ❌ Bad - Direct dependency on data layer
class PlaceDetectionUseCases(private val locationDao: LocationDao)
```

### 2. **Error Handling**
```kotlin
// ✅ Good - Domain errors
sealed class VoyagerError : Exception() {
    object NoLocationPermission : VoyagerError()
    object InsufficientData : VoyagerError()
    data class DatabaseError(override val message: String) : VoyagerError()
}

// Use case throws domain errors
suspend fun detectPlaces(): Result<List<Place>> {
    return try {
        val places = performDetection()
        Result.success(places)
    } catch (e: Exception) {
        Result.failure(VoyagerError.InsufficientData)
    }
}
```

### 3. **Data Flow**
```kotlin
// ✅ Good - Reactive data flow
fun getCurrentLocation(): Flow<Location?> {
    return locationDao.getCurrentLocation()
        .map { it?.toDomainModel() }
}

// ❌ Bad - Blocking calls in UI thread
suspend fun getCurrentLocationBlocking(): Location? {
    return runBlocking { locationDao.getCurrentLocation() }
}
```

---

*Next: [Domain Models & Entities](./DOMAIN_MODELS.md)*