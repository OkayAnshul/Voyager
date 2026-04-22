# Phase 8: Quick Reference Guide

## What is Phase 8?
Battery optimization and sleep schedule management for location tracking. Enable users to define sleep hours and reduce location tracking during those times.

## Key Files at a Glance

| File | Location | What It Does |
|------|----------|-------------|
| **LocationTrackingService.kt** | `data/service/` | Main location tracking - add sleep check here |
| **UserPreferences.kt** | `domain/model/` | User settings model - add sleep/motion fields |
| **PreferencesRepository.kt** | `domain/repository/` | Interface for preference updates - add methods |
| **SettingsScreen.kt** | `presentation/screen/settings/` | Settings UI - add sleep schedule section |
| **SettingsViewModel.kt** | `presentation/screen/settings/` | Settings logic - add update methods |
| **SleepScheduleManager.kt** | `utils/` | NEW - Core sleep logic |
| **MotionDetectionManager.kt** | `utils/` | NEW - Motion sensor handling |
| **SleepScheduleSection.kt** | `presentation/screen/settings/components/` | NEW - Sleep UI component |

## Current Battery Features Already in Place
- Stationary detection (reduces updates when not moving)
- Accuracy-based filtering (ignores inaccurate GPS)
- Speed validation (filters impossible speeds)
- Interval adaptation based on accuracy mode
- Time-based location filtering

## What Phase 8 Adds
1. **Sleep Schedule** - Pause tracking during user-defined sleep hours
2. **Motion Detection** - Resume tracking if motion detected during sleep
3. **Battery Optimization** - Adaptive update frequency based on battery level
4. **Smart Scheduling** - Suggest sleep times based on location patterns

## Integration Points (Minimal Changes)

### LocationTrackingService.kt
```kotlin
// Add field
@Inject lateinit var sleepScheduleManager: SleepScheduleManager

// Modify saveLocation() - add 1 condition
if (isPaused || sleepScheduleManager.isInSleepWindow()) return

// Modify shouldSaveLocation() - check sleep before filtering
val isSleeping = sleepScheduleManager.isInSleepWindow()
if (isSleeping && !prefs.motionDetectionEnabled) return false
```

### UserPreferences.kt
Add these fields:
```kotlin
val sleepModeEnabled: Boolean = false
val sleepStartHour: Int = 22
val sleepEndHour: Int = 6
val motionDetectionEnabled: Boolean = true
val motionSensitivityThreshold: Float = 0.5f
val batteryOptimizationLevel: BatteryOptimizationLevel = BatteryOptimizationLevel.BALANCED
```

Add these enums:
```kotlin
enum class SleepModeStrictness {
    RELAXED,      // Pause tracking but listen for motion
    NORMAL,       // Don't track unless motion detected
    STRICT        // Pause completely
}

enum class BatteryOptimizationLevel {
    MINIMAL,
    BALANCED,
    AGGRESSIVE
}
```

### PreferencesRepository.kt
Add these methods:
```kotlin
suspend fun updateSleepStartHour(hour: Int)
suspend fun updateSleepEndHour(hour: Int)
suspend fun updateEnableSleepMode(enabled: Boolean)
suspend fun updateEnableMotionDetection(enabled: Boolean)
```

### SettingsViewModel.kt
Add these methods (follow existing pattern):
```kotlin
fun updateSleepStartHour(hour: Int)
fun updateSleepEndHour(hour: Int)
fun updateEnableSleepMode(enabled: Boolean)
fun updateEnableMotionDetection(enabled: Boolean)
```

### SettingsScreen.kt
Add this section in LazyColumn:
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

## New Files to Create

### 1. SleepScheduleManager.kt
Pure logic service for sleep scheduling:
```kotlin
@Singleton
class SleepScheduleManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun isInSleepWindow(): Boolean { ... }
    private fun isInRange(current: Int, start: Int, end: Int): Boolean { ... }
    suspend fun getSuggestedSleepSchedule(): Pair<Int, Int> { ... }
}
```

