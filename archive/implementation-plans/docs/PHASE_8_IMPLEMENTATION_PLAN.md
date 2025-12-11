# Phase 8: Performance & Battery Optimization - Comprehensive Implementation Plan

## Executive Summary
Phase 8 focuses on implementing intelligent battery optimization through sleep schedule management, motion detection, and dynamic location update intervals. The plan leverages existing architecture while minimizing changes to proven components.

---

## 1. CURRENT LOCATION TRACKING IMPLEMENTATION

### Current Architecture
**File:** `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/data/service/LocationTrackingService.kt`

#### Key Observations:
- **Location Update Intervals:**
  - Default: 30 seconds (`locationUpdateIntervalMs: Long = 30000L`)
  - Power Save Mode: 60+ seconds (2x multiplier when stationary)
  - High Accuracy Mode: 10-15 seconds (0.5x multiplier)
  - Stationary Mode: Doubles the interval

- **Stationary Detection:**
  - Already implemented (lines 513-539)
  - Threshold: 5 minutes inactivity + 20m movement detection
  - Automatically switches location request priority (BALANCED when stationary)
  - Updates location request in real-time (lines 544-575)

- **Battery Optimizations Already in Place:**
  - Adaptive distance thresholds based on accuracy mode
  - Time-based filtering with adaptive intervals
  - GPS accuracy filtering (max 100m default)
  - Speed validation to filter impossible speeds
  - Stationary mode with reduced frequency
  - Smart location filtering to prevent spam (lines 432-508)

#### Location Filtering Logic:
```
shouldSaveLocation() uses:
  1. Accuracy filtering: 100m max when stationary, user-configurable
  2. Distance-based: 10m minimum movement
  3. Time-based: Respect minTimeBetweenUpdatesSeconds
  4. Speed validation: Max 200 km/h (filters GPS errors)
  5. Adaptive thresholds when stationary (more lenient)
```

#### Interval Configuration:
```
createLocationRequest() (lines 811-846):
  - Priority selection based on mode and accuracy preference
  - Wait for accurate locations (setWaitForAccurateLocation(true))
  - Min update interval: 15 seconds (safety minimum)
  - Max update delay: 2x the requested interval
  - Min update distance: 15m when stationary
```

#### Integration Points for Phase 8:
1. **Sleep Schedule:** Add to condition in `shouldSaveLocation()` (line 432)
2. **Motion Detection:** Enhance `updateStationaryMode()` (line 513)
3. **Auto-pause:** Modify `saveLocation()` (line 349) to check sleep window
4. **Settings UI:** Add sleep schedule selectors to SettingsScreen

---

## 2. REPOSITORY & PREFERENCES STRUCTURE

### PreferencesRepository Interface
**File:** `/home/anshul/AndroidStudioProjects/Voyager/domain/repository/PreferencesRepository.kt`

#### Current Methods:
- `getUserPreferences()` - Flow for reactive updates
- `updateUserPreferences()` - Bulk update
- Specific update methods for individual settings

#### What Needs to be Added:
```kotlin
// New methods for Phase 8:
suspend fun updateSleepSchedule(startHour: Int, endHour: Int)
suspend fun updateEnableSleepMode(enabled: Boolean)
suspend fun updateMotionSensitivityThreshold(threshold: Float)
suspend fun updateEnableMotionDetection(enabled: Boolean)
suspend fun updateBatteryOptimizationLevel(level: BatteryOptimizationLevel)
```

#### New PreferencesRepository Implementation Points:
- DataStore/SharedPreferences: Need to add sleep schedule fields
- Validation: Hour range (0-23), enable flag
- Default values: 22:00-06:00 sleep window

### VisitRepository
**File:** `/home/anshul/AndroidStudioProjects/Voyager/domain/repository/VisitRepository.kt`

#### Current Methods for Phase 8 Integration:
```kotlin
fun getVisitsForPlace(placeId: Long): Flow<List<Visit>>
suspend fun getVisitCountForPlace(placeId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Int
suspend fun getTotalTimeAtPlace(placeId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Long
```

