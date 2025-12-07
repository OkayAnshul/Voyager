# Automated Testing Strategy for Voyager
**Version:** 1.0
**Last Updated:** 2025-11-14
**Target Coverage:** 80%+ for critical paths

---

## Testing Philosophy

**Pyramid Approach:**
```
           ┌───────────────┐
           │   E2E Tests   │  ← 10% (Critical user flows)
           │   (5-10)      │
           ├───────────────┤
           │ Integration   │  ← 30% (Repository, ViewModel, Use Case)
           │ Tests (30-50) │
           ├───────────────┤
           │  Unit Tests   │  ← 60% (Business logic, utilities)
           │  (100-150)    │
           └───────────────┘
```

**Priorities:**
1. ✅ **Unit Tests** - Fast, cheap, many
2. ✅ **Integration Tests** - Moderate speed/cost, focused on critical integrations
3. ⚠️ **E2E Tests** - Slow, expensive, only for critical happy paths

---

## Test Stack

### Dependencies to Add

```kotlin
// build.gradle.kts (app module)
dependencies {
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")

    // Mockk for mocking
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.8")

    // Coroutines Test
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.0.0")

    // Truth assertions
    testImplementation("com.google.truth:truth:1.1.5")

    // Room testing
    testImplementation("androidx.room:room-testing:2.6.1")

    // Hilt testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48")

    // Espresso for UI tests
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")

    // Robolectric for Android unit tests
    testImplementation("org.robolectric:robolectric:4.11.1")

    // Fake data
    testImplementation("com.github.javafaker:javafaker:1.0.2")
}
```

---

## Phase 1: Unit Tests (Week 1-2)

### 1.1 Use Case Tests

**Priority:** CRITICAL - This is your business logic

#### CompareWeeklyAnalyticsUseCase Test

`test/domain/usecase/CompareWeeklyAnalyticsUseCaseTest.kt`:

```kotlin
@Test
fun `invoke returns correct weekly comparison`() = runTest {
    // Given: Mock repositories with test data
    val thisWeekVisits = listOf(
        Visit(placeId = 1, duration = 3600000L, entryTime = thisMonday),
        Visit(placeId = 2, duration = 7200000L, entryTime = thisWednesday)
    )
    val lastWeekVisits = listOf(
        Visit(placeId = 1, duration = 1800000L, entryTime = lastMonday)
    )

    coEvery { visitRepository.getVisitsBetween(any(), any()) } returns
        flowOf(thisWeekVisits) andThen flowOf(lastWeekVisits)

    // When
    val result = useCase.invoke(referenceDate = thisMonday)

    // Then
    assertThat(result.thisWeekTotalTime).isEqualTo(10800000L)
    assertThat(result.lastWeekTotalTime).isEqualTo(1800000L)
    assertThat(result.totalTimeChange).isWithin(1f).of(500f) // 500% increase
    assertThat(result.totalTimeTrend).isEqualTo(Trend.UP)
}

@Test
fun `calculates percentage change correctly for edge cases`() = runTest {
    // Test: 0 -> 0 = 0%
    // Test: 0 -> 100 = 100%
    // Test: 100 -> 0 = -100%
    // Test: 50 -> 100 = 100%
}

@Test
fun `handles empty visit lists`() = runTest {
    // Given: No visits
    coEvery { visitRepository.getVisitsBetween(any(), any()) } returns flowOf(emptyList())

    // When
    val result = useCase.invoke()

    // Then
    assertThat(result.thisWeekTotalTime).isEqualTo(0L)
    assertThat(result.placeComparisons).isEmpty()
}
```

**Tests to Write:**
- ✅ Correct week boundary calculation (Monday-Sunday)
- ✅ Percentage change calculations
- ✅ Per-place comparison accuracy
- ✅ Trend detection (UP/DOWN/STABLE)
- ✅ Edge cases: empty data, single visit, etc.

**Estimated:** 8 tests, ~2 hours

---

#### AnalyzePlacePatternsUseCase Test

