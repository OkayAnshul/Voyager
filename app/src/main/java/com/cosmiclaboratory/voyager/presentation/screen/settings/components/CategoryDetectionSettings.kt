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
 * Category detection thresholds for automatic place categorization
 */
@Composable
fun CategoryDetectionSettings(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit,
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
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Place Category Detection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
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
                    Text(
                        text = "Adjust how the app automatically categorizes places based on your behavior patterns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Home Detection
                    CategoryCard(
                        icon = Icons.Default.Home,
                        title = "Home Detection",
                        description = "Detected based on overnight presence"
                    ) {
                        SliderSetting(
                            label = "Night Activity Threshold",
                            value = preferences.homeNightActivityThreshold,
                            valueRange = 0.1f..1.0f,
                            steps = 8,
                            unit = "",
                            valueFormatter = { "${(it * 100).roundToInt()}%" },
                            description = "% of nights spent at location to classify as Home",
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(homeNightActivityThreshold = newValue))
                            }
                        )
                    }

                    // Work Detection
                    CategoryCard(
                        icon = Icons.Default.Build,
                        title = "Work Detection",
                        description = "Detected based on weekday daytime presence"
                    ) {
                        SliderSetting(
                            label = "Work Hours Activity Threshold",
                            value = preferences.workHoursActivityThreshold,
                            valueRange = 0.1f..1.0f,
                            steps = 8,
                            unit = "",
                            valueFormatter = { "${(it * 100).roundToInt()}%" },
                            description = "% of work hours spent at location to classify as Work",
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(workHoursActivityThreshold = newValue))
                            }
                        )
                    }

                    // Gym Detection
                    CategoryCard(
                        icon = Icons.Default.Star,
                        title = "Gym Detection",
                        description = "Detected based on workout time patterns"
                    ) {
                        SliderSetting(
                            label = "Workout Activity Threshold",
                            value = preferences.gymActivityThreshold,
                            valueRange = 0.1f..1.0f,
                            steps = 8,
                            unit = "",
                            valueFormatter = { "${(it * 100).roundToInt()}%" },
                            description = "% of visits during typical workout times",
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(gymActivityThreshold = newValue))
                            }
                        )
                    }

                    // Shopping Detection
                    CategoryCard(
                        icon = Icons.Default.ShoppingCart,
                        title = "Shopping Detection",
                        description = "Detected based on visit duration"
                    ) {
                        SliderSetting(
                            label = "Minimum Shopping Duration",
                            value = preferences.shoppingMinDurationMinutes.toFloat(),
                            valueRange = 5f..60f,
                            steps = 10,
                            unit = " min",
                            description = "Shortest visit to consider as shopping",
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(shoppingMinDurationMinutes = newValue.roundToInt()))
                            }
                        )

                        SliderSetting(
                            label = "Maximum Shopping Duration",
                            value = preferences.shoppingMaxDurationMinutes.toFloat(),
                            valueRange = 30f..480f,
                            steps = 8,
                            unit = " min",
                            description = "Longest visit to still consider as shopping",
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(shoppingMaxDurationMinutes = newValue.roundToInt()))
                            }
                        )
                    }

                    // Restaurant Detection
                    CategoryCard(
                        icon = Icons.Default.Star,
                        title = "Restaurant Detection",
                        description = "Detected based on meal time patterns"
                    ) {
                        SliderSetting(
                            label = "Meal Time Activity Threshold",
                            value = preferences.restaurantMealTimeThreshold,
                            valueRange = 0.1f..1.0f,
                            steps = 8,
                            unit = "",
                            valueFormatter = { "${(it * 100).roundToInt()}%" },
                            description = "% of visits during typical meal times",
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(restaurantMealTimeThreshold = newValue))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
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
    Column(modifier = modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (description.isNotEmpty()) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