#### Recommended New Method:
```kotlin
suspend fun getHomeVisits(startDate: LocalDateTime, endDate: LocalDateTime): List<Visit>
```
- Query visits to places categorized as "HOME"
- Analyze visit patterns to suggest sleep schedules
- Could filter by category from Place.category

#### Category Query Logic:
- Need to JOIN with PlaceEntity in DAO layer
- Filter by `PlaceCategory.HOME`
- Return visits with timing information

---

## 3. EXISTING SERVICES & HELPERS

### WorkManagerHelper
**File:** `/home/anshul/AndroidStudioProjects/Voyager/utils/WorkManagerHelper.kt`

#### Current Capabilities:
- `enqueuePlaceDetectionWork()` - One-time and periodic place detection
- `scheduleDailySummary()` - Daily notifications at specific hour
- `getWorkStatus()` - Monitor worker status
- `cancelAllWorkers()` - Stop all background work

#### Phase 8 Integration Points:
```kotlin
// New methods to add:
suspend fun scheduleSleepModeWorker(startHour: Int, endHour: Int): Boolean
suspend fun scheduleMotionDetectionWork(): Boolean
suspend fun cancelSleepModeWorker(): Boolean
```

#### Characteristics Already Available:
- Retry logic with exponential backoff (5 attempts)
- Fallback worker support if main fails
- Constraint-based scheduling (battery, network, etc.)
- Health check capability

### LocationTrackingService Integration
**Files:** 
- LocationTrackingService.kt (main location service)
- SmartDataProcessor.kt (processes locations)

#### Service Dependencies:
```kotlin
@Inject lateinit var preferencesRepository: PreferencesRepository
@Inject lateinit var workManagerHelper: WorkManagerHelper
@Inject lateinit var locationRepository: LocationRepository
// All available for Phase 8 use
```

#### Key Service Methods for Phase 8:
1. `saveLocation()` - Add sleep schedule check
2. `shouldSaveLocation()` - Add sleep mode filtering
3. `updateStationaryMode()` - Enhance with motion detection
4. `createLocationRequest()` - Can be modified for sleep times

---

## 4. UI SETTINGS SCREEN

### SettingsScreen.kt
**File:** `/home/anshul/AndroidStudioProjects/Voyager/presentation/screen/settings/SettingsScreen.kt`

#### Current Structure:
- Uses LazyColumn for organized sections
- Sections include: Data Stats, Location Tracking, Data Management, Tracking Settings, Place Detection, etc.
- Pattern: Each section has a composable component imported
- ViewModel patterns: `hiltViewModel()` dependency injection

#### Current Components Pattern:
```kotlin
item {
    TrackingSettingsSection(
        preferences = uiState.preferences,
        onUpdateLocationInterval = viewModel::updateLocationUpdateInterval,
        // ... more callbacks
    )
}
```

#### Phase 8 UI Integration:
```kotlin
item {
    Spacer(modifier = Modifier.height(16.dp))
    SleepScheduleSection(
        preferences = uiState.preferences,
        onUpdateSleepStart = viewModel::updateSleepStartHour,
        onUpdateSleepEnd = viewModel::updateSleepEndHour,
        onUpdateEnableSleep = viewModel::updateEnableSleepMode,
        onUpdateMotionDetection = viewModel::updateEnableMotionDetection
    )
}
```

#### New Component Needed:
- `SleepScheduleSection.kt` - Time pickers for start/end, toggle for sleep mode
- Similar to existing `TrackingSettingsSection.kt` in style

### SettingsViewModel.kt
**File:** `/home/anshul/AndroidStudioProjects/Voyager/presentation/screen/settings/SettingsViewModel.kt`

#### State Management Pattern:
```kotlin
data class SettingsUiState(
    // ... existing fields
    val preferences: UserPreferences = UserPreferences()
)
```

#### Current Update Methods Pattern:
```kotlin
fun updateLocationUpdateInterval(intervalMs: Long) {
    viewModelScope.launch {
        preferencesRepository.updateLocationUpdateInterval(intervalMs)
    }
}
```

