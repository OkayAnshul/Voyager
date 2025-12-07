package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.animation.AnimatedVisibility
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
import kotlin.math.roundToInt

/**
 * Battery & Performance settings category
 */
@Composable
fun BatteryPerformanceCategory(
    preferences: UserPreferences,
    onUpdateStationaryMultipliers: (Float, Float) -> Unit,
    onUpdateDashboardRefreshIntervals: (Int, Int, Int) -> Unit,
    onUpdateGeocodingCacheDuration: (Int) -> Unit,
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
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Battery & Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
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
                    // Stationary Mode Optimization
                    Text(
                        text = "Stationary Mode Optimization",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "When stationary, the app can reduce update frequency to save battery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Interval Multiplier (Stationary)",
                        value = preferences.stationaryIntervalMultiplier,
                        valueRange = 1.5f..3.0f,
                        steps = 5,
                        unit = "x",
                        valueFormatter = { "${String.format("%.1f", it)}x slower" },
                        description = "How much to slow down updates when stationary",
                        onValueChange = { newValue ->
                            onUpdateStationaryMultipliers(newValue, preferences.stationaryDistanceMultiplier)
                        }
                    )

                    SliderSetting(
                        label = "Distance Multiplier (Stationary)",
                        value = preferences.stationaryDistanceMultiplier,
                        valueRange = 1.5f..3.0f,
                        steps = 5,
                        unit = "x",
                        valueFormatter = { "${String.format("%.1f", it)}x larger" },
                        description = "How much to increase minimum distance when stationary",
                        onValueChange = { newValue ->
                            onUpdateStationaryMultipliers(preferences.stationaryIntervalMultiplier, newValue)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // UI Refresh Rates
                    Text(
                        text = "Dashboard Refresh Rates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Control how often the dashboard updates in different states",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Refresh at Place",
                        value = preferences.dashboardRefreshAtPlaceSeconds.toFloat(),
                        valueRange = 10f..60f,
                        steps = 9,
                        unit = "s",
                        description = "Refresh interval when at a detected place",
                        onValueChange = { newValue ->
                            onUpdateDashboardRefreshIntervals(
                                newValue.roundToInt(),
                                preferences.dashboardRefreshTrackingSeconds,
                                preferences.dashboardRefreshIdleSeconds
                            )
                        }
                    )

                    SliderSetting(
                        label = "Refresh While Tracking",
                        value = preferences.dashboardRefreshTrackingSeconds.toFloat(),
                        valueRange = 30f..120f,
                        steps = 8,
                        unit = "s",
                        description = "Refresh interval while actively tracking movement",
                        onValueChange = { newValue ->
                            onUpdateDashboardRefreshIntervals(
                                preferences.dashboardRefreshAtPlaceSeconds,
                                newValue.roundToInt(),
                                preferences.dashboardRefreshIdleSeconds
                            )
                        }
                    )

                    SliderSetting(
                        label = "Refresh When Idle",
                        value = preferences.dashboardRefreshIdleSeconds.toFloat(),
                        valueRange = 60f..300f,
                        steps = 7,
                        unit = "s",
                        description = "Refresh interval when no tracking is active",
                        onValueChange = { newValue ->
                            onUpdateDashboardRefreshIntervals(
                                preferences.dashboardRefreshAtPlaceSeconds,
                                preferences.dashboardRefreshTrackingSeconds,
                                newValue.roundToInt()
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Caching Settings
                    Text(
                        text = "Caching Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        label = "Geocoding Cache Duration",
                        value = preferences.geocodingCacheDurationDays.toFloat(),
                        valueRange = 7f..90f,
                        steps = 10,
                        unit = " days",
                        description = "How long to cache reverse geocoding results (address lookups)",
                        onValueChange = { newValue ->
                            onUpdateGeocodingCacheDuration(newValue.roundToInt())
                        }
                    )

                    // Battery savings estimate
                    BatterySavingsEstimate(preferences)
                }
            }
        }
    }
}

@Composable
private fun BatterySavingsEstimate(preferences: UserPreferences) {
    val sleepHours = if (preferences.sleepModeEnabled) {
        val start = preferences.sleepStartHour
        val end = preferences.sleepEndHour
        if (end > start) end - start else (24 - start) + end
    } else 0

    val sleepSavings = (sleepHours.toFloat() / 24f * 100).roundToInt()

    val stationarySavings = when {
        preferences.stationaryIntervalMultiplier >= 2.5f -> 15
        preferences.stationaryIntervalMultiplier >= 2.0f -> 10
        else -> 5
    }

    val totalEstimatedSavings = sleepSavings + stationarySavings

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Estimated Battery Savings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "~$totalEstimatedSavings% with current settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                if (sleepHours > 0) {
                    Text(
                        text = "Sleep mode: $sleepHours hours ($sleepSavings%) • Stationary: ~$stationarySavings%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
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
                color = MaterialTheme.colorScheme.tertiary
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
