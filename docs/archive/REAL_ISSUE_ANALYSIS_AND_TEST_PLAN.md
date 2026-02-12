# Real Issue Analysis & Comprehensive Test Plan
**Date:** 2025-11-14
**Purpose:** Address actual bugs found during usage testing

---

## Real Issues Reported

### Issue #1: Real-time Data Not Reflected in Screens
**Symptom:** Data doesn't update automatically in UI, time calculations inaccurate
**Affected Screens:** Dashboard, Timeline, Map, Insights
**Severity:** HIGH

### Issue #2: Background Location Tracking Stops
**Symptom:** Service stops when clearing recent apps
**Impact:** No location data collected, breaks core functionality
**Severity:** CRITICAL

---

## Root Cause Analysis

### Issue #1: Real-time Data Flow Problems

**Potential Causes:**
1. ViewModels not collecting StateFlow/Flow properly
2. UI not using `.collectAsState()` or `.collectAsStateWithLifecycle()`
3. Repository queries not returning Flow (using suspend functions instead)
4. Time calculations not reactive (computed once, never updated)
5. AppStateManager not emitting updates
6. Database triggers not firing

**Need to Verify:**
- [ ] Each ViewModel observes correct Flows
- [ ] Each Screen collects ViewModel state
- [ ] Time calculations re-run periodically
- [ ] Visit durations update every second/minute
- [ ] Dashboard refreshes when data changes
- [ ] Timeline updates when new visits added

---

### Issue #2: Background Service Dies

**Potential Causes:**
1. Battery optimization killing service
2. Service not properly configured in manifest
3. Missing `START_STICKY` flag (CHECKED: Present ✅)
4. Notification not persistent
5. WorkManager constraints too restrictive
6. App not requesting "Don't Optimize" permission
7. Service not restarting on device reboot

**Need to Verify:**
- [ ] Manifest has proper foreground service permissions
- [ ] Notification channel priority is HIGH
- [ ] Service requests battery optimization exemption
- [ ] Service registered in manifest with android:stopWithTask="false"
- [ ] Boot receiver registered for service restart
- [ ] Service properly handles onTaskRemoved()

---

## Comprehensive Test Plan

### Phase 1: Test Infrastructure Setup

#### 1.1 Create Test Data Fixtures