#### Phase 8 ViewModel Methods to Add:
```kotlin
fun updateSleepStartHour(hour: Int) {
    viewModelScope.launch {
        preferencesRepository.updateSleepStartHour(hour)
    }
}

fun updateSleepEndHour(hour: Int) {
    viewModelScope.launch {
        preferencesRepository.updateSleepEndHour(hour)
    }
}

fun updateEnableSleepMode(enabled: Boolean) {
    viewModelScope.launch {
        preferencesRepository.updateEnableSleepMode(enabled)
    }
}
```

---

## 5. USER PREFERENCES MODEL

### UserPreferences.kt
**File:** `/home/anshul/AndroidStudioProjects/Voyager/domain/model/UserPreferences.kt`

#### Current Fields Relevant to Phase 8:
```kotlin
val batteryRequirement: BatteryRequirement = BatteryRequirement.ANY
val activityTimeRangeStart: Int = 6  // 0-23 hours
val activityTimeRangeEnd: Int = 23   // 0-23 hours
val enableLocationHistory: Boolean = true
```

#### New Fields to Add:
```kotlin
// Sleep Schedule Settings
val sleepModeEnabled: Boolean = false
val sleepStartHour: Int = 22          // 10 PM
val sleepEndHour: Int = 6             // 6 AM
val sleepModeStrictness: SleepModeStrictness = SleepModeStrictness.NORMAL

// Motion Detection Settings
val motionDetectionEnabled: Boolean = true
val motionSensitivityThreshold: Float = 0.5f  // 0.0-1.0 scale
val useAccelerometerForDetection: Boolean = true

// Battery Optimization
val batteryOptimizationLevel: BatteryOptimizationLevel = BatteryOptimizationLevel.BALANCED
val reduceFrequencyBelowBatteryPercent: Int = 20  // Below 20% battery
val autoReduceFrequencyBelowPercent: Boolean = true
```

#### New Enums to Add:
```kotlin
enum class SleepModeStrictness {
    RELAXED,      // Pause tracking but listen for motion
    NORMAL,       // Don't track unless motion detected
    STRICT        // Pause completely, disable all background work
}

enum class BatteryOptimizationLevel {
    MINIMAL,      // No optimization
    BALANCED,     // Smart stationary detection + reasonable intervals
    AGGRESSIVE    // Reduce updates 3-4x, less accurate
}
```

#### Validation in `validated()` function:
```kotlin
sleepStartHour = sleepStartHour.coerceIn(0, 23),
sleepEndHour = sleepEndHour.coerceIn(0, 23),
motionSensitivityThreshold = motionSensitivityThreshold.coerceIn(0.0f, 1.0f),
```

---

## 6. DEPENDENCIES & ARCHITECTURE PATTERNS

### Hilt Dependency Injection
**Current Usage Pattern:**
```kotlin
@Singleton
class WorkManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    // ...
)

@AndroidEntryPoint
class LocationTrackingService : Service() {
    @Inject lateinit var preferencesRepository: PreferencesRepository
}
```

#### For Phase 8 - New Services Needed:
```kotlin
// MotionDetectionService.kt - Uses SensorManager
@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val logger: ProductionLogger
) {
    // Will need SensorManager from context.getSystemService()
}

// SleepScheduleManager.kt - Uses PreferencesRepository
@Singleton
class SleepScheduleManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val logger: ProductionLogger
) {
    // Pure logic, no Android dependencies needed
}
```

### Coroutines & Flow Usage
**Current Pattern:**
```kotlin
fun getUserPreferences(): Flow<UserPreferences>

private fun observePreferences() {
    viewModelScope.launch {
        preferencesRepository.getUserPreferences().collect { preferences ->
            _uiState.value = _uiState.value.copy(preferences = preferences)
        }
    }
}
```

#### Phase 8 Usage:
- Use Flow for preference reactive updates
- Use suspend functions in repositories
- ViewModel handles collection and state updates
- Service uses coroutines for background work

### Motion Sensors (NOT Currently Used)
**Availability:** Android system service available but not imported
```kotlin
// Phase 8 will need to add:
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

// In MotionDetectionManager:
val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
```