```kotlin
@Test
fun `detects day-of-week pattern for weekday visits`() = runTest {
    // Given: Place visited every weekday at 9am
    val visits = (1..20).map { day ->
        Visit(
            placeId = 1,
            entryTime = mondayMorning.plusDays(day.toLong()),
            duration = 28800000L // 8 hours
        )
    }.filter { it.entryTime.dayOfWeek != DayOfWeek.SATURDAY &&
               it.entryTime.dayOfWeek != DayOfWeek.SUNDAY }

    mockVisitsForPlace(placeId = 1, visits)

    // When
    val patterns = useCase.invoke()

    // Then
    val dayPattern = patterns.find { it.patternType == PlacePatternType.DAY_OF_WEEK }
    assertThat(dayPattern).isNotNull()
    assertThat(dayPattern!!.confidence).isGreaterThan(0.7f)
    assertThat(dayPattern.description).contains("Monday")
    assertThat(dayPattern.description).contains("Friday")
}

@Test
fun `detects time-of-day pattern`() = runTest {
    // Given: Visits all around 2pm +/- 30min
    val visits = generateVisitsAroundTime(place = homePlace, time = "14:00", variance = 30)

    // When
    val patterns = useCase.invoke()

    // Then
    val timePattern = patterns.find { it.patternType == PlacePatternType.TIME_OF_DAY }
    assertThat(timePattern?.confidence).isGreaterThan(0.5f)
}

@Test
fun `detects daily routine pattern`() = runTest {
    // Given: Home visited every night at 10pm
    val visits = (1..30).map {
        Visit(placeId = homeId, entryTime = today.minusDays(it).atTime(22, 0))
    }

    // When
    val patterns = useCase.invoke()

    // Then
    val routinePattern = patterns.find { it.patternType == PlacePatternType.DAILY_ROUTINE }
    assertThat(routinePattern?.description).contains("every night")
}

@Test
fun `requires minimum visits before detecting pattern`() = runTest {
    // Given: Only 2 visits (below MIN_VISITS_FOR_PATTERN)
    mockVisitsForPlace(placeId = 1, listOf(visit1, visit2))

    // When
    val patterns = useCase.invoke()

    // Then
    assertThat(patterns).isEmpty()
}
```

**Tests to Write:**
- ✅ Day-of-week detection with various schedules
- ✅ Time-of-day clustering
- ✅ Frequency pattern (daily, weekly, occasional)
- ✅ Daily routine detection
- ✅ Minimum visit threshold
- ✅ Confidence scoring accuracy

**Estimated:** 12 tests, ~3 hours

---

#### DetectAnomaliesUseCase Test

```kotlin
@Test
fun `detects unusual time anomaly`() = runTest {
    // Given: Work place normally visited 9am-5pm, but one visit at 3am
    val normalVisits = generateWorkHoursVisits(count = 20)
    val unusualVisit = Visit(
        placeId = workId,
        entryTime = today.atTime(3, 0),
        duration = 3600000L
    )

    mockVisitsForPlace(workId, normalVisits + unusualVisit)

    // When
    val anomalies = useCase.invoke()

    // Then
    val timeAnomaly = anomalies.find { it.type == AnomalyType.UNUSUAL_TIME }
    assertThat(timeAnomaly).isNotNull()
    assertThat(timeAnomaly?.visit).isEqualTo(unusualVisit)
}

@Test
fun `detects long absence anomaly`() = runTest {
    // Given: Daily visits to home, then 10 day gap
    val beforeGap = (1..30).map { Visit(..., entryTime = today.minusDays(40 - it)) }
    val afterGap = (1..5).map { Visit(..., entryTime = today.minusDays(it)) }

    // When
    val anomalies = useCase.invoke()

    // Then
    val absenceAnomaly = anomalies.find { it.type == AnomalyType.LONG_ABSENCE }
    assertThat(absenceAnomaly?.daysSinceLastVisit).isGreaterThan(9)
}

@Test
fun `no anomalies for irregular place`() = runTest {
    // Given: Place with random, unpredictable visits
    val randomVisits = generateRandomVisits(count = 10)

    // When
    val anomalies = useCase.invoke()

    // Then: Irregular behavior shouldn't flag anomalies
    assertThat(anomalies).isEmpty()
}
```

**Tests to Write:**
- ✅ Unusual time detection
- ✅ Unusual day detection
- ✅ Unusual duration detection
- ✅ Long absence detection
- ✅ No false positives for irregular places
- ✅ Severity calculation

**Estimated:** 8 tests, ~2 hours

---

### 1.2 Analytics Logic Tests

#### AnalyticsUseCases Test

