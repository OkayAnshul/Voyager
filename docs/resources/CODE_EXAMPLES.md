# Code Examples & Practical Implementations

This guide provides practical, copy-paste examples for common development tasks in the Voyager project.

## 🏗️ Creating New Features

### Adding a New Place Category

**1. Update the Domain Model**

```kotlin
// File: domain/model/Place.kt
enum class PlaceCategory {
    HOME,
    WORK,
    GYM,
    RESTAURANT,
    SHOPPING,
    ENTERTAINMENT,
    HEALTHCARE,
    EDUCATION,
    TRANSPORT,
    TRAVEL,
    OUTDOOR,
    SOCIAL,
    SERVICES,
    COFFEE_SHOP,    // ← New category
    UNKNOWN,
    CUSTOM
}
```

**2. Add Categorization Logic**

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
        isCoffeeShopPattern(patterns, preferences) -> PlaceCategory.COFFEE_SHOP // ← New
        isRestaurantPattern(patterns, preferences) -> PlaceCategory.RESTAURANT
        isShoppingPattern(patterns, preferences) -> PlaceCategory.SHOPPING
        else -> PlaceCategory.UNKNOWN
    }
}

// New categorization function
private fun isCoffeeShopPattern(
    patterns: TemporalPatterns, 
    preferences: UserPreferences
): Boolean {
    // Coffee shops typically have:
    // 1. Morning visits (7-10 AM)
    // 2. Short stays (15-45 minutes)
    // 3. Regular but not daily visits
    
    val morningHours = patterns.hourDistribution
        .filterKeys { it in 7..10 }    // 7 AM to 10 AM
        .values.sum()
    
    val totalActivity = patterns.hourDistribution.values.sum()
    val morningActivity = morningHours.toFloat() / totalActivity
    
    val avgDurationMinutes = patterns.averageStayDuration / 60000L
    val isTypicalCoffeeVisit = avgDurationMinutes in 15..45
    
    return morningActivity >= 0.6f &&           // 60%+ morning visits
           isTypicalCoffeeVisit &&              // Typical duration
           patterns.visitFrequency >= 1.0f &&   // At least weekly
           patterns.visitFrequency <= 5.0f      // But not daily
}
```

**3. Update Name Generation**

```kotlin
// File: domain/usecase/PlaceDetectionUseCases.kt
private fun generatePlaceName(category: PlaceCategory, lat: Double, lng: Double): String {
    val locationHash = ((lat + lng) * 1000).toInt().toString().takeLast(3)
    
    return when (category) {
        PlaceCategory.HOME -> "Home"
        PlaceCategory.WORK -> "Work"
        PlaceCategory.GYM -> "Gym $locationHash"
        PlaceCategory.COFFEE_SHOP -> "Coffee Shop $locationHash"  // ← New
        PlaceCategory.RESTAURANT -> "Restaurant $locationHash"
        PlaceCategory.SHOPPING -> "Store $locationHash"
        else -> "Place $locationHash"
    }
}
```

**4. Update UI Display**

```kotlin
// File: presentation/components/PlaceCategoryIcon.kt
@Composable
fun PlaceCategoryIcon(category: PlaceCategory, modifier: Modifier = Modifier) {
    val iconRes = when (category) {
        PlaceCategory.HOME -> R.drawable.ic_home
        PlaceCategory.WORK -> R.drawable.ic_work
        PlaceCategory.GYM -> R.drawable.ic_fitness
        PlaceCategory.COFFEE_SHOP -> R.drawable.ic_coffee  // ← New icon
        PlaceCategory.RESTAURANT -> R.drawable.ic_restaurant
        PlaceCategory.SHOPPING -> R.drawable.ic_shopping
        else -> R.drawable.ic_place
    }
    
    Icon(
        painter = painterResource(iconRes),
        contentDescription = category.name,
        modifier = modifier
    )
}