### Permissions Already Available
**From AndroidManifest.xml:**
```xml
<!-- Already have everything needed -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- Sensor access is implicit, no permission needed for accelerometer -->
```

---

## 7. IMPLEMENTATION APPROACH & RECOMMENDATIONS

### Architecture Decisions

#### A. Sleep Schedule Approach
**Recommended: Time-based Pause in LocationTrackingService**

Rationale:
- Non-intrusive modification to existing service
- Respects user preferences from PreferencesRepository
- Minimal impact on existing code
- Can be toggled at runtime

Implementation:
```kotlin
// In LocationTrackingService.saveLocation()
private suspend fun saveLocation(androidLocation: AndroidLocation) {
    if (isPaused || isInSleepWindow()) return  // Add this check
    // ... rest of saveLocation
}

private fun isInSleepWindow(): Boolean {
    val prefs = currentPreferences ?: return false
    if (!prefs.sleepModeEnabled) return false
    
    val now = LocalDateTime.now().hour
    return if (prefs.sleepStartHour < prefs.sleepEndHour) {
        now >= prefs.sleepStartHour && now < prefs.sleepEndHour
    } else {
        now >= prefs.sleepStartHour || now < prefs.sleepEndHour
    }
}
```

#### B. Motion Detection Approach
**Recommended: Separate Manager with Lazy Sensor Initialization**

Rationale:
- Can be disabled without affecting service
- Sensor access is expensive (battery), should be optional
- Easy to test independently
- Doesn't require WorkManager

Implementation Plan:
```kotlin
// MotionDetectionManager.kt
@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var listener: SensorEventListener? = null
    private var isListening = false
    
    suspend fun initialize() {
        if (!preferencesRepository.getCurrentPreferences().motionDetectionEnabled) {
            return
        }
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    fun startListening(callback: (isMotion: Boolean) -> Unit) {
        // Only start if initialized and enabled
        // Use low power mode for sensors
    }
    
    fun stopListening() {
        // Cleanup sensors when not needed
    }
    
    fun isMotionDetected(): Boolean {
        // Check recent motion data
    }
}
```

#### C. Sleep Schedule Manager Approach
**Recommended: Pure Logic Service**

```kotlin
// SleepScheduleManager.kt
@Singleton
class SleepScheduleManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun isInSleepWindow(): Boolean {
        val prefs = preferencesRepository.getCurrentPreferences()
        if (!prefs.sleepModeEnabled) return false
        
        val now = LocalDateTime.now().hour
        return isInRange(now, prefs.sleepStartHour, prefs.sleepEndHour)
    }
    
    private fun isInRange(current: Int, start: Int, end: Int): Boolean {
        return if (start < end) {
            current >= start && current < end
        } else {
            current >= start || current < end
        }
    }
    
    suspend fun getSuggestedSleepSchedule(): Pair<Int, Int> {
        // Analyze home visit patterns
        // Return (startHour, endHour)
    }
}
```

### Integration Points - Minimal Changes Required

#### 1. LocationTrackingService.kt
**Changes Needed:**
```kotlin
// Add one field
@Inject
lateinit var sleepScheduleManager: SleepScheduleManager

// Add one condition in saveLocation()
if (isPaused || sleepScheduleManager.isInSleepWindow()) return

// Add one condition in shouldSaveLocation()
val isSleeping = sleepScheduleManager.isInSleepWindow()
return when {
    isSleeping && !prefs.motionDetectionEnabled -> false
    // ... rest of conditions
}
```

#### 2. UserPreferences.kt
**Changes Needed:**
- Add 6-8 new fields (sleep hours, motion detection settings)
- Add validation in `validated()` function
- Add 2 new enums (SleepModeStrictness, BatteryOptimizationLevel)

#### 3. PreferencesRepository.kt (Interface)
**Changes Needed:**
- Add 4 new suspend methods for sleep/motion settings
- Other methods remain unchanged