```kotlin
@Test
fun `generateDayAnalytics calculates all metrics correctly`() = runTest {
    // Given: Known data for a specific day
    val locations = generateLocations(count = 100, date = today)
    val visits = listOf(
        Visit(placeId = 1, duration = 3600000L, entryTime = today.atTime(9, 0)),
        Visit(placeId = 2, duration = 7200000L, entryTime = today.atTime(14, 0))
    )

    mockLocationData(locations)
    mockVisitData(visits)

    // When
    val analytics = analyticsUseCases.generateDayAnalytics(today)

    // Then
    assertThat(analytics.placesVisited).isEqualTo(2)
    assertThat(analytics.totalTimeTracked).isEqualTo(10800000L) // 3 hours
    assertThat(analytics.distanceTraveled).isGreaterThan(0.0)
    assertThat(analytics.longestStay?.placeId).isEqualTo(2)
}

@Test
fun `calculateDistanceTraveled uses haversine formula correctly`() {
    // Given: Two GPS points with known distance
    val point1 = Location(lat = 0.0, lng = 0.0)
    val point2 = Location(lat = 0.1, lng = 0.1) // ~15.7 km

    // When
    val distance = calculateDistance(point1, point2)

    // Then
    assertThat(distance).isWithin(0.5).of(15.7)
}
```

**Tests to Write:**
- ✅ Day analytics calculation
- ✅ Time analytics for week/month
- ✅ Distance calculation accuracy
- ✅ Time by category aggregation
- ✅ Movement pattern detection
- ✅ Current state analytics

**Estimated:** 10 tests, ~3 hours

---

### 1.3 Utility Tests

#### LocationUtils Test

```kotlin
@Test
fun `calculateDistance returns correct km`() {
    val sfLat = 37.7749
    val sfLng = -122.4194
    val laLat = 34.0522
    val laLng = -118.2437

    val distance = LocationUtils.calculateDistance(sfLat, sfLng, laLat, laLng)

    // SF to LA is ~559 km
    assertThat(distance).isWithin(10.0).of(559.0)
}

@Test
fun `isAccurateEnough filters by threshold`() {
    assertThat(LocationUtils.isAccurateEnough(10.0f, 20.0f)).isTrue()
    assertThat(LocationUtils.isAccurateEnough(30.0f, 20.0f)).isFalse()
}

@Test
fun `isMovingFast detects speeding correctly`() {
    assertThat(LocationUtils.isMovingFast(5.0f, 10.0f)).isFalse() // 5 km/h
    assertThat(LocationUtils.isMovingFast(120.0f, 100.0f)).isTrue() // 120 km/h
}
```

**Estimated:** 6 tests, ~1 hour

---

#### TimeUtils / DateFormatters Test

```kotlin
@Test
fun `formatDuration shows correct human-readable time`() {
    assertThat(formatDuration(3661000L)).isEqualTo("1h 1m")
    assertThat(formatDuration(60000L)).isEqualTo("1m")
    assertThat(formatDuration(86400000L)).isEqualTo("1d")
}

@Test
fun `parseTimeString handles various formats`() {
    assertThat(parseTimeString("14:30")).isEqualTo(LocalTime.of(14, 30))
    assertThat(parseTimeString("2:30 PM")).isEqualTo(LocalTime.of(14, 30))
}
```

**Estimated:** 4 tests, ~30 minutes

---

### 1.4 Validation Tests

#### ValidationService Test

```kotlin
@Test
fun `validateLocation rejects inaccurate GPS`() {
    val inaccurateLocation = Location(lat = 0.0, lng = 0.0, accuracy = 500.0f)

    val result = validationService.validateLocation(inaccurateLocation)

    assertThat(result.isValid).isFalse()
    assertThat(result.errors).contains(ValidationError.POOR_ACCURACY)
}

@Test
fun `validatePlace ensures required fields`() {
    val invalidPlace = Place(name = "", lat = 200.0, lng = 0.0)

    val result = validationService.validatePlace(invalidPlace)

    assertThat(result.isValid).isFalse()
    assertThat(result.errors).contains(ValidationError.INVALID_COORDINATES)
}
```

**Estimated:** 6 tests, ~1 hour

---

## Phase 2: Integration Tests (Week 3-4)

### 2.1 Repository Tests (with Room)

#### LocationRepositoryImpl Test

