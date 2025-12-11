# Phase 8: Code Implementation Snippets

## Quick Copy-Paste Code for Phase 8

### 1. UserPreferences.kt - New Fields to Add

Add these fields to the `data class UserPreferences(...)`:

```kotlin
// Sleep Schedule Settings
val sleepModeEnabled: Boolean = false,
val sleepStartHour: Int = 22,          // 10 PM default
val sleepEndHour: Int = 6,             // 6 AM default
val sleepModeStrictness: SleepModeStrictness = SleepModeStrictness.NORMAL,

// Motion Detection Settings
val motionDetectionEnabled: Boolean = true,
val motionSensitivityThreshold: Float = 0.5f,  // 0.0-1.0 scale
val useAccelerometerForDetection: Boolean = true,

// Battery Optimization
val batteryOptimizationLevel: BatteryOptimizationLevel = BatteryOptimizationLevel.BALANCED,
val reduceFrequencyBelowBatteryPercent: Int = 20,
val autoReduceFrequencyBelowPercent: Boolean = true,
```

Add these enums after the data class:

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

Add to the `validated()` function:

```kotlin
sleepStartHour = sleepStartHour.coerceIn(0, 23),
sleepEndHour = sleepEndHour.coerceIn(0, 23),
motionSensitivityThreshold = motionSensitivityThreshold.coerceIn(0.0f, 1.0f),
reduceFrequencyBelowBatteryPercent = reduceFrequencyBelowBatteryPercent.coerceIn(5, 50),
```

---

### 2. PreferencesRepository.kt - New Methods to Add

Add these method signatures to the interface:

```kotlin
suspend fun updateSleepStartHour(hour: Int)
suspend fun updateSleepEndHour(hour: Int)
suspend fun updateEnableSleepMode(enabled: Boolean)
suspend fun updateEnableMotionDetection(enabled: Boolean)
```

---

### 3. SleepScheduleManager.kt - New File to Create

Create new file: `app/src/main/java/com/cosmiclaboratory/voyager/utils/SleepScheduleManager.kt`

```kotlin
package com.cosmiclaboratory.voyager.utils

import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.util.Log
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDateTime

/**
 * Manages sleep schedule logic for location tracking pause
 * Implements intelligent sleep window detection with midnight crossing support
 */
@Singleton
class SleepScheduleManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val logger: ProductionLogger
) {
    companion object {
        private const val TAG = "SleepScheduleManager"
    }

    /**
     * Check if current time falls within the user's configured sleep window
     */
    suspend fun isInSleepWindow(): Boolean {
        try {
            val prefs = preferencesRepository.getCurrentPreferences()
            if (!prefs.sleepModeEnabled) {
                return false
            }

            val now = LocalDateTime.now().hour
            return isInRange(now, prefs.sleepStartHour, prefs.sleepEndHour)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sleep window", e)
            return false // Default to awake on error
        }
    }

    /**
     * Helper function to check if current hour is within range
     * Handles midnight crossing (e.g., 22:00-06:00)
     *
     * @param current Current hour (0-23)
     * @param start Sleep start hour (0-23)
     * @param end Sleep end hour (0-23)
     * @return true if current is within sleep window
     */
    private fun isInRange(current: Int, start: Int, end: Int): Boolean {
        return if (start < end) {
            // Normal range: 6:00-22:00
            current >= start && current < end
        } else {
            // Midnight crossing: 22:00-06:00
            current >= start || current < end
        }
    }

    /**
     * Analyze home visit patterns to suggest optimal sleep schedule
     * (Future enhancement - returns sensible defaults for now)
     */
    suspend fun getSuggestedSleepSchedule(): Pair<Int, Int> {
        return Pair(22, 6) // 10 PM to 6 AM
    }

    /**
     * Get sleep window as human-readable string
     */
    suspend fun getSleepWindowString(): String {
        try {
            val prefs = preferencesRepository.getCurrentPreferences()
            val startStr = String.format("%02d:00", prefs.sleepStartHour)
            val endStr = String.format("%02d:00", prefs.sleepEndHour)
            return "$startStr - $endStr"
        } catch (e: Exception) {
            return "22:00 - 06:00" // Default
        }
    }

    /**
     * Get remaining time until sleep window
     */
    suspend fun getMinutesUntilSleep(): Int {
        try {
            val prefs = preferencesRepository.getCurrentPreferences()
            if (!prefs.sleepModeEnabled) return -1

            val now = LocalDateTime.now()
            val currentHour = now.hour
            val currentMinute = now.minute

            val sleepHour = prefs.sleepStartHour
            val timeToSleep = if (sleepHour > currentHour) {
                ((sleepHour - currentHour) * 60) - currentMinute
            } else if (sleepHour == currentHour) {
                -currentMinute // Already in sleep hour
            } else {
                ((24 - currentHour + sleepHour) * 60) - currentMinute
            }

            return maxOf(0, timeToSleep)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating time until sleep", e)
            return -1
        }
    }
}
```