@Composable
fun PlaceCategoryText(category: PlaceCategory) {
    val text = when (category) {
        PlaceCategory.HOME -> "Home"
        PlaceCategory.WORK -> "Work"
        PlaceCategory.GYM -> "Gym"
        PlaceCategory.COFFEE_SHOP -> "Coffee Shop"  // ← New display text
        PlaceCategory.RESTAURANT -> "Restaurant"
        PlaceCategory.SHOPPING -> "Shopping"
        else -> "Unknown"
    }
    
    Text(text = text)
}
```

### Creating a Custom Background Worker

**1. Define the Worker**

```kotlin
// File: data/worker/CustomAnalyticsWorker.kt
@HiltWorker
class CustomAnalyticsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsUseCases: AnalyticsUseCases,
    private val logger: ProductionLogger
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "custom_analytics_work"
        private const val TAG = "CustomAnalyticsWorker"
        
        fun createPeriodicWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<CustomAnalyticsWorker>(
                repeatInterval = 6, // Every 6 hours
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        return try {
            logger.i(TAG, "Starting custom analytics processing...")
            
            // 1. Generate weekly analytics
            val weeklyStats = analyticsUseCases.generateWeeklyStats()
            logger.d(TAG, "Generated weekly stats: $weeklyStats")
            
            // 2. Identify new patterns
            val newPatterns = analyticsUseCases.identifyNewPatterns()
            logger.d(TAG, "Found ${newPatterns.size} new patterns")
            
            // 3. Update place confidence scores
            val updatedPlaces = analyticsUseCases.updatePlaceConfidenceScores()
            logger.d(TAG, "Updated confidence for ${updatedPlaces.size} places")
            
            // 4. Return success with data
            val outputData = workDataOf(
                "weekly_stats_generated" to true,
                "new_patterns_count" to newPatterns.size,
                "updated_places_count" to updatedPlaces.size
            )
            
            logger.i(TAG, "Custom analytics processing completed successfully")
            Result.success(outputData)
            
        } catch (e: Exception) {
            logger.e(TAG, "Custom analytics processing failed", e)
            
            // Retry up to 3 times
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error_message" to e.message))
            }
        }
    }
}
```

**2. Schedule the Worker**

```kotlin
// File: domain/usecase/WorkerManagementUseCases.kt
class WorkerManagementUseCases @Inject constructor(
    private val workManagerHelper: WorkManagerHelper
) {
    
    suspend fun scheduleCustomAnalytics(): EnqueueResult {
        return try {
            val workManager = workManagerHelper.getWorkManagerSafely()
                ?: return EnqueueResult.Failed(Exception("WorkManager not available"))
            
            val workRequest = CustomAnalyticsWorker.createPeriodicWorkRequest()
            
            workManager.enqueueUniquePeriodicWork(
                CustomAnalyticsWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            EnqueueResult.Success(workRequest.id)
            
        } catch (e: Exception) {
            EnqueueResult.Failed(e)
        }
    }
    
    suspend fun cancelCustomAnalytics(): Boolean {
        return try {
            val workManager = workManagerHelper.getWorkManagerSafely()
                ?: return false
            
            workManager.cancelUniqueWork(CustomAnalyticsWorker.WORK_NAME)
            true
            
        } catch (e: Exception) {
            false
        }
    }
}
```

**3. Integrate with Settings**

```kotlin
// File: presentation/screen/settings/SettingsViewModel.kt
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val workerManagementUseCases: WorkerManagementUseCases
) : ViewModel() {
    
    fun toggleCustomAnalytics(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) {
                    val result = workerManagementUseCases.scheduleCustomAnalytics()
                    when (result) {
                        is EnqueueResult.Success -> {
                            preferencesRepository.updateCustomAnalyticsEnabled(true)
                            _uiState.value = _uiState.value.copy(
                                customAnalyticsEnabled = true,
                                message = "Custom analytics enabled"
                            )
                        }
                        is EnqueueResult.Failed -> {
                            _uiState.value = _uiState.value.copy(
                                message = "Failed to enable analytics: ${result.exception.message}"
                            )
                        }
                    }
                } else {
                    val cancelled = workerManagementUseCases.cancelCustomAnalytics()
                    if (cancelled) {
                        preferencesRepository.updateCustomAnalyticsEnabled(false)
                        _uiState.value = _uiState.value.copy(
                            customAnalyticsEnabled = false,
                            message = "Custom analytics disabled"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Error: ${e.message}"
                )
            }
        }
    }
}
```

## 🧪 Testing Examples

### Unit Testing ViewModels

```kotlin
// File: presentation/screen/dashboard/DashboardViewModelTest.kt
@ExtendWith(MockitoExtension::class)
class DashboardViewModelTest {
    