```kotlin
@Test
fun `insertLocation saves to database and emits flow update`() = runTest {
    // Given
    val location = testLocation()

    // When
    repository.insertLocation(location)

    // Then: Verify database insert
    val saved = database.locationDao().getAllLocations().first()
    assertThat(saved).hasSize(1)
    assertThat(saved[0].latitude).isEqualTo(location.latitude)

    // Then: Verify Flow emission
    val locations = repository.getRecentLocations(10).first()
    assertThat(locations).contains(location)
}

@Test
fun `getLocationsBetween filters by time range`() = runTest {
    // Given: Locations across 3 days
    insertLocations(
        locationAt(today.minusDays(2)),
        locationAt(today.minusDays(1)),
        locationAt(today)
    )

    // When: Query for yesterday only
    val locations = repository.getLocationsBetween(
        yesterday.atStartOfDay(),
        yesterday.atTime(23, 59)
    ).first()

    // Then
    assertThat(locations).hasSize(1)
    assertThat(locations[0].timestamp.toLocalDate()).isEqualTo(yesterday)
}

@Test
fun `deleteLocationsBefore removes old data`() = runTest {
    // Given
    insertLocations(
        locationAt(today.minusDays(40)),
        locationAt(today.minusDays(20)),
        locationAt(today)
    )

    // When: Delete older than 30 days
    repository.deleteLocationsBefore(today.minusDays(30))

    // Then
    val remaining = repository.getRecentLocations(100).first()
    assertThat(remaining).hasSize(2) // Only recent 2 remain
}
```

**Tests to Write:**
- ✅ Insert, update, delete operations
- ✅ Flow emissions on data changes
- ✅ Time-range queries
- ✅ Pagination
- ✅ Cleanup operations
- ✅ Transaction handling

**Estimated:** 10 tests per repository × 4 repositories = 40 tests, ~6 hours

---

### 2.2 ViewModel Tests

#### DashboardViewModel Test

```kotlin
@Test
fun `uiState updates when AppStateManager emits new state`() = runTest {
    // Given: Mock AppStateManager with StateFlow
    val appStateFlow = MutableStateFlow(AppState(
        locationTracking = TrackingState(isActive = false),
        dailyStats = DailyStats(locationCount = 0)
    ))
    every { appStateManager.appState } returns appStateFlow

    val viewModel = DashboardViewModel(...)

    // When: App state changes to tracking active
    appStateFlow.value = AppState(
        locationTracking = TrackingState(isActive = true),
        dailyStats = DailyStats(locationCount = 10)
    )

    advanceUntilIdle() // Process coroutines

    // Then: UI state reflects change
    assertThat(viewModel.uiState.value.isLocationTrackingActive).isTrue()
    assertThat(viewModel.uiState.value.totalLocations).isEqualTo(10)
}

@Test
fun `toggleLocationTracking starts service when not tracking`() = runTest {
    // Given
    val viewModel = DashboardViewModel(...)

    // When
    viewModel.toggleLocationTracking()

    // Then
    coVerify { locationUseCases.startLocationTracking() }
}

@Test
fun `refreshDashboard clears cache and reloads`() = runTest {
    // Given: Cached analytics
    val viewModel = DashboardViewModel(...)
    viewModel.loadDashboardData() // Load with cache

    // When: Refresh
    viewModel.refreshDashboard()

    // Then: Analytics regenerated (verify use case called twice)
    coVerify(exactly = 2) { analyticsUseCases.generateDayAnalytics(any()) }
}
```

**Tests to Write (per ViewModel):**
- ✅ UI state initialization
- ✅ Flow collection and updates
- ✅ User action handling
- ✅ Error handling
- ✅ Loading states
- ✅ Caching behavior

**Estimated:** 8 tests × 6 ViewModels = 48 tests, ~8 hours

---

### 2.3 Worker Tests

#### PlaceDetectionWorker Test

```kotlin
@Test
fun `doWork detects places successfully`() = runTest {
    // Given: Sufficient location data
    mockLocations(count = 100)
    val worker = TestListenableWorkerBuilder<PlaceDetectionWorker>(context).build()

    // When
    val result = worker.doWork()

    // Then
    assertThat(result).isEqualTo(Result.success())
    coVerify { placeDetectionUseCases.detectNewPlaces() }
}

@Test
fun `doWork retries on transient failure`() = runTest {
    // Given: Use case throws retryable exception
    coEvery { placeDetectionUseCases.detectNewPlaces() } throws IOException()

    // When
    val result = worker.doWork()

    // Then
    assertThat(result).isInstanceOf(Result.retry())
}

@Test
fun `doWork respects battery requirement`() = runTest {
    // Given: Low battery + battery requirement
    mockBatteryLevel(10)
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    // Worker shouldn't even start
    // Test WorkManager scheduling logic
}
```