---

### 4. LocationTrackingService.kt - Modifications

Add field to the class (with other @Inject fields):

```kotlin
@Inject
lateinit var sleepScheduleManager: SleepScheduleManager
```

Modify the `saveLocation()` function (add this check at the start):

```kotlin
private suspend fun saveLocation(androidLocation: AndroidLocation) {
    // Add this check
    if (isPaused || sleepScheduleManager.isInSleepWindow()) {
        logger.d(TAG, "Location skipped: paused=${isPaused}, sleeping=${sleepScheduleManager.isInSleepWindow()}")
        return
    }
    
    // ... rest of existing saveLocation code
}
```

Enhance `shouldSaveLocation()` (add after the accuracy check):

```kotlin
private fun shouldSaveLocation(newLocation: AndroidLocation): Boolean {
    val preferences = currentPreferences ?: return true
    val lastLocation = lastSavedLocation ?: return true

    // CRITICAL: Check if in sleep mode
    val isSleeping = runBlocking { sleepScheduleManager.isInSleepWindow() }
    if (isSleeping && !preferences.motionDetectionEnabled) {
        logger.d(TAG, "Location filtered: in sleep window, no motion detection")
        return false
    }

    // ... rest of existing shouldSaveLocation code
}
```

---

### 5. SettingsViewModel.kt - New Methods to Add

Add these methods to the class:

```kotlin
fun updateSleepStartHour(hour: Int) {
    viewModelScope.launch {
        try {
            preferencesRepository.updateSleepStartHour(hour)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to update sleep start time: ${e.message}"
            )
        }
    }
}

fun updateSleepEndHour(hour: Int) {
    viewModelScope.launch {
        try {
            preferencesRepository.updateSleepEndHour(hour)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to update sleep end time: ${e.message}"
            )
        }
    }
}

fun updateEnableSleepMode(enabled: Boolean) {
    viewModelScope.launch {
        try {
            preferencesRepository.updateEnableSleepMode(enabled)
            _uiState.value = _uiState.value.copy(
                exportMessage = if (enabled) "Sleep mode enabled" else "Sleep mode disabled"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to update sleep mode: ${e.message}"
            )
        }
    }
}

fun updateEnableMotionDetection(enabled: Boolean) {
    viewModelScope.launch {
        try {
            preferencesRepository.updateEnableMotionDetection(enabled)
            _uiState.value = _uiState.value.copy(
                exportMessage = if (enabled) "Motion detection enabled" else "Motion detection disabled"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to update motion detection: ${e.message}"
            )
        }
    }
}
```

---

### 6. SleepScheduleSection.kt - New Component to Create

Create new file: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/settings/components/SleepScheduleSection.kt`

```kotlin
package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.UserPreferences

