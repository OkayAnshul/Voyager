package com.cosmiclaboratory.voyager.presentation.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.screen.settings.components.SleepScheduleSection
import com.cosmiclaboratory.voyager.presentation.screen.settings.components.CategoryDetectionSettings
import java.time.format.DateTimeFormatter

/**
 * Advanced Settings Screen
 *
 * Consolidated screen that merges EnhancedSettingsScreen and ExpertSettingsScreen
 * Provides advanced configuration options with glassmorphism design
 *
 * Features:
 * - Advanced tracking parameters
 * - Battery & performance tuning
 * - Activity recognition settings
 * - Sleep schedule configuration
 * - Place review settings
 * - Timeline customization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Location Tracking Section
            item {
                GlassCard {
                    Text(
                        text = "Location Tracking",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Update Interval
                    Text(
                        text = "Update Interval: ${uiState.preferences.locationUpdateIntervalMs / 1000}s",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = (uiState.preferences.locationUpdateIntervalMs / 1000).toFloat(),
                        onValueChange = { viewModel.updateLocationUpdateInterval((it * 1000).toLong()) },
                        valueRange = 10f..300f,
                        steps = 28
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Min Distance
                    Text(
                        text = "Min Distance: ${uiState.preferences.minDistanceChangeMeters}m",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.preferences.minDistanceChangeMeters,
                        onValueChange = { viewModel.updateMinDistanceChange(it) },
                        valueRange = 5f..200f,
                        steps = 38
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Max GPS Accuracy
                    Text(
                        text = "Max GPS Accuracy: ${uiState.preferences.maxGpsAccuracyMeters}m",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.preferences.maxGpsAccuracyMeters,
                        onValueChange = { viewModel.updateMaxGpsAccuracy(it) },
                        valueRange = 10f..100f,
                        steps = 17
                    )
                }
            }

            // Place Detection Section
            item {
                GlassCard {
                    Text(
                        text = "Place Detection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Min Distance Between Places
                    Text(
                        text = "Min Distance Between Places: ${uiState.preferences.minimumDistanceBetweenPlaces.toInt()}m",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.preferences.minimumDistanceBetweenPlaces,
                        onValueChange = { viewModel.updateMinimumDistanceBetweenPlaces(it) },
                        valueRange = 20f..500f,
                        steps = 47
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stationary Threshold
                    Text(
                        text = "Stationary Threshold: ${uiState.preferences.stationaryThresholdMinutes} min",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.preferences.stationaryThresholdMinutes.toFloat(),
                        onValueChange = { viewModel.updateStationaryThreshold(it.toInt()) },
                        valueRange = 2f..30f,
                        steps = 27
                    )
                }
            }

            // Timeline Settings Section
            item {
                GlassCard {
                    Text(
                        text = "Timeline Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Time Window: ${uiState.preferences.timelineTimeWindowMinutes} min",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Group nearby visits within this time window",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = uiState.preferences.timelineTimeWindowMinutes == minutes.toLong(),
                                onClick = { viewModel.updateTimelineTimeWindow(minutes) },
                                label = { Text("${minutes}m") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Distance Threshold
                    Text(
                        text = "Distance Threshold: ${uiState.preferences.timelineDistanceThresholdMeters.toInt()}m",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Show distance between places when greater than this threshold",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = uiState.preferences.timelineDistanceThresholdMeters.toFloat(),
                        onValueChange = { viewModel.updateTimelineDistanceThreshold(it.toDouble()) },
                        valueRange = 50f..1000f,
                        steps = 18
                    )
                }
            }

            // Battery & Performance Section
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Battery & Performance",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Auto-detect Battery Threshold
                        Text(
                            text = "Auto-Detect Battery Threshold: ${uiState.preferences.autoDetectBatteryThreshold}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Pause auto-detection when battery below this level",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = uiState.preferences.autoDetectBatteryThreshold.toFloat(),
                            onValueChange = { viewModel.updateAutoDetectBatteryThreshold(it.toInt()) },
                            valueRange = 10f..50f,
                            steps = 7
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stationary Mode Multipliers
                        Text(
                            text = "Stationary Interval Multiplier: ${String.format("%.1f", uiState.preferences.stationaryIntervalMultiplier)}x",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Reduce GPS frequency when stationary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = uiState.preferences.stationaryIntervalMultiplier,
                            onValueChange = { viewModel.updateStationaryIntervalMultiplier(it) },
                            valueRange = 1.5f..3.0f,
                            steps = 14
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Battery Requirement for Auto-Detection
                        Text(
                            text = "Battery Requirement",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "When to run automated place detection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.cosmiclaboratory.voyager.domain.model.BatteryRequirement.entries.forEach { requirement ->
                                FilterChip(
                                    selected = uiState.preferences.batteryRequirement == requirement,
                                    onClick = { viewModel.updateBatteryRequirement(requirement) },
                                    label = {
                                        Text(
                                            when (requirement) {
                                                com.cosmiclaboratory.voyager.domain.model.BatteryRequirement.ANY -> "Any"
                                                com.cosmiclaboratory.voyager.domain.model.BatteryRequirement.NOT_LOW -> "Not Low"
                                                com.cosmiclaboratory.voyager.domain.model.BatteryRequirement.CHARGING -> "Charging"
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Activity Recognition Section
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Activity Recognition",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Enable Activity Recognition
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use Activity Recognition",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Detect walking, driving, stationary to prevent false detections",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = uiState.preferences.useActivityRecognition,
                                onCheckedChange = { viewModel.updateUseActivityRecognition(it) }
                            )
                        }

                        if (uiState.preferences.useActivityRecognition) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Motion Detection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Motion Detection",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Resume tracking if motion detected during sleep mode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = uiState.preferences.motionDetectionEnabled,
                                    onCheckedChange = { viewModel.updateMotionDetectionEnabled(it) }
                                )
                            }

                            if (uiState.preferences.motionDetectionEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))

                                // Motion Sensitivity
                                Text(
                                    text = "Motion Sensitivity: ${String.format("%.1f", (1.0f - uiState.preferences.motionSensitivityThreshold))}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Higher = more sensitive to motion",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = 1.0f - uiState.preferences.motionSensitivityThreshold,
                                    onValueChange = { viewModel.updateMotionSensitivityThreshold(1.0f - it) },
                                    valueRange = 0.0f..1.0f,
                                    steps = 9
                                )
                            }
                        }
                    }
                }
            }

            // ISSUE #5: Sleep Schedule Section
            item {
                SleepScheduleSection(
                    preferences = uiState.preferences,
                    sleepScheduleDisplay = formatSleepSchedule(
                        uiState.preferences.sleepStartHour,
                        uiState.preferences.sleepEndHour
                    ),
                    estimatedBatterySavings = calculateBatterySavings(
                        uiState.preferences.sleepStartHour,
                        uiState.preferences.sleepEndHour
                    ),
                    motionDetectionAvailable = true,
                    onUpdateSleepModeEnabled = { viewModel.updateSleepModeEnabled(it) },
                    onUpdateSleepStartHour = { viewModel.updateSleepStartHour(it) },
                    onUpdateSleepEndHour = { viewModel.updateSleepEndHour(it) },
                    onUpdateMotionDetectionEnabled = { viewModel.updateMotionDetectionEnabled(it) },
                    onUpdateMotionSensitivity = { viewModel.updateMotionSensitivityThreshold(it) }
                )
            }

            // ISSUE #5: Category Detection Settings (disabled due to Issue #3)
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Category Detection (Disabled)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Automatic category detection is disabled. All places require manual category selection during review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ISSUE #5: Data Processing Settings
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Data Processing",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Batch Size
                        Text(
                            text = "Processing Batch Size: ${uiState.preferences.dataProcessingBatchSize}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Number of location points processed at once",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = uiState.preferences.dataProcessingBatchSize.toFloat(),
                            onValueChange = { viewModel.updateDataProcessingBatchSize(it.toInt()) },
                            valueRange = 100f..5000f,
                            steps = 48
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Auto-Detect Trigger Count
                        Text(
                            text = "Auto-Detect Trigger: ${uiState.preferences.autoDetectTriggerCount} locations",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Run place detection automatically after collecting this many points",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = uiState.preferences.autoDetectTriggerCount.toFloat(),
                            onValueChange = { viewModel.updateAutoDetectTriggerCount(it.toInt()) },
                            valueRange = 10f..500f,
                            steps = 48
                        )
                    }
                }
            }

            // ISSUE #5: Quality Filtering Settings
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quality Filtering",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Max GPS Accuracy (duplicate from above, but consolidated here)
                        Text(
                            text = "GPS Accuracy Filter: ${uiState.preferences.maxGpsAccuracyMeters.toInt()}m",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Reject location points with accuracy worse than this",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = uiState.preferences.maxGpsAccuracyMeters,
                            onValueChange = { viewModel.updateMaxGpsAccuracy(it) },
                            valueRange = 10f..100f,
                            steps = 17
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Speed Filter
                        Text(
                            text = "Max Speed Filter: ${String.format("%.0f", uiState.preferences.maxSpeedKmh)} km/h",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Reject location points with unrealistic speed (likely GPS errors)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = uiState.preferences.maxSpeedKmh.toFloat(),
                            onValueChange = { viewModel.updateMaxSpeedKmh(it.toDouble()) },
                            valueRange = 30f..300f,
                            steps = 53
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Minimum Dwell Time
                        Text(
                            text = "Min Dwell Time: ${uiState.preferences.minDwellTimeSeconds / 60} min",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Minimum duration to count as a visit (filters brief stops)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = (uiState.preferences.minDwellTimeSeconds / 60).toFloat(),
                            onValueChange = { viewModel.updateMinTimeBetweenUpdates((it * 60).toInt()) },
                            valueRange = 1f..30f,
                            steps = 28
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * Format sleep schedule for display
 */
private fun formatSleepSchedule(startHour: Int, endHour: Int): String {
    val startPeriod = if (startHour >= 12) "PM" else "AM"
    val endPeriod = if (endHour >= 12) "PM" else "AM"
    val displayStartHour = if (startHour > 12) startHour - 12 else if (startHour == 0) 12 else startHour
    val displayEndHour = if (endHour > 12) endHour - 12 else if (endHour == 0) 12 else endHour
    return "${displayStartHour}:00 $startPeriod - ${displayEndHour}:00 $endPeriod"
}

/**
 * Calculate estimated battery savings based on sleep duration
 */
private fun calculateBatterySavings(startHour: Int, endHour: Int): Int {
    val sleepDuration = if (endHour > startHour) {
        endHour - startHour
    } else {
        (24 - startHour) + endHour
    }
    // Assume ~4% battery per hour of tracking
    return (sleepDuration * 4).coerceIn(0, 100)
}