#### 4. PreferencesRepository Implementation (DataStore)
**Changes Needed:**
- Add fields to data class
- Implement 4 new update methods
- Add serialization for new fields

#### 5. SettingsScreen.kt & SettingsViewModel.kt
**Changes Needed:**
- Add `SleepScheduleSection` component to UI
- Add 4 new viewModel methods (follow existing pattern)
- No changes to existing code

#### 6. DI Modules
**Changes Needed:**
- Add provider for SleepScheduleManager in UtilsModule
- Optionally add provider for MotionDetectionManager

---

## 8. IMPLEMENTATION ROADMAP

### Phase 8.1: Foundation (3-4 days)
1. Add new fields to UserPreferences.kt
2. Create SleepScheduleManager.kt
3. Implement PreferencesRepository methods for sleep settings
4. Add DI bindings

### Phase 8.2: Integration (2-3 days)
1. Integrate SleepScheduleManager into LocationTrackingService
2. Add sleep window checks to saveLocation() and shouldSaveLocation()
3. Test with manual hour adjustments

### Phase 8.3: UI & Settings (2-3 days)
1. Create SleepScheduleSection composable component
2. Add callbacks to SettingsViewModel
3. Integrate into SettingsScreen
4. User testing with time picker

### Phase 8.4: Motion Detection (3-4 days)
1. Create MotionDetectionManager.kt
2. Implement SensorManager integration
3. Add to LocationTrackingService conditions
4. Test with different sensitivity levels

### Phase 8.5: Analytics & Polish (2-3 days)
1. Add sleep pattern analysis
2. Implement schedule suggestion feature
3. Battery usage analytics
4. Final testing and optimization

---

## 9. POTENTIAL ISSUES & MITIGATIONS