### 2. MotionDetectionManager.kt
Sensor-based motion detection:
```kotlin
@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun initialize() { ... }
    fun startListening(callback: (Boolean) -> Unit) { ... }
    fun stopListening() { ... }
    fun isMotionDetected(): Boolean { ... }
}
```

### 3. SleepScheduleSection.kt
Composable UI component with:
- Time picker for sleep start hour (0-23)
- Time picker for sleep end hour (0-23)
- Toggle for sleep mode enabled
- Toggle for motion detection enabled

## Android Dependencies Already Available

```
Coroutines: kotlinx.coroutines
DI: Hilt (dagger.hilt)
Database: Room
Location: Google Play Services Location
Sensors: Android system services (no import needed)
Flow: kotlinx.coroutines.flow
```

## Manifest Permissions

All permissions already declared:
- ACCESS_FINE_LOCATION ✓
- ACCESS_BACKGROUND_LOCATION ✓
- WAKE_LOCK ✓
- POST_NOTIFICATIONS ✓
- Sensor access (implicit, no permission needed)

## Testing Checklist

- [ ] Sleep schedule: Test with current hour within sleep window
- [ ] Midnight crossing: Test with sleep start (e.g., 22:00) > end (e.g., 6:00)
- [ ] Motion detection: Test with sensor input simulation
- [ ] Preferences persistence: Test after app restart
- [ ] UI time pickers: Test all hour values (0-23)
- [ ] Interaction: Toggle sleep on/off during tracking
- [ ] Battery drain: Monitor sensor usage with profiler

## Common Patterns in This Project

### Hilt Injection
```kotlin
@Inject
lateinit var dependency: SomeService
```

### Repository Updates
```kotlin
viewModelScope.launch {
    preferencesRepository.updateSetting(value)
}
```

### Flow Observation
```kotlin
viewModelScope.launch {
    repository.getSomeFlow().collect { value ->
        _state.value = _state.value.copy(field = value)
    }
}
```

### Settings Section Component
```kotlin
@Composable
fun MySectionComponent(
    preferences: UserPreferences,
    onUpdate: (value: Type) -> Unit
) {
    Card { /* UI here */ }
}
```

## Potential Pitfalls

1. **Midnight Crossing Logic** - When sleep start > end (10 PM - 6 AM)
   - Solution: Use `isInRange()` helper that handles wrap-around

2. **Sensor Battery Drain** - Accelerometer always listening
   - Solution: Register/unregister listeners based on preferences
   - Use SENSOR_DELAY_NORMAL, not SENSOR_DELAY_FASTEST

3. **Race Conditions** - User changes sleep schedule while tracking
   - Solution: Already handled - service observes preferences via Flow

4. **Missing Accelerometer** - Some devices don't have it
   - Solution: Check `sensorManager.getDefaultSensor() != null`

5. **Concurrent Updates** - Multiple sleep schedule changes at once
   - Solution: PreferencesRepository handles via DataStore atomicity

## Implementation Timeline Estimate

- **Foundation** (3-4 days): Models, repositories, DI
- **Integration** (2-3 days): Connect to LocationTrackingService
- **UI** (2-3 days): Settings screen components
- **Motion Detection** (3-4 days): Sensor integration
- **Testing & Polish** (2-3 days): Complete testing, edge cases
- **Total: 12-17 days**

## Success Criteria

1. Users can set sleep schedule (e.g., 22:00-06:00)
2. Location tracking pauses during sleep hours
3. Motion detection can wake up tracking
4. Settings persist across app restarts
5. No battery drain from sensors during sleep
6. Time picker properly handles all 24 hours

## References

- **Full Plan:** See PHASE_8_IMPLEMENTATION_PLAN.md
- **Service Code:** LocationTrackingService.kt (stationary detection example)
- **Settings Pattern:** SettingsScreen.kt existing components
- **DI Pattern:** di/UtilsModule.kt
- **Preferences Pattern:** PreferencesRepository interface and implementations

---
**Next Step:** Start with Phase 8.1 (Foundation) - Create UserPreferences fields and SleepScheduleManager