**Estimated:** 6 tests × 2 workers = 12 tests, ~3 hours

---

## Phase 3: UI Tests (Week 5)

### 3.1 Compose UI Tests

#### DashboardScreen Test

```kotlin
@Test
fun `displays tracking status correctly`() {
    // Given
    val uiState = DashboardUiState(
        isLocationTrackingActive = true,
        totalLocations = 42,
        isLoading = false
    )

    composeTestRule.setContent {
        DashboardScreen(viewModel = mockViewModel(uiState))
    }

    // Then
    composeTestRule.onNodeWithText("Live").assertIsDisplayed()
    composeTestRule.onNodeWithText("42").assertIsDisplayed()
}

@Test
fun `start tracking button triggers viewModel action`() {
    composeTestRule.setContent {
        DashboardScreen(viewModel = viewModel)
    }

    // When
    composeTestRule.onNodeWithText("Start Tracking").performClick()

    // Then
    verify { viewModel.toggleLocationTracking() }
}

@Test
fun `shows loading state`() {
    val uiState = DashboardUiState(isLoading = true)

    composeTestRule.setContent {
        DashboardScreen(viewModel = mockViewModel(uiState))
    }

    composeTestRule.onNode(hasTestTag("LoadingIndicator")).assertIsDisplayed()
}
```

**Tests to Write (per screen):**
- ✅ Initial render
- ✅ State changes
- ✅ User interactions
- ✅ Loading/error states
- ✅ Navigation

**Estimated:** 5 tests × 8 screens = 40 tests, ~6 hours

---

### 3.2 Navigation Tests

```kotlin
@Test
fun `navigate from insights to weekly comparison`() {
    val navController = TestNavHostController(ApplicationProvider.getApplicationContext())

    composeTestRule.setContent {
        VoyagerNavHost(navController = navController)
    }

    // Navigate to Insights
    navController.navigate(VoyagerDestination.Insights.route)

    // Tap weekly comparison card
    composeTestRule.onNodeWithText("Weekly Comparison").performClick()

    // Assert navigation occurred
    assertThat(navController.currentBackStackEntry?.destination?.route)
        .isEqualTo(VoyagerDestination.WeeklyComparison.route)
}
```

**Estimated:** 8 navigation flows, ~2 hours

---

## Phase 4: E2E Tests (Week 6)

### 4.1 Critical User Flows

#### Complete Tracking Flow Test

```kotlin
@Test
fun `complete user journey - track, detect places, view insights`() {
    // This would be a SLOW test - use sparingly

    // 1. Launch app
    // 2. Start tracking
    // 3. Inject mock locations
    // 4. Trigger place detection
    // 5. Verify places appear in UI
    // 6. Navigate to insights
    // 7. Verify analytics shown

    // Total time: ~30 seconds
}
```

**Critical Flows to Test:**
- ✅ First-time user onboarding
- ✅ Start tracking → detect places → view timeline
- ✅ Configure settings → observe behavior change
- ✅ Export data → verify file created

**Estimated:** 5 flows, ~4 hours

---

## Test Infrastructure Setup

### Fakes & Test Doubles

Create `test/fake/` package:

**FakeLocationRepository:**
```kotlin
class FakeLocationRepository : LocationRepository {
    private val locations = mutableListOf<Location>()
    private val locationsFlow = MutableStateFlow<List<Location>>(emptyList())

    override suspend fun insertLocation(location: Location) {
        locations.add(location)
        locationsFlow.value = locations.toList()
    }

    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return locationsFlow.map { it.takeLast(limit) }
    }

    // ... implement all methods
}
```

**FakeAppStateManager:**
```kotlin
class FakeAppStateManager : AppStateManager {
    override val appState = MutableStateFlow(AppState())

    fun setTracking(isActive: Boolean) {
        appState.value = appState.value.copy(
            locationTracking = TrackingState(isActive = isActive)
        )
    }
}
```

**Test Data Builders:**
```kotlin
object TestDataFactory {
    fun location(
        lat: Double = 37.7749,
        lng: Double = -122.4194,
        timestamp: LocalDateTime = LocalDateTime.now(),
        accuracy: Float = 10.0f
    ) = Location(lat, lng, timestamp, accuracy)

    fun place(
        id: Long = 1,
        name: String = "Test Place",
        category: PlaceCategory = PlaceCategory.OTHER
    ) = Place(id, name, category, ...)

    fun visit(
        placeId: Long = 1,
        duration: Long = 3600000L,
        entryTime: LocalDateTime = LocalDateTime.now()
    ) = Visit(placeId, entryTime, duration)
}
```