`test/fixtures/TestDataFactory.kt`:
```kotlin
object TestDataFactory {

    /**
     * Generate realistic location points along a path
     */
    fun generateLocationPath(
        startLat: Double = 37.7749,
        startLng: Double = -122.4194,
        points: Int = 100,
        timeIntervalSeconds: Long = 30,
        radiusMeters: Double = 500.0
    ): List<Location> {
        val now = LocalDateTime.now()
        return (0 until points).map { i ->
            val angle = (i.toDouble() / points) * 2 * Math.PI
            val offsetLat = (Math.cos(angle) * radiusMeters) / 111111.0
            val offsetLng = (Math.sin(angle) * radiusMeters) / (111111.0 * Math.cos(Math.toRadians(startLat)))

            Location(
                latitude = startLat + offsetLat,
                longitude = startLng + offsetLng,
                timestamp = now.minusSeconds(timeIntervalSeconds * (points - i)),
                accuracy = 10.0f + (Math.random() * 5).toFloat(),
                speed = 1.5f + (Math.random() * 2).toFloat(),
                altitude = 50.0,
                bearing = (angle * 180 / Math.PI).toFloat()
            )
        }
    }

    /**
     * Generate a place with realistic data
     */
    fun createPlace(
        id: Long = 1,
        name: String = "Test Coffee Shop",
        category: PlaceCategory = PlaceCategory.FOOD,
        latitude: Double = 37.7749,
        longitude: Double = -122.4194,
        address: String = "123 Market St, San Francisco, CA",
        visitCount: Int = 5,
        totalTimeSpent: Long = 18000000L // 5 hours
    ) = Place(
        id = id,
        name = name,
        category = category,
        latitude = latitude,
        longitude = longitude,
        address = address,
        streetName = "Market St",
        city = "San Francisco",
        state = "CA",
        country = "USA",
        radius = 50.0,
        confidence = 0.85,
        visitCount = visitCount,
        totalTimeSpent = totalTimeSpent,
        lastVisit = LocalDateTime.now().minusHours(2),
        createdAt = LocalDateTime.now().minusDays(30),
        placeId = null,
        photoReference = null
    )

    /**
     * Generate a visit with entry and exit times
     */
    fun createVisit(
        id: Long = 1,
        placeId: Long = 1,
        entryTime: LocalDateTime = LocalDateTime.now().minusHours(2),
        exitTime: LocalDateTime? = LocalDateTime.now().minusHours(1),
        duration: Long = 3600000L // 1 hour
    ) = Visit(
        id = id,
        placeId = placeId,
        entryTime = entryTime,
        exitTime = exitTime,
        duration = duration
    )

    /**
     * Generate an ongoing visit (no exit time)
     */
    fun createOngoingVisit(
        id: Long = 1,
        placeId: Long = 1,
        entryTime: LocalDateTime = LocalDateTime.now().minusMinutes(30)
    ) = Visit(
        id = id,
        placeId = placeId,
        entryTime = entryTime,
        exitTime = null,
        duration = 0L // Will be calculated
    )

    /**
     * Generate a full day of realistic location data
     */
    fun generateFullDayLocationData(): List<Location> {
        val locations = mutableListOf<Location>()
        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()

        // Morning commute (7-8 AM)
        locations.addAll(generateLocationPath(
            startLat = 37.7749, // Home
            startLng = -122.4194,
            points = 40,
            timeIntervalSeconds = 60,
            radiusMeters = 2000.0 // Moving
        ))

        // At work (8 AM - 5 PM)
        locations.addAll(generateLocationPath(
            startLat = 37.7849, // Work
            startLng = -122.4094,
            points = 180,
            timeIntervalSeconds = 180, // Every 3 minutes
            radiusMeters = 50.0 // Stationary
        ))

        // Evening commute (5-6 PM)
        locations.addAll(generateLocationPath(
            startLat = 37.7849,
            startLng = -122.4094,
            points = 40,
            timeIntervalSeconds = 60,
            radiusMeters = 2000.0 // Moving
        ))

        // At home (6 PM - now)
        val hoursAtHome = java.time.Duration.between(startOfDay.plusHours(18), now).toHours()
        locations.addAll(generateLocationPath(
            startLat = 37.7749,
            startLng = -122.4194,
            points = (hoursAtHome * 10).toInt(),
            timeIntervalSeconds = 360, // Every 6 minutes
            radiusMeters = 30.0 // Stationary
        ))

        return locations
    }
}
```

#### 1.2 Create Fake Repositories

`test/fake/FakeLocationRepository.kt`:
```kotlin
class FakeLocationRepository : LocationRepository {
    private val locations = mutableListOf<Location>()
    private val locationsFlow = MutableStateFlow<List<Location>>(emptyList())

    // Inject test data
    fun injectLocations(testLocations: List<Location>) {
        locations.clear()
        locations.addAll(testLocations)
        locationsFlow.value = locations.toList()
    }

    override suspend fun insertLocation(location: Location): Long {
        val newLocation = location.copy(id = locations.size.toLong() + 1)
        locations.add(newLocation)
        locationsFlow.value = locations.toList()
        return newLocation.id
    }

    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return locationsFlow.map { it.takeLast(limit) }
    }

    override fun getLocationsBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flow<List<Location>> {
        return locationsFlow.map { allLocations ->
            allLocations.filter { location ->
                location.timestamp.isAfter(startTime) && location.timestamp.isBefore(endTime)
            }
        }
    }

    override suspend fun getLocationCount(): Int = locations.size

    override suspend fun deleteLocationsBefore(beforeDate: LocalDateTime): Int {
        val beforeSize = locations.size
        locations.removeIf { it.timestamp.isBefore(beforeDate) }
        locationsFlow.value = locations.toList()
        return beforeSize - locations.size
    }

    override suspend fun deleteAllLocations() {
        locations.clear()
        locationsFlow.value = emptyList()
    }
}
```