@Composable
fun SleepScheduleSection(
    preferences: UserPreferences,
    onUpdateSleepStart: (Int) -> Unit,
    onUpdateSleepEnd: (Int) -> Unit,
    onUpdateEnableSleep: (Boolean) -> Unit,
    onUpdateMotionDetection: (Boolean) -> Unit
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Text(
                text = "Sleep Schedule",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Enable Sleep Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Sleep Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Pause tracking during sleep hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences.sleepModeEnabled,
                    onCheckedChange = onUpdateEnableSleep,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            if (preferences.sleepModeEnabled) {
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Sleep Start Time
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sleep Start",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%02d:00", preferences.sleepStartHour),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showStartTimePicker = !showStartTimePicker }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit sleep start time")
                        }
                    }
                }

                // Sleep End Time
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sleep End",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%02d:00", preferences.sleepEndHour),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showEndTimePicker = !showEndTimePicker }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit sleep end time")
                        }
                    }
                }

                // Motion Detection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Motion Detection",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Resume tracking if motion detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.motionDetectionEnabled,
                        onCheckedChange = onUpdateMotionDetection,
                        modifier = Modifier.padding(start = 12.dp),
                        enabled = preferences.sleepModeEnabled
                    )
                }
            }
        }
    }

    // Time Picker Dialogs
    if (showStartTimePicker) {
        SimpleHourPickerDialog(
            currentHour = preferences.sleepStartHour,
            title = "Select Sleep Start Time",
            onConfirm = { hour ->
                onUpdateSleepStart(hour)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        SimpleHourPickerDialog(
            currentHour = preferences.sleepEndHour,
            title = "Select Sleep End Time",
            onConfirm = { hour ->
                onUpdateSleepEnd(hour)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@Composable
private fun SimpleHourPickerDialog(
    currentHour: Int,
    title: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember { mutableStateOf(currentHour) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%02d:00", selectedHour),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Slider(
                    value = selectedHour.toFloat(),
                    onValueChange = { selectedHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Hour: $selectedHour",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHour) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

### 7. SettingsScreen.kt - Integration Point

Add this item to the LazyColumn (place it after other sections):

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

Also add import at top:

```kotlin
import com.cosmiclaboratory.voyager.presentation.screen.settings.components.SleepScheduleSection
```

---

### 8. UtilsModule.kt - DI Registration

Add this provider to the UtilsModule:

```kotlin
@Provides
@Singleton
fun provideSleepScheduleManager(
    preferencesRepository: PreferencesRepository,
    logger: ProductionLogger
): SleepScheduleManager {
    return SleepScheduleManager(preferencesRepository, logger)
}
```

---

### 9. PreferencesRepository Implementation - Example for DataStore

Example of how to implement the new methods (adjust to your actual implementation):

```kotlin
override suspend fun updateSleepStartHour(hour: Int) {
    val validated = hour.coerceIn(0, 23)
    val current = getCurrentPreferences()
    val updated = current.copy(sleepStartHour = validated)
    updateUserPreferences(updated)
}

override suspend fun updateSleepEndHour(hour: Int) {
    val validated = hour.coerceIn(0, 23)
    val current = getCurrentPreferences()
    val updated = current.copy(sleepEndHour = validated)
    updateUserPreferences(updated)
}

override suspend fun updateEnableSleepMode(enabled: Boolean) {
    val current = getCurrentPreferences()
    val updated = current.copy(sleepModeEnabled = enabled)
    updateUserPreferences(updated)
}

override suspend fun updateEnableMotionDetection(enabled: Boolean) {
    val current = getCurrentPreferences()
    val updated = current.copy(motionDetectionEnabled = enabled)
    updateUserPreferences(updated)
}
```

---

## Unit Test Examples

Create test file: `app/src/test/java/com/cosmiclaboratory/voyager/utils/SleepScheduleManagerTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.*

class SleepScheduleManagerTest {
    
    @Mock private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var sleepScheduleManager: SleepScheduleManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        sleepScheduleManager = SleepScheduleManager(preferencesRepository, ProductionLogger())
    }
    
    @Test
    fun testIsInSleepWindow_NormalRange() {
        // Test 6:00-22:00 range
        // 10:00 (10 AM) should be awake
        // 13:00 (1 PM) should be awake
        // 23:00 (11 PM) should be awake
    }
    
    @Test
    fun testIsInSleepWindow_MidnightCrossing() {
        // Test 22:00-06:00 range
        // 23:00 (11 PM) should be sleeping
        // 02:00 (2 AM) should be sleeping
        // 05:00 (5 AM) should be sleeping
        // 06:00 (6 AM) should be awake
        // 07:00 (7 AM) should be awake
    }
    
    @Test
    fun testIsInSleepWindow_Disabled() {
        // When sleep mode disabled, should always return false
    }
}
```

---

## Common Implementation Errors to Avoid

```kotlin
// WRONG: Using runBlocking in main thread
private fun saveLocation() {
    val isSleeping = runBlocking { sleepScheduleManager.isInSleepWindow() }
    // This blocks the main thread!
}

// CORRECT: Make it suspend and call from coroutine
private suspend fun saveLocation() {
    val isSleeping = sleepScheduleManager.isInSleepWindow()
    // OK - we're in a coroutine
}

// WRONG: Not checking if enabled first
if (sleepScheduleManager.isInSleepWindow()) { }
// What if sleep mode is disabled?

// CORRECT: Check enabled flag
val prefs = preferencesRepository.getCurrentPreferences()
if (prefs.sleepModeEnabled && sleepScheduleManager.isInSleepWindow()) { }

// WRONG: Midnight logic
val isSleeping = hour >= 22 && hour < 6  // WRONG!

// CORRECT: Midnight logic
val isSleeping = if (22 < 6) {
    hour >= 22 && hour < 6
} else {
    hour >= 22 || hour < 6  // CORRECT!
}
```

---

End of Code Snippets