---

## CI/CD Integration

### GitHub Actions Workflow

`.github/workflows/test.yml`:

```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run unit tests
        run: ./gradlew testDebugUnitTest
      - name: Upload test report
        uses: actions/upload-artifact@v3
        with:
          name: unit-test-report
          path: app/build/reports/tests/

  instrumented-tests:
    runs-on: macos-latest # For Android emulator
    steps:
      - uses: actions/checkout@v3
      - name: Run instrumented tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33
          script: ./gradlew connectedDebugAndroidTest
```

---

## Code Coverage Goals

### Target Coverage

| Layer | Target | Priority |
|-------|--------|----------|
| Use Cases | 90%+ | CRITICAL |
| ViewModels | 85%+ | HIGH |
| Repositories | 80%+ | HIGH |
| Utils | 90%+ | MEDIUM |
| UI | 60%+ | LOW |

### Measure with JaCoCo

```kotlin
// build.gradle.kts
plugins {
    id("jacoco")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files("build/intermediates/classes/debug"))
    executionData.setFrom(files("build/jacoco/testDebugUnitTest.exec"))
}
```

Run: `./gradlew jacocoTestReport`

---

## Testing Schedule

### Week 1-2: Unit Tests (Foundation)
- Day 1-3: Use case tests (CompareWeekly, AnalyzePatterns, DetectAnomalies)
- Day 4-5: Analytics logic tests
- Day 6-7: Utility and validation tests
- Day 8-10: Review and refine

### Week 3-4: Integration Tests
- Day 1-4: Repository tests (all 4 repositories)
- Day 5-8: ViewModel tests (all 6 ViewModels)
- Day 9-10: Worker tests

### Week 5: UI Tests
- Day 1-3: Compose UI tests for main screens
- Day 4-5: Navigation and interaction tests

### Week 6: E2E & Polish
- Day 1-2: Critical user flow E2E tests
- Day 3-4: Fix flaky tests, improve stability
- Day 5: Coverage report and gap analysis

**Total: 6 weeks part-time (3 weeks full-time)**

---

## Test Execution Commands

```bash
# Run all tests
./gradlew test

# Run unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests (requires emulator/device)
./gradlew connectedDebugAndroidTest

# Run with coverage
./gradlew testDebugUnitTest jacocoTestReport

# Run specific test class
./gradlew test --tests "CompareWeeklyAnalyticsUseCaseTest"

# Run tests in watch mode
./gradlew test --continuous
```

---

## Success Metrics

Before considering testing "complete":

- [ ] 150+ unit tests written
- [ ] 50+ integration tests written
- [ ] 40+ UI tests written
- [ ] 5+ E2E tests for critical flows
- [ ] 80%+ overall code coverage
- [ ] 90%+ coverage for use cases
- [ ] 0 flaky tests
- [ ] All tests pass in CI
- [ ] Test execution time < 5 minutes (unit + integration)

---

## Maintenance

### Test Hygiene Rules

1. **Fast Tests:** Unit tests should run in < 2 seconds total
2. **Independent:** Tests must not depend on execution order
3. **Deterministic:** Same input always produces same output
4. **Focused:** One logical assertion per test
5. **Readable:** Test name describes scenario and expectation
6. **Maintainable:** Update tests when requirements change

### Handling Flaky Tests

If a test fails randomly:
1. Identify timing/concurrency issues
2. Add proper `advanceUntilIdle()` for coroutines
3. Use `TestScope` and `runTest` correctly
4. Avoid hardcoded delays (use `advanceTimeBy()`)
5. Mark as `@Ignore` with ticket number if can't fix immediately

---

## Next Steps

1. **Week 1:** Add testing dependencies to `build.gradle.kts`
2. **Week 1:** Set up test infrastructure (fakes, factories, utils)
3. **Week 1:** Write first 10 use case tests
4. **Week 2:** Achieve 50 unit tests milestone
5. **Week 3:** Begin repository integration tests
6. **Month 2:** Full test suite operational
7. **Ongoing:** Maintain and expand as features added

**Remember:** Writing tests is an investment. It pays off when you can confidently refactor, add features, and catch regressions before they reach users.