`test/fake/FakePlaceRepository.kt`:
```kotlin
class FakePlaceRepository : PlaceRepository {
    private val places = mutableListOf<Place>()
    private val placesFlow = MutableStateFlow<List<Place>>(emptyList())

    fun injectPlaces(testPlaces: List<Place>) {
        places.clear()
        places.addAll(testPlaces)
        placesFlow.value = places.toList()
    }

    override fun getAllPlaces(): Flow<List<Place>> = placesFlow

    override suspend fun getPlaceById(id: Long): Place? {
        return places.find { it.id == id }
    }

    override suspend fun insertPlace(place: Place): Long {
        val newPlace = place.copy(id = places.size.toLong() + 1)
        places.add(newPlace)
        placesFlow.value = places.toList()
        return newPlace.id
    }

    override suspend fun updatePlace(place: Place) {
        val index = places.indexOfFirst { it.id == place.id }
        if (index != -1) {
            places[index] = place
            placesFlow.value = places.toList()
        }
    }

    override suspend fun deletePlace(place: Place) {
        places.remove(place)
        placesFlow.value = places.toList()
    }

    override fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>> {
        return placesFlow.map { it.filter { place -> place.category == category } }
    }
}
```

---

### Phase 2: ViewModel Tests with Injected Data

#### 2.1 Test DashboardViewModel Real-time Updates

`test/presentation/DashboardViewModelTest.kt`:
```kotlin
@ExperimentalCoroutinesTest
class DashboardViewModelTest {

    private lateinit var fakeLocationRepository: FakeLocationRepository
    private lateinit var fakePlaceRepository: FakePlaceRepository
    private lateinit var fakeVisitRepository: FakeVisitRepository
    private lateinit var fakeAppStateManager: FakeAppStateManager
    private lateinit var viewModel: DashboardViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fakeLocationRepository = FakeLocationRepository()
        fakePlaceRepository = FakePlaceRepository()
        fakeVisitRepository = FakeVisitRepository()
        fakeAppStateManager = FakeAppStateManager()

        // Create viewModel with fake dependencies
        viewModel = DashboardViewModel(
            context = ApplicationProvider.getApplicationContext(),
            locationRepository = fakeLocationRepository,
            placeRepository = fakePlaceRepository,
            // ... other dependencies
            appStateManager = fakeAppStateManager,
            // ... use mocks for other dependencies
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dashboard updates when new location is added`() = runTest {
        // Given: Initial state
        val initialState = viewModel.uiState.value
        assertThat(initialState.totalLocations).isEqualTo(0)

        // When: Inject 10 locations
        val testLocations = TestDataFactory.generateLocationPath(points = 10)
        fakeLocationRepository.injectLocations(testLocations)

        advanceUntilIdle()

        // Then: Dashboard shows updated count
        val updatedState = viewModel.uiState.value
        assertThat(updatedState.totalLocations).isEqualTo(10)
        assertThat(updatedState.isLoading).isFalse()
    }

    @Test
    fun `dashboard shows real-time visit duration`() = runTest {
        // Given: User entered a place 30 minutes ago
        val place = TestDataFactory.createPlace(id = 1, name = "Coffee Shop")
        fakePlaceRepository.injectPlaces(listOf(place))

        val visit = TestDataFactory.createOngoingVisit(
            placeId = 1,
            entryTime = LocalDateTime.now().minusMinutes(30)
        )
        fakeVisitRepository.injectVisits(listOf(visit))

        // Update app state to show user is at place
        fakeAppStateManager.setCurrentPlace(
            placeId = 1,
            entryTime = visit.entryTime
        )

        advanceUntilIdle()

        // Then: Dashboard shows ongoing visit
        val state = viewModel.uiState.value
        assertThat(state.isAtPlace).isTrue()
        assertThat(state.currentPlace?.id).isEqualTo(1)
        assertThat(state.currentVisitDuration).isGreaterThan(1800000L) // > 30 minutes
    }

    @Test
    fun `dashboard updates tracking status in real-time`() = runTest {
        // Given: Tracking is off
        assertThat(viewModel.uiState.value.isLocationTrackingActive).isFalse()

        // When: AppStateManager reports tracking started
        fakeAppStateManager.updateTrackingStatus(isActive = true)
        advanceUntilIdle()

        // Then: Dashboard reflects change immediately
        assertThat(viewModel.uiState.value.isLocationTrackingActive).isTrue()
        assertThat(viewModel.uiState.value.isTracking).isTrue()
    }

    @Test
    fun `time tracked updates accurately`() = runTest {
        // Given: Multiple completed visits
        val visits = listOf(
            TestDataFactory.createVisit(
                id = 1,
                placeId = 1,
                duration = 3600000L // 1 hour
            ),
            TestDataFactory.createVisit(
                id = 2,
                placeId = 2,
                duration = 7200000L // 2 hours
            ),
            TestDataFactory.createVisit(
                id = 3,
                placeId = 1,
                duration = 1800000L // 30 minutes
            )
        )
        fakeVisitRepository.injectVisits(visits)

        advanceUntilIdle()

        // Then: Total time = 3.5 hours
        val state = viewModel.uiState.value
        val expectedTotal = 3600000L + 7200000L + 1800000L
        assertThat(state.totalTimeTracked).isEqualTo(expectedTotal)
    }
}
```

#### 2.2 Test Timeline Real-time Updates

`test/presentation/TimelineViewModelTest.kt`:
```kotlin
@ExperimentalCoroutinesTest
class TimelineViewModelTest {