    @Mock lateinit var locationUseCases: LocationUseCases
    @Mock lateinit var analyticsUseCases: AnalyticsUseCases
    @Mock lateinit var workManagerHelper: WorkManagerHelper
    
    private lateinit var viewModel: DashboardViewModel
    
    @Before
    fun setup() {
        viewModel = DashboardViewModel(locationUseCases, analyticsUseCases, workManagerHelper)
    }
    
    @Test
    fun `when toggle tracking called, should start tracking if stopped`() = runTest {
        // Given
        whenever(locationUseCases.isTrackingActive()).thenReturn(false)
        whenever(locationUseCases.startLocationTracking()).thenReturn(Unit)
        
        // When
        viewModel.toggleTracking()
        
        // Then
        verify(locationUseCases).startLocationTracking()
        
        // Verify UI state updated
        val uiState = viewModel.uiState.value
        assertThat(uiState.isLoading).isFalse()
    }
    
    @Test
    fun `when refresh data called, should load current stats`() = runTest {
        // Given
        val mockStats = TodayStats(
            totalTimeTracked = 8 * 60 * 60 * 1000L, // 8 hours
            placesVisited = 3,
            visitCount = 5
        )
        whenever(analyticsUseCases.getTodayStats()).thenReturn(mockStats)
        whenever(locationUseCases.isTrackingActive()).thenReturn(true)
        
        // When
        viewModel.refreshData()
        
        // Then
        verify(analyticsUseCases).getTodayStats()
        verify(locationUseCases).isTrackingActive()
        
        val uiState = viewModel.uiState.value
        assertThat(uiState).isInstanceOf(DashboardUiState.Success::class.java)
        val successState = uiState as DashboardUiState.Success
        assertThat(successState.stats.placesVisited).isEqualTo(3)
        assertThat(successState.isTracking).isTrue()
    }
    
    @Test
    fun `when error occurs during refresh, should show error state`() = runTest {
        // Given
        val errorMessage = "Database connection failed"
        whenever(analyticsUseCases.getTodayStats()).thenThrow(RuntimeException(errorMessage))
        
        // When
        viewModel.refreshData()
        
        // Then
        val uiState = viewModel.uiState.value
        assertThat(uiState).isInstanceOf(DashboardUiState.Error::class.java)
        val errorState = uiState as DashboardUiState.Error
        assertThat(errorState.message).contains(errorMessage)
    }
}
```

### Testing Place Detection Algorithm

```kotlin
// File: domain/usecase/PlaceDetectionUseCasesTest.kt
@ExtendWith(MockitoExtension::class)
class PlaceDetectionUseCasesTest {
    
    @Mock lateinit var locationRepository: LocationRepository
    @Mock lateinit var placeRepository: PlaceRepository
    @Mock lateinit var visitRepository: VisitRepository
    @Mock lateinit var preferencesRepository: PreferencesRepository
    @Mock lateinit var errorHandler: ErrorHandler
    @Mock lateinit var validationService: ValidationService
    
    private lateinit var placeDetectionUseCases: PlaceDetectionUseCases
    
    @Before
    fun setup() {
        placeDetectionUseCases = PlaceDetectionUseCases(
            locationRepository,
            placeRepository,
            visitRepository,
            preferencesRepository,
            errorHandler,
            validationService
        )
    }
    
