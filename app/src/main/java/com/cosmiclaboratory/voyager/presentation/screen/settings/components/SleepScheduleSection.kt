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

/**
 * Sleep Schedule settings section for battery optimization (Phase 8.1 & 8.4)
 *
 * Allows users to configure when location tracking should pause during sleep hours.
 * Includes motion detection settings to resume tracking if movement is detected.
 */
@Composable
fun SleepScheduleSection(
    preferences: UserPreferences,
    sleepScheduleDisplay: String,
    estimatedBatterySavings: Int,
    motionDetectionAvailable: Boolean,
    onUpdateSleepModeEnabled: (Boolean) -> Unit,
    onUpdateSleepStartHour: (Int) -> Unit,
    onUpdateSleepEndHour: (Int) -> Unit,
    onUpdateMotionDetectionEnabled: (Boolean) -> Unit,
    onUpdateMotionSensitivity: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Sleep Schedule (Battery Optimization)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (preferences.sleepModeEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Sleep Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sleep Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (preferences.sleepModeEnabled) {
                                "Tracking paused during sleep"
                            } else {
                                "Tap to enable battery savings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.sleepModeEnabled,
                        onCheckedChange = onUpdateSleepModeEnabled
                    )
                }

                if (preferences.sleepModeEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sleep Schedule Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sleep Hours",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = sleepScheduleDisplay,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Time Picker Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set Start")
                        }

                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set End")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Battery Savings Estimate
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Estimated Battery Savings",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "~$estimatedBatterySavings% daily",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Phase 8.4: Motion Detection Settings
                    if (motionDetectionAvailable) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Motion Detection Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Motion Detection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (preferences.motionDetectionEnabled) {
                                        "Resume tracking if motion detected"
                                    } else {
                                        "Stay paused during sleep"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.motionDetectionEnabled,
                                onCheckedChange = onUpdateMotionDetectionEnabled
                            )
                        }

                        // Sensitivity Slider (only if motion detection enabled)
                        if (preferences.motionDetectionEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Column {
                                Text(
                                    text = "Motion Sensitivity: ${getSensitivityLabel(preferences.motionSensitivityThreshold)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = preferences.motionSensitivityThreshold,
                                    onValueChange = onUpdateMotionSensitivity,
                                    valueRange = 0f..1f,
                                    steps = 2 // Low, Medium, High
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("High", style = MaterialTheme.typography.labelSmall)
                                    Text("Low", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Time Picker Dialogs
    if (showStartTimePicker) {
        HourPickerDialog(
            title = "Sleep Start Time",
            currentHour = preferences.sleepStartHour,
            onHourSelected = { hour ->
                onUpdateSleepStartHour(hour)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        HourPickerDialog(
            title = "Wake Up Time",
            currentHour = preferences.sleepEndHour,
            onHourSelected = { hour ->
                onUpdateSleepEndHour(hour)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

/**
 * Simple hour picker dialog
 */
@Composable
private fun HourPickerDialog(
    title: String,
    currentHour: Int,
    onHourSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(currentHour) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Select hour (24-hour format):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Hour slider
                Column {
                    Text(
                        text = formatHour(selectedHour),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = selectedHour.toFloat(),
                        onValueChange = { selectedHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("12:00 AM", style = MaterialTheme.typography.labelSmall)
                        Text("11:00 PM", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onHourSelected(selectedHour) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Format hour as 12-hour time string
 */
private fun formatHour(hour: Int): String {
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour < 12) "AM" else "PM"
    return "$h:00 $amPm"
}

/**
 * Get sensitivity label (Phase 8.4)
 */
private fun getSensitivityLabel(threshold: Float): String {
    return when {
        threshold <= 0.3f -> "High"
        threshold <= 0.7f -> "Medium"
        else -> "Low"
    }
}