    @Test
    fun `timeline shows visits in chronological order`() = runTest {
        // Given: 5 visits at different times
        val visits = listOf(
            TestDataFactory.createVisit(
                id = 1,
                entryTime = LocalDateTime.now().minusHours(5),
                exitTime = LocalDateTime.now().minusHours(4)
            ),
            TestDataFactory.createVisit(
                id = 2,
                entryTime = LocalDateTime.now().minusHours(3),
                exitTime = LocalDateTime.now().minusHours(2)
            ),
            TestDataFactory.createVisit(
                id = 3,
                entryTime = LocalDateTime.now().minusHours(1),
                exitTime = null // Ongoing
            )
        )
        fakeVisitRepository.injectVisits(visits)

        advanceUntilIdle()

        // Then: Timeline shows newest first
        val state = viewModel.uiState.value
        assertThat(state.visits).hasSize(3)
        assertThat(state.visits[0].id).isEqualTo(3) // Most recent
        assertThat(state.visits[2].id).isEqualTo(1) // Oldest
    }

    @Test
    fun `ongoing visit duration updates every minute`() = runTest {
        // Given: Ongoing visit started 10 minutes ago
        val visit = TestDataFactory.createOngoingVisit(
            entryTime = LocalDateTime.now().minusMinutes(10)
        )
        fakeVisitRepository.injectVisits(listOf(visit))

        advanceUntilIdle()

        val initialDuration = viewModel.uiState.value.visits[0].duration

        // When: 1 minute passes (simulate)
        advanceTimeBy(60000) // 60 seconds

        // Then: Duration increased by 1 minute
        val updatedDuration = viewModel.uiState.value.visits[0].duration
        assertThat(updatedDuration).isGreaterThan(initialDuration)
    }
}
```

---

### Phase 3: Service Persistence Tests

#### 3.1 Test Background Service Survival

`androidTest/service/LocationTrackingServiceTest.kt`:
```kotlin
@RunWith(AndroidJUnit4::class)
class LocationTrackingServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun service_stays_alive_after_task_removed() {
        // Start service
        val intent = Intent(ApplicationProvider.getApplicationContext(), LocationTrackingService::class.java)
        intent.action = LocationTrackingService.ACTION_START_TRACKING

        val binder = serviceRule.bindService(intent)

        // Verify service is running
        assertThat(binder).isNotNull()

        // Simulate task removal
        val service = serviceRule.getService() as LocationTrackingService
        service.onTaskRemoved(Intent())

        // Wait a bit
        Thread.sleep(2000)