    @Test
    fun `detect places with sufficient home pattern locations`() = runTest {
        // Given - locations showing home pattern (night activity)
        val homeLocations = createHomePatternLocations()
        val preferences = UserPreferences(
            enablePlaceDetection = true,
            clusteringDistanceMeters = 100.0,
            minPointsForCluster = 10
        )
        
        whenever(preferencesRepository.getCurrentPreferences()).thenReturn(preferences)
        whenever(locationRepository.getRecentLocations(any())).thenReturn(flowOf(homeLocations))
        whenever(placeRepository.getPlacesNearLocation(any(), any(), any())).thenReturn(emptyList())
        whenever(placeRepository.insertPlace(any())).thenReturn(1L)
        
        // When
        val detectedPlaces = placeDetectionUseCases.detectNewPlaces()
        
        // Then
        assertThat(detectedPlaces).hasSize(1)
        val homePlace = detectedPlaces.first()
        assertThat(homePlace.category).isEqualTo(PlaceCategory.HOME)
        assertThat(homePlace.confidence).isGreaterThan(0.6f)
        assertThat(homePlace.name).isEqualTo("Home")
        
        verify(placeRepository).insertPlace(any())
    }
    
    @Test
    fun `detect places with work pattern locations`() = runTest {
        // Given - locations showing work pattern (weekday 9-5)
        val workLocations = createWorkPatternLocations()
        val preferences = UserPreferences(enablePlaceDetection = true)
        
        whenever(preferencesRepository.getCurrentPreferences()).thenReturn(preferences)
        whenever(locationRepository.getRecentLocations(any())).thenReturn(flowOf(workLocations))
        whenever(placeRepository.getPlacesNearLocation(any(), any(), any())).thenReturn(emptyList())
        whenever(placeRepository.insertPlace(any())).thenReturn(2L)
        
        // When
        val detectedPlaces = placeDetectionUseCases.detectNewPlaces()
        
        // Then
        assertThat(detectedPlaces).hasSize(1)
        val workPlace = detectedPlaces.first()
        assertThat(workPlace.category).isEqualTo(PlaceCategory.WORK)
        assertThat(workPlace.confidence).isGreaterThan(0.5f)
    }
    
    @Test
    fun `should not detect places when detection disabled`() = runTest {
        // Given
        val locations = createHomePatternLocations()
        val preferences = UserPreferences(enablePlaceDetection = false)
        
        whenever(preferencesRepository.getCurrentPreferences()).thenReturn(preferences)
        
        // When
        val detectedPlaces = placeDetectionUseCases.detectNewPlaces()
        
        // Then
        assertThat(detectedPlaces).isEmpty()
        verify(locationRepository, never()).getRecentLocations(any())
    }
    
    private fun createHomePatternLocations(): List<Location> {
        val baseTime = LocalDateTime.of(2024, 1, 1, 0, 0)
        val homeLatLng = 37.7749 to -122.4194
        
        return (0..20).flatMap { day ->
            // Create night-time locations (home pattern)
            (22..23).map { hour ->
                Location(
                    latitude = homeLatLng.first + (Random.nextDouble() - 0.5) * 0.001, // Small variation
                    longitude = homeLatLng.second + (Random.nextDouble() - 0.5) * 0.001,
                    timestamp = baseTime.plusDays(day.toLong()).plusHours(hour.toLong()),
                    accuracy = 15f + Random.nextFloat() * 10f // 15-25m accuracy
                )
            } + (0..6).map { hour ->
                Location(
                    latitude = homeLatLng.first + (Random.nextDouble() - 0.5) * 0.001,
                    longitude = homeLatLng.second + (Random.nextDouble() - 0.5) * 0.001,
                    timestamp = baseTime.plusDays(day.toLong()).plusHours(hour.toLong()),
                    accuracy = 15f + Random.nextFloat() * 10f
                )
            }
        }
    }
    