### Issue 1: Sensor Battery Drain
**Risk:** Accelerometer always listening causes excessive battery drain
**Mitigation:**
- Register listener only when needed
- Use SensorManager.SENSOR_DELAY_NORMAL (not SENSOR_DELAY_FASTEST)
- Unregister when app goes to background
- Add battery requirement check (don't use if battery < 15%)

### Issue 2: Sleep Schedule Logic with Midnight Crossing
**Risk:** When startHour > endHour (e.g., 22:00-06:00), logic is complex
**Mitigation:**
- Implement helper function `isInRange()` that handles midnight
- Add unit tests for edge cases (23:55, 00:05, etc.)
- Document behavior clearly in comments

### Issue 3: Preferences Update Race Conditions
**Risk:** User changes sleep schedule while service is running
**Mitigation:**
- Already handled: Service observes preferences changes via Flow
- Preferences update triggers service restart (existing code)
- No additional changes needed

### Issue 4: Motion Detection on Phones Without Accelerometer
**Risk:** Some devices might not have accelerometer
**Mitigation:**
- Check `sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null`
- Gracefully disable motion detection if unavailable
- Fall back to time-only sleep scheduling

### Issue 5: WorkManager vs Direct Implementation
**Risk:** Do we need WorkManager for sleep scheduling?
**Recommendation:** NO - Use direct location service approach
- Sleep check is just a flag in existing saveLocation loop
- No background work needed
- Simpler than WorkManager approach
- Existing service already running in foreground

---

## 10. TESTING STRATEGY

### Unit Tests
```kotlin
// Test SleepScheduleManager
fun testIsInSleepWindow_Morning() { }
fun testIsInSleepWindow_Midnight() { }
fun testIsInSleepWindow_Disabled() { }

// Test motion detection logic
fun testMotionDetectionThreshold() { }
```

### Integration Tests
```kotlin
// Test with actual preferences
fun testLocationTracking_SleepMode_Pauses()
fun testLocationTracking_SleepMode_OnMotion_Resumes()
```

### Manual Testing
1. Set sleep schedule to current hour ± 1
2. Verify locations not saved during sleep window
3. Test motion detection triggering
4. Test time picker in settings
5. Test persistence across app restart

---

## 11. FILE LOCATIONS SUMMARY

### Core Files to Modify
1. **Domain Model:** `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/domain/model/UserPreferences.kt`
   - Add sleep/motion fields and enums

2. **Repository Interface:** `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/domain/repository/PreferencesRepository.kt`
   - Add new suspend functions

3. **Service:** `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/data/service/LocationTrackingService.kt`
   - Add sleep manager injection
   - Add sleep window check

4. **ViewModel:** `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/settings/SettingsViewModel.kt`
   - Add sleep/motion update methods

5. **UI Screen:** `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/settings/SettingsScreen.kt`
   - Add sleep schedule section

### New Files to Create
1. `SleepScheduleManager.kt` - in utils package
2. `MotionDetectionManager.kt` - in utils package
3. `SleepScheduleSection.kt` - in settings components
4. (Optional) PreferencesRepository implementation class

### DI Modifications
- `/home/anshul/AndroidStudioProjects/Voyager/app/src/main/java/com/cosmiclaboratory/voyager/di/UtilsModule.kt`
  - Add providers for new managers

---

## 12. ARCHITECTURE DIAGRAM

```
┌─────────────────────────────────────────────────────────────────┐
│                    SettingsScreen.kt                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  SleepScheduleSection (NEW)                             │   │
│  │  - Time picker for start/end                            │   │
│  │  - Toggle sleep mode                                    │   │
│  │  - Motion detection checkbox                            │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────┬─────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│            SettingsViewModel.kt (MODIFIED)                      │
│  - updateSleepStartHour()                                       │
│  - updateSleepEndHour()                                         │
│  - updateEnableSleepMode()                                      │
│  - updateMotionDetectionEnabled()                               │
└────────────────┬─────────────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────┬──────────────────────────────────────┐
│ PreferencesRepository    │                                      │
│ (MODIFIED)               │ UserPreferences (MODIFIED)           │
│ ────────────────────     │ ──────────────────────────           │
│ updateSleepStartHour()   │ - sleepModeEnabled: Boolean          │
│ updateSleepEndHour()     │ - sleepStartHour: Int                │
│ updateEnableSleepMode()  │ - sleepEndHour: Int                  │
│ updateMotionDetection()  │ - motionDetectionEnabled: Boolean    │
└──────────────────────────┴──────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  LocationTrackingService.kt (MODIFIED)                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ @Inject sleepScheduleManager: SleepScheduleManager     │   │
│  │                                                         │   │
│  │ private fun isInSleepWindow() { ... }                  │   │
│  │ private fun saveLocation() {                            │   │
│  │   if (isPaused || isInSleepWindow()) return             │   │
│  │   ...                                                   │   │
│  │ }                                                       │   │
│  └─────────────────────────────────────────────────────────┘   │
└──────────┬─────────────────────────────┬──────────────────────────┘
           │                             │
           ▼                             ▼
┌──────────────────────────┐   ┌──────────────────────────┐
│ SleepScheduleManager     │   │ MotionDetectionManager   │
│ (NEW - Pure Logic)       │   │ (NEW - Sensor Handling)  │
│ ──────────────────────   │   │ ─────────────────────    │
│ isInSleepWindow()        │   │ startListening()         │
│ getSuggestedSchedule()   │   │ stopListening()          │
│                          │   │ isMotionDetected()       │
└──────────────────────────┘   └──────────────────────────┘
                                        │
                                        ▼
                        ┌──────────────────────────────┐
                        │ Android SensorManager (system)│
                        │ - TYPE_ACCELEROMETER         │
                        │ - TYPE_GYROSCOPE (optional)  │
                        └──────────────────────────────┘
```

---

## 13. CONCLUSION

Phase 8 implementation requires:
- **5 files to modify** (UserPreferences, PreferencesRepository, LocationTrackingService, SettingsViewModel, SettingsScreen)
- **2 new manager services** (SleepScheduleManager, MotionDetectionManager)
- **1 new UI component** (SleepScheduleSection)
- **1 DI module update** (UtilsModule)
- **Minimal changes to existing logic** - Mostly additions and conditional checks

The approach is conservative, leveraging existing architecture patterns (Hilt, Flow, ViewModel, Coroutines) while adding new capabilities in isolated, testable components.

