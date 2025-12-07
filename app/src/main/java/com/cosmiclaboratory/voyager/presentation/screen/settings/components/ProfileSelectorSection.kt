package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.BatteryImpact
import com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile

/**
 * Profile selector section for quick switching between preset configurations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectorSection(
    currentProfile: SettingsPresetProfile,
    onProfileSelected: (SettingsPresetProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentProfile.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = currentProfile.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }

                FilledTonalButton(
                    onClick = { showProfileDialog = true },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Change")
                }
            }

            // Battery and accuracy indicators
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatteryImpactChip(batteryImpact = currentProfile.batteryImpact)
                AccuracyLevelChip(accuracyLevel = currentProfile.accuracyLevel)
            }
        }
    }

    if (showProfileDialog) {
        ProfileSelectionDialog(
            currentProfile = currentProfile,
            onProfileSelected = { profile ->
                onProfileSelected(profile)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
private fun BatteryImpactChip(batteryImpact: BatteryImpact) {
    val chipData = when (batteryImpact) {
        BatteryImpact.LOW -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.tertiary,
            "Low Battery Use"
        )
        BatteryImpact.MODERATE -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.primary,
            "Moderate Battery"
        )
        BatteryImpact.HIGH -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            "Higher Battery Use"
        )
        BatteryImpact.UNKNOWN -> Triple(
            Icons.Default.Settings,
            MaterialTheme.colorScheme.outline,
            "Custom Battery"
        )
    }

    AssistChip(
        onClick = { },
        label = { Text(chipData.third, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(chipData.first, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = chipData.second.copy(alpha = 0.1f),
            labelColor = chipData.second,
            leadingIconContentColor = chipData.second
        )
    )
}

@Composable
private fun AccuracyLevelChip(accuracyLevel: com.cosmiclaboratory.voyager.domain.model.AccuracyLevel) {
    val chipData = when (accuracyLevel) {
        com.cosmiclaboratory.voyager.domain.model.AccuracyLevel.BASIC -> Triple(
            Icons.Default.LocationOn,
            MaterialTheme.colorScheme.outline,
            "Basic Accuracy"
        )
        com.cosmiclaboratory.voyager.domain.model.AccuracyLevel.BALANCED -> Triple(
            Icons.Default.LocationOn,
            MaterialTheme.colorScheme.primary,
            "Balanced"
        )
        com.cosmiclaboratory.voyager.domain.model.AccuracyLevel.PRECISE -> Triple(
            Icons.Default.Place,
            MaterialTheme.colorScheme.tertiary,
            "Precise"
        )
        com.cosmiclaboratory.voyager.domain.model.AccuracyLevel.CUSTOM -> Triple(
            Icons.Default.Settings,
            MaterialTheme.colorScheme.secondary,
            "Custom"
        )
    }

    AssistChip(
        onClick = { },
        label = { Text(chipData.third, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(chipData.first, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = chipData.second.copy(alpha = 0.1f),
            labelColor = chipData.second,
            leadingIconContentColor = chipData.second
        )
    )
}

@Composable
private fun ProfileSelectionDialog(
    currentProfile: SettingsPresetProfile,
    onProfileSelected: (SettingsPresetProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Settings Profile") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose a preset configuration or use custom settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsPresetProfile.values()
                    .filter { it != SettingsPresetProfile.CUSTOM || currentProfile == SettingsPresetProfile.CUSTOM }
                    .forEach { profile ->
                        ProfileCard(
                            profile = profile,
                            isSelected = profile == currentProfile,
                            onClick = { onProfileSelected(profile) }
                        )
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileCard(
    profile: SettingsPresetProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (profile) {
        SettingsPresetProfile.BATTERY_SAVER -> Icons.Default.Check
        SettingsPresetProfile.DAILY_COMMUTER -> Icons.Default.Home
        SettingsPresetProfile.TRAVELER -> Icons.Default.Place
        SettingsPresetProfile.CUSTOM -> Icons.Default.Settings
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Key features
                if (profile != SettingsPresetProfile.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    profile.getKeyFeatures().take(3).forEach { feature ->
                        Text(
                            text = "• $feature",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
