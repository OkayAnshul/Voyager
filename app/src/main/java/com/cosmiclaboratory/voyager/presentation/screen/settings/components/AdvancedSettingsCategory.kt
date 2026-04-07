package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import kotlin.math.roundToInt

/**
 * Advanced settings category - collapsible section with advanced parameters
 */
@Composable
fun AdvancedSettingsCategory(
    preferences: UserPreferences,
    onUpdateMinDistanceBetweenPlaces: (Float) -> Unit,
    onUpdateStationaryThreshold: (Int) -> Unit,
    onUpdateStationaryMovementThreshold: (Float) -> Unit,
    onUpdatePatternAnalysisSettings: (Int, Float, Int, Int) -> Unit,
    onUpdateAnomalyDetectionSettings: (Int, Int, Float, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Category header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Advanced Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Place Detection Advanced Settings
                    Text(
                        text = "Place Detection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Minimum Distance Between Places",
                        value = preferences.minimumDistanceBetweenPlaces,
                        valueRange = 10f..100f,
                        steps = 17,
                        unit = "m",
                        description = "Minimum separation between distinct places. Lower = more places detected.",
                        onValueChange = onUpdateMinDistanceBetweenPlaces
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Stationary Detection Settings
                    Text(
                        text = "Stationary Detection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Stationary Time Threshold",
                        value = preferences.stationaryThresholdMinutes.toFloat(),
                        valueRange = 3f..15f,
                        steps = 11,
                        unit = "min",
                        description = "Time before activating battery-saving stationary mode",
                        onValueChange = { onUpdateStationaryThreshold(it.roundToInt()) }
                    )

                    SliderSetting(
                        label = "Stationary Movement Threshold",
                        value = preferences.stationaryMovementThreshold,
                        valueRange = 10f..50f,
                        steps = 7,
                        unit = "m",
                        description = "Maximum movement to still be considered stationary",
                        onValueChange = onUpdateStationaryMovementThreshold
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Pattern Analysis Settings
                    Text(
                        text = "Pattern Analysis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Minimum Visits for Pattern",
                        value = preferences.patternMinVisits.toFloat(),
                        valueRange = 2f..10f,
                        steps = 7,
                        unit = "visits",
                        description = "Visits needed to establish a behavioral pattern",
                        onValueChange = { newValue ->
                            onUpdatePatternAnalysisSettings(
                                newValue.roundToInt(),
                                preferences.patternMinConfidence,
                                preferences.patternTimeWindowMinutes,
                                preferences.patternAnalysisDays
                            )
                        }
                    )

                    SliderSetting(
                        label = "Pattern Confidence Threshold",
                        value = preferences.patternMinConfidence,
                        valueRange = 0.1f..0.8f,
                        steps = 6,
                        unit = "",
                        valueFormatter = { "${(it * 100).roundToInt()}%" },
                        description = "Minimum confidence to report a pattern",
                        onValueChange = { newValue ->
                            onUpdatePatternAnalysisSettings(
                                preferences.patternMinVisits,
                                newValue,
                                preferences.patternTimeWindowMinutes,
                                preferences.patternAnalysisDays
                            )
                        }
                    )

                    SliderSetting(
                        label = "Pattern Analysis Period",
                        value = preferences.patternAnalysisDays.toFloat(),
                        valueRange = 30f..365f,
                        steps = 10,
                        unit = "days",
                        description = "Historical period to analyze for patterns",
                        onValueChange = { newValue ->
                            onUpdatePatternAnalysisSettings(
                                preferences.patternMinVisits,
                                preferences.patternMinConfidence,
                                preferences.patternTimeWindowMinutes,
                                newValue.roundToInt()
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Anomaly Detection Settings
                    Text(
                        text = "Anomaly Detection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Recent Period for Anomalies",
                        value = preferences.anomalyRecentDays.toFloat(),
                        valueRange = 7f..30f,
                        steps = 4,
                        unit = "days",
                        description = "Recent time window to check for unusual behavior",
                        onValueChange = { newValue ->
                            onUpdateAnomalyDetectionSettings(
                                newValue.roundToInt(),
                                preferences.anomalyLookbackDays,
                                preferences.anomalyDurationThreshold,
                                preferences.anomalyTimeThresholdHours
                            )
                        }
                    )

                    SliderSetting(
                        label = "Anomaly Baseline Period",
                        value = preferences.anomalyLookbackDays.toFloat(),
                        valueRange = 30f..365f,
                        steps = 10,
                        unit = "days",
                        description = "Historical period for establishing normal behavior",
                        onValueChange = { newValue ->
                            onUpdateAnomalyDetectionSettings(
                                preferences.anomalyRecentDays,
                                newValue.roundToInt(),
                                preferences.anomalyDurationThreshold,
                                preferences.anomalyTimeThresholdHours
                            )
                        }
                    )

                    SliderSetting(
                        label = "Duration Deviation Threshold",
                        value = preferences.anomalyDurationThreshold,
                        valueRange = 0.3f..1.0f,
                        steps = 6,
                        unit = "",
                        valueFormatter = { "${(it * 100).roundToInt()}%" },
                        description = "Percentage deviation to flag duration anomalies",
                        onValueChange = { newValue ->
                            onUpdateAnomalyDetectionSettings(
                                preferences.anomalyRecentDays,
                                preferences.anomalyLookbackDays,
                                newValue,
                                preferences.anomalyTimeThresholdHours
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    description: String,
    valueFormatter: (Float) -> String = { value -> "${value.roundToInt()}$unit" },
    onValueChange: (Float) -> Unit,
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (description.isNotEmpty()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = valueFormatter(valueRange.start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueFormatter(valueRange.endInclusive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