        // Verify service is still running
        // (This would require checking if service is in foreground state)
        assertThat(service.isTracking).isTrue()
    }

    @Test
    fun service_restarts_on_crash() {
        // This test would simulate a crash and verify START_STICKY behavior
        // Requires more complex setup with ActivityTestRule
    }
}
```

---

### Phase 4: Integration Tests

#### 4.1 Test Complete Data Flow

`androidTest/integration/LocationToUIFlowTest.kt`:
```kotlin
@RunWith(AndroidJUnit4::class)
class LocationToUIFlowTest {

    @Test
    fun location_insertion_updates_dashboard_immediately() = runTest {
        // Given: App is running with Dashboard visible
        val scenario = launchActivity<MainActivity>()

        // Get database instance
        val context = ApplicationProvider.getApplicationContext<VoyagerApplication>()
        val db = // Get database from Hilt

        // When: Insert a location
        val location = TestDataFactory.generateLocationPath(points = 1)[0]
        db.locationDao().insertLocation(location.toEntity())

        // Then: Dashboard should update within 2 seconds
        Thread.sleep(2000)

        onView(withText("1")) // Total locations count
            .check(matches(isDisplayed()))
    }
}
```

---

### Phase 5: Time Calculation Accuracy Tests

`test/domain/TimeCalculationTest.kt`:
```kotlin
class TimeCalculationTest {

    @Test
    fun `visit duration calculated correctly`() {
        val entryTime = LocalDateTime.of(2025, 11, 14, 10, 0, 0)
        val exitTime = LocalDateTime.of(2025, 11, 14, 12, 30, 0)

        val duration = Duration.between(entryTime, exitTime).toMillis()

        assertThat(duration).isEqualTo(9000000L) // 2.5 hours in milliseconds
    }

    @Test
    fun `ongoing visit duration updates correctly`() {
        val entryTime = LocalDateTime.now().minusHours(2).minusMinutes(30)
        val currentTime = LocalDateTime.now()

        val duration = Duration.between(entryTime, currentTime).toMillis()

        // Should be approximately 2.5 hours (allowing 1 minute variance)
        assertThat(duration).isGreaterThan(9000000L - 60000L)
        assertThat(duration).isLessThan(9000000L + 60000L)
    }

    @Test
    fun `day analytics time totals are accurate`() {
        val visits = listOf(
            TestDataFactory.createVisit(duration = 3600000L), // 1 hour
            TestDataFactory.createVisit(duration = 7200000L), // 2 hours
            TestDataFactory.createVisit(duration = 1800000L)  // 30 min
        )

        val totalTime = visits.sumOf { it.duration }

        assertThat(totalTime).isEqualTo(12600000L) // 3.5 hours
    }
}
```

---

## Execution Plan

### Week 1: Infrastructure
- Day 1-2: Create test data factories
- Day 3-4: Create fake repositories
- Day 5: Set up test dependencies in build.gradle

### Week 2: ViewModel Tests
- Day 1-2: Dashboard tests (5-10 tests)
- Day 3: Timeline tests (3-5 tests)
- Day 4: Map tests (3-5 tests)
- Day 5: Insights tests (3-5 tests)

### Week 3: Integration & Service Tests
- Day 1-2: Repository integration tests
- Day 3-4: Service persistence tests
- Day 5: End-to-end flow tests

### Week 4: Bug Fixes
- Fix issues found by tests
- Re-run all tests
- Manual verification

---

## Expected Outcomes

After running these tests, we will know:

✅ **What Actually Works:**
- Which screens update in real-time
- Which time calculations are accurate
- Which data flows are reactive

❌ **What Needs Fixing:**
- Specific ViewModels not collecting Flows
- Specific UI components not observing state
- Service configuration issues
- Time calculation bugs

---

## Next Steps

1. **Immediate:** Create test infrastructure (factories, fakes)
2. **Day 1-2:** Write and run ViewModel tests
3. **Day 3:** Analyze test failures and identify real bugs
4. **Day 4-5:** Fix bugs found by tests
5. **Week 2:** Write service and integration tests

This approach will give us **concrete evidence** of what's broken and **reproducible tests** to verify fixes.

Ready to start implementing? Let's begin with the test infrastructure.