    private fun createWorkPatternLocations(): List<Location> {
        val baseTime = LocalDateTime.of(2024, 1, 1, 9, 0) // Start at 9 AM
        val workLatLng = 37.7849 to -122.4094 // Different location from home
        
        return (0..20).flatMap { day ->
            if (baseTime.plusDays(day.toLong()).dayOfWeek.value <= 5) { // Weekdays only
                (9..17).map { hour ->
                    Location(
                        latitude = workLatLng.first + (Random.nextDouble() - 0.5) * 0.001,
                        longitude = workLatLng.second + (Random.nextDouble() - 0.5) * 0.001,
                        timestamp = baseTime.plusDays(day.toLong()).withHour(hour),
                        accuracy = 20f + Random.nextFloat() * 15f // 20-35m accuracy
                    )
                }
            } else {
                emptyList()
            }
        }
    }
}
```

### Testing Database Migrations

```kotlin
// File: data/database/migration/DatabaseMigrationsTest.kt
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationsTest {
    
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VoyagerDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )
    
    @Test
    @Throws(IOException::class)
    fun migrate1To2_contains_correct_data() {
        // Create database version 1
        val db = helper.createDatabase(TEST_DB, 1).apply {
            // Insert test data in version 1 format
            execSQL("INSERT INTO places (id, name, category, latitude, longitude, radius) VALUES (1, 'Test Place', 'HOME', 37.7749, -122.4194, 100.0)")
            execSQL("INSERT INTO visits (id, place_id, entry_time, exit_time, duration) VALUES (1, 1, '2024-01-01T09:00:00', '2024-01-01T17:00:00', 28800000)")
            close()
        }
        
        // Migrate to version 2
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        
        // Verify migration worked correctly
        val placeCursor = migratedDb.query("SELECT confidence FROM places WHERE id = 1")
        placeCursor.moveToFirst()
        assertThat(placeCursor.getFloat(0)).isEqualTo(1.0f) // Default confidence applied
        placeCursor.close()
        
        val visitCursor = migratedDb.query("SELECT confidence FROM visits WHERE id = 1")
        visitCursor.moveToFirst()  
        assertThat(visitCursor.getFloat(0)).isEqualTo(1.0f) // Default confidence applied
        visitCursor.close()
    }
    
    @Test
    @Throws(IOException::class)
    fun migrate2To3_foreign_keys_work() {
        // Create database version 2
        val db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO places (id, name, category, latitude, longitude, radius, confidence) VALUES (1, 'Test Place', 'HOME', 37.7749, -122.4194, 100.0, 0.8)")
            close()
        }
        
        // Migrate to version 3 (adds foreign key constraints)
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        
        // Test foreign key constraint works
        migratedDb.execSQL("INSERT INTO visits (place_id, entry_time, exit_time, duration, confidence) VALUES (1, '2024-01-01T09:00:00', '2024-01-01T17:00:00', 28800000, 0.9)")
        
        // This should fail due to foreign key constraint
        try {
            migratedDb.execSQL("INSERT INTO visits (place_id, entry_time, exit_time, duration, confidence) VALUES (999, '2024-01-01T09:00:00', '2024-01-01T17:00:00', 28800000, 0.9)")
            fail("Should have thrown foreign key constraint exception")
        } catch (e: SQLException) {
            assertThat(e.message).contains("FOREIGN KEY constraint failed")
        }
    }
    
    companion object {
        private const val TEST_DB = "migration-test"
    }
}
```

## 🎨 UI Components Examples

### Creating a Custom Place Card

```kotlin
// File: presentation/components/PlaceCard.kt
@Composable
fun PlaceCard(
    place: Place,
    onPlaceClick: (Place) -> Unit,
    onEditClick: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlaceClick(place) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with icon and name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaceCategoryIcon(
                        category = place.category,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = { onEditClick(place) }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit place"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Category and confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlaceCategoryChip(category = place.category)
                ConfidenceIndicator(confidence = place.confidence)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Statistics
            PlaceStatistics(place = place)
            
            // Address if available
            place.address?.let { address ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaceCategoryChip(category: PlaceCategory) {
    Surface(
        color = getCategoryColor(category),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = category.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun ConfidenceIndicator(confidence: Float) {
    val color = when {
        confidence >= 0.8f -> Color.Green
        confidence >= 0.6f -> Color.Orange
        else -> Color.Red
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Confidence",
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun PlaceStatistics(place: Place) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatisticItem(
            label = "Visits",
            value = place.visitCount.toString(),
            icon = Icons.Default.LocationOn
        )
        
        StatisticItem(
            label = "Time",
            value = formatDuration(place.totalTimeSpent),
            icon = Icons.Default.AccessTime
        )
        
        place.lastVisit?.let { lastVisit ->
            StatisticItem(
                label = "Last",
                value = formatRelativeTime(lastVisit),
                icon = Icons.Default.Schedule
            )
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun getCategoryColor(category: PlaceCategory): Color {
    return when (category) {
        PlaceCategory.HOME -> Color(0xFF4CAF50)      // Green
        PlaceCategory.WORK -> Color(0xFF2196F3)       // Blue
        PlaceCategory.GYM -> Color(0xFFFF5722)        // Deep Orange
        PlaceCategory.RESTAURANT -> Color(0xFFFF9800) // Orange
        PlaceCategory.SHOPPING -> Color(0xFF9C27B0)   // Purple
        PlaceCategory.COFFEE_SHOP -> Color(0xFF795548) // Brown
        else -> Color(0xFF607D8B)                     // Blue Grey
    }
}

// Helper functions
private fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

private fun formatRelativeTime(dateTime: LocalDateTime): String {
    val now = LocalDateTime.now()
    val duration = java.time.Duration.between(dateTime, now)
    
    return when {
        duration.toDays() > 0 -> "${duration.toDays()}d ago"
        duration.toHours() > 0 -> "${duration.toHours()}h ago"
        duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
        else -> "Just now"
    }
}
```

### Custom Settings Section

```kotlin
// File: presentation/screen/settings/components/PlaceDetectionSettingsSection.kt
@Composable
fun PlaceDetectionSettingsSection(
    preferences: UserPreferences,
    onPreferenceChange: (UserPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Place Detection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Switch(
                    checked = preferences.enablePlaceDetection,
                    onCheckedChange = { enabled ->
                        onPreferenceChange(preferences.copy(enablePlaceDetection = enabled))
                    }
                )
            }
            
            if (preferences.enablePlaceDetection) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Detection Frequency
                SettingSlider(
                    label = "Detection Frequency",
                    value = preferences.placeDetectionFrequencyHours.toFloat(),
                    valueRange = 1f..24f,
                    steps = 23,
                    onValueChange = { hours ->
                        onPreferenceChange(
                            preferences.copy(placeDetectionFrequencyHours = hours.toInt())
                        )
                    },
                    valueFormatter = { "${it.toInt()} hours" }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Minimum Accuracy
                SettingSlider(
                    label = "GPS Accuracy Threshold",
                    value = preferences.maxGpsAccuracyMeters,
                    valueRange = 10f..200f,
                    steps = 19,
                    onValueChange = { accuracy ->
                        onPreferenceChange(
                            preferences.copy(maxGpsAccuracyMeters = accuracy)
                        )
                    },
                    valueFormatter = { "${it.toInt()}m" }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Advanced Settings Toggle
                TextButton(
                    onClick = { showAdvancedSettings = !showAdvancedSettings }
                ) {
                    Text("Advanced Settings")
                    Icon(
                        imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvancedSettings) "Hide" else "Show"
                    )
                }
                
                AnimatedVisibility(visible = showAdvancedSettings) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Clustering Distance
                        SettingSlider(
                            label = "Clustering Distance",
                            value = preferences.clusteringDistanceMeters.toFloat(),
                            valueRange = 25f..500f,
                            steps = 19,
                            onValueChange = { distance ->
                                onPreferenceChange(
                                    preferences.copy(clusteringDistanceMeters = distance.toDouble())
                                )
                            },
                            valueFormatter = { "${it.toInt()}m" }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Minimum Points for Cluster
                        SettingSlider(
                            label = "Minimum Points per Place",
                            value = preferences.minPointsForCluster.toFloat(),
                            valueRange = 5f..50f,
                            steps = 9,
                            onValueChange = { points ->
                                onPreferenceChange(
                                    preferences.copy(minPointsForCluster = points.toInt())
                                )
                            },
                            valueFormatter = { "${it.toInt()} points" }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Auto-detection Trigger Count
                        SettingSlider(
                            label = "Auto-detection Trigger",
                            value = preferences.autoDetectTriggerCount.toFloat(),
                            valueRange = 50f..500f,
                            steps = 9,
                            onValueChange = { count ->
                                onPreferenceChange(
                                    preferences.copy(autoDetectTriggerCount = count.toInt())
                                )
                            },
                            valueFormatter = { "${it.toInt()} locations" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
```

## 🔧 Utility Functions

### Location Utility Functions

```kotlin
// File: utils/LocationUtils.kt
object LocationUtils {
    
    /**
     * Calculate distance between two points using Haversine formula
     * @return Distance in meters
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
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
    
    /**
     * Calculate bearing (direction) from one point to another
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        
        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360 // Normalize to 0-360
    }
    
    /**
     * Check if a point is within a circular geofence
     */
    fun isPointInCircle(
        pointLat: Double, pointLon: Double,
        centerLat: Double, centerLon: Double,
        radiusMeters: Double
    ): Boolean {
        val distance = calculateDistance(pointLat, pointLon, centerLat, centerLon)
        return distance <= radiusMeters
    }
    
    /**
     * Calculate the center point of a list of coordinates
     */
    fun calculateCenter(coordinates: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (coordinates.isEmpty()) return 0.0 to 0.0
        
        val avgLat = coordinates.map { it.first }.average()
        val avgLon = coordinates.map { it.second }.average()
        
        return avgLat to avgLon
    }
    
    /**
     * Calculate bounding box for a list of coordinates
     */
    fun calculateBoundingBox(
        coordinates: List<Pair<Double, Double>>
    ): LocationBounds? {
        if (coordinates.isEmpty()) return null
        
        val latitudes = coordinates.map { it.first }
        val longitudes = coordinates.map { it.second }
        
        return LocationBounds(
            north = latitudes.maxOrNull() ?: 0.0,
            south = latitudes.minOrNull() ?: 0.0,
            east = longitudes.maxOrNull() ?: 0.0,
            west = longitudes.minOrNull() ?: 0.0
        )
    }
    
    /**
     * Format coordinates for display
     */
    fun formatCoordinates(lat: Double, lon: Double, precision: Int = 4): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        
        return "${abs(lat).format(precision)}°$latDir, ${abs(lon).format(precision)}°$lonDir"
    }
    
    private fun Double.format(precision: Int): String {
        return "%.${precision}f".format(this)
    }
}

data class LocationBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    val center: Pair<Double, Double>
        get() = (north + south) / 2 to (east + west) / 2
    
    val width: Double
        get() = abs(east - west)
    
    val height: Double
        get() = abs(north - south)
}
```

### Analytics Helper Functions

```kotlin
// File: utils/AnalyticsUtils.kt
object AnalyticsUtils {
    
    /**
     * Calculate daily statistics for a given date
     */
    suspend fun calculateDailyStats(
        date: LocalDate,
        locationRepository: LocationRepository,
        visitRepository: VisitRepository
    ): DayStats {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay()
        
        val locations = locationRepository.getLocationsBetween(startOfDay, endOfDay)
        val visits = visitRepository.getVisitsBetween(startOfDay, endOfDay).first()
        
        val placesVisited = visits.map { it.placeId }.distinct().size
        val totalTimeTracked = visits.sumOf { visit ->
            if (visit.exitTime != null) {
                visit.duration
            } else {
                // Active visit - calculate current duration
                visit.getCurrentDuration()
            }
        }
        
        return DayStats(
            date = date,
            locationCount = locations.size,
            placesVisited = placesVisited,
            visits = visits,
            totalTimeTracked = totalTimeTracked
        )
    }
    
    /**
     * Calculate weekly summary statistics
     */
    suspend fun calculateWeeklyStats(
        startOfWeek: LocalDate,
        locationRepository: LocationRepository,
        visitRepository: VisitRepository,
        placeRepository: PlaceRepository
    ): WeekStats {
        val endOfWeek = startOfWeek.plusDays(7)
        val startDateTime = startOfWeek.atStartOfDay()
        val endDateTime = endOfWeek.atStartOfDay()
        
        val visits = visitRepository.getVisitsBetween(startDateTime, endDateTime).first()
        val uniquePlaceIds = visits.map { it.placeId }.distinct()
        val places = placeRepository.getPlacesByIds(uniquePlaceIds)
        
        val dailyStats = (0..6).map { dayOffset ->
            calculateDailyStats(
                startOfWeek.plusDays(dayOffset.toLong()),
                locationRepository,
                visitRepository
            )
        }
        
        return WeekStats(
            weekStart = startOfWeek,
            dailyStats = dailyStats,
            totalPlacesVisited = places.size,
            totalTimeTracked = dailyStats.sumOf { it.totalTimeTracked },
            mostVisitedPlace = findMostVisitedPlace(visits, places),
            longestVisit = visits.maxByOrNull { it.duration },
            averageDailyPlaces = dailyStats.map { it.placesVisited }.average()
        )
    }
    
    /**
     * Identify patterns in place visits
     */
    fun identifyVisitPatterns(visits: List<Visit>): List<VisitPattern> {
        val patterns = mutableListOf<VisitPattern>()
        
        // Group visits by place and day of week
        val visitsByPlaceAndDay = visits.groupBy { visit ->
            visit.placeId to visit.entryTime.dayOfWeek
        }
        
        visitsByPlaceAndDay.forEach { (placeAndDay, placeVisits) ->
            val (placeId, dayOfWeek) = placeAndDay
            
            if (placeVisits.size >= 3) { // Need at least 3 visits for pattern
                val avgEntryTime = placeVisits.map { 
                    it.entryTime.hour * 60 + it.entryTime.minute 
                }.average()
                
                val avgDuration = placeVisits.map { it.duration }.average()
                
                patterns.add(
                    VisitPattern(
                        placeId = placeId,
                        dayOfWeek = dayOfWeek,
                        averageEntryTime = LocalTime.ofSecondOfDay((avgEntryTime * 60).toLong()),
                        averageDuration = avgDuration.toLong(),
                        frequency = placeVisits.size,
                        confidence = calculatePatternConfidence(placeVisits)
                    )
                )
            }
        }
        
        return patterns.sortedByDescending { it.confidence }
    }
    
    /**
     * Calculate pattern confidence based on consistency
     */
    private fun calculatePatternConfidence(visits: List<Visit>): Float {
        if (visits.size < 2) return 0f
        
        // Calculate time consistency (how consistent are entry times?)
        val entryTimes = visits.map { 
            it.entryTime.hour * 60 + it.entryTime.minute 
        }
        val avgEntryTime = entryTimes.average()
        val timeVariance = entryTimes.map { (it - avgEntryTime).pow(2) }.average()
        val timeConsistency = max(0f, 1f - (timeVariance / (60 * 60)).toFloat()) // Normalize to 0-1
        
        // Calculate duration consistency
        val durations = visits.map { it.duration.toDouble() }
        val avgDuration = durations.average()
        val durationVariance = durations.map { (it - avgDuration).pow(2) }.average()
        val durationConsistency = max(0f, 1f - (durationVariance / avgDuration.pow(2)).toFloat())
        
        // Frequency bonus (more visits = higher confidence)
        val frequencyBonus = min(0.3f, visits.size * 0.05f)
        
        return (timeConsistency * 0.4f + durationConsistency * 0.4f + frequencyBonus).coerceIn(0f, 1f)
    }
    
    private fun findMostVisitedPlace(visits: List<Visit>, places: List<Place>): Place? {
        val visitCounts = visits.groupBy { it.placeId }.mapValues { it.value.size }
        val mostVisitedPlaceId = visitCounts.maxByOrNull { it.value }?.key
        return places.find { it.id == mostVisitedPlaceId }
    }
}

data class WeekStats(
    val weekStart: LocalDate,
    val dailyStats: List<DayStats>,
    val totalPlacesVisited: Int,
    val totalTimeTracked: Long,
    val mostVisitedPlace: Place?,
    val longestVisit: Visit?,
    val averageDailyPlaces: Double
)

data class VisitPattern(
    val placeId: Long,
    val dayOfWeek: DayOfWeek,
    val averageEntryTime: LocalTime,
    val averageDuration: Long,
    val frequency: Int,
    val confidence: Float
)
```

---

*This code examples guide provides practical implementations you can directly use and modify for your Voyager development needs.*