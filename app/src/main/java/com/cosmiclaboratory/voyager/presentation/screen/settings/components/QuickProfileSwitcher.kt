package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile
import com.cosmiclaboratory.voyager.domain.model.TrackingAccuracyMode

/**
 * Quick Profile Switcher FAB
 * Provides fast access to switch between preset profiles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickProfileSwitcherFAB(
    currentProfile: SettingsPresetProfile,
    onProfileSelected: (SettingsPresetProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var justSwitched by remember { mutableStateOf(false) }

    // Animation for profile switch confirmation
    val scale by animateFloatAsState(
        targetValue = if (justSwitched) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { justSwitched = false }
    )

    FloatingActionButton(
        onClick = { showBottomSheet = true },
        modifier = modifier.scale(scale),
        containerColor = when (currentProfile) {
            SettingsPresetProfile.BATTERY_SAVER -> MaterialTheme.colorScheme.tertiary
            SettingsPresetProfile.DAILY_COMMUTER -> MaterialTheme.colorScheme.primary
            SettingsPresetProfile.TRAVELER -> MaterialTheme.colorScheme.secondary
            SettingsPresetProfile.CUSTOM -> MaterialTheme.colorScheme.primary
        }
    ) {
        Icon(
            imageVector = getProfileIcon(currentProfile),
            contentDescription = "Switch Profile"
        )
    }

    if (showBottomSheet) {
        ProfileSwitcherBottomSheet(
            currentProfile = currentProfile,
            onProfileSelected = { profile ->
                onProfileSelected(profile)
                justSwitched = true
                showBottomSheet = false
            },
            onDismiss = { showBottomSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitcherBottomSheet(
    currentProfile: SettingsPresetProfile,
    onProfileSelected: (SettingsPresetProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Switch Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Choose a preset configuration for your tracking needs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Profile Cards
            ProfileCard(
                profile = SettingsPresetProfile.BATTERY_SAVER,
                isSelected = currentProfile == SettingsPresetProfile.BATTERY_SAVER,
                onClick = { onProfileSelected(SettingsPresetProfile.BATTERY_SAVER) },
                icon = Icons.Default.Star,
                color = MaterialTheme.colorScheme.tertiary
            )

            ProfileCard(
                profile = SettingsPresetProfile.DAILY_COMMUTER,
                isSelected = currentProfile == SettingsPresetProfile.DAILY_COMMUTER,
                onClick = { onProfileSelected(SettingsPresetProfile.DAILY_COMMUTER) },
                icon = Icons.Default.Home,
                color = MaterialTheme.colorScheme.primary
            )

            ProfileCard(
                profile = SettingsPresetProfile.TRAVELER,
                isSelected = currentProfile == SettingsPresetProfile.TRAVELER,
                onClick = { onProfileSelected(SettingsPresetProfile.TRAVELER) },
                icon = Icons.Default.Place,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileCard(
    profile: SettingsPresetProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(color))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = color.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Profile Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Trade-off Indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TradeOffIndicator(
                        "Battery",
                        getBatteryLevel(profile),
                        Icons.Default.Star
                    )
                    TradeOffIndicator(
                        "Accuracy",
                        getAccuracyLevel(profile),
                        Icons.Default.Star
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterChip(label: String, icon: ImageVector) {
    AssistChip(
        onClick = { },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) },
        modifier = Modifier.height(28.dp)
    )
}

@Composable
private fun TradeOffIndicator(label: String, level: Int, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 4.dp)
                        .then(
                            Modifier.padding(horizontal = 1.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (index < level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {}
                }
            }
        }
    }
}

// Helper functions
private fun getProfileIcon(profile: SettingsPresetProfile): ImageVector {
    return when (profile) {
        SettingsPresetProfile.BATTERY_SAVER -> Icons.Default.Star
        SettingsPresetProfile.DAILY_COMMUTER -> Icons.Default.Home
        SettingsPresetProfile.TRAVELER -> Icons.Default.Place
        SettingsPresetProfile.CUSTOM -> Icons.Default.Settings
    }
}

private fun getAccuracyLabel(mode: TrackingAccuracyMode): String {
    return when (mode) {
        TrackingAccuracyMode.HIGH_ACCURACY -> "High Acc"
        TrackingAccuracyMode.BALANCED -> "Balanced"
        TrackingAccuracyMode.POWER_SAVE -> "Power Save"
    }
}

private fun getBatteryLevel(profile: SettingsPresetProfile): Int {
    return when (profile) {
        SettingsPresetProfile.BATTERY_SAVER -> 3 // High battery saving
        SettingsPresetProfile.DAILY_COMMUTER -> 2 // Medium
        SettingsPresetProfile.TRAVELER -> 1 // Low battery saving (more drain)
        SettingsPresetProfile.CUSTOM -> 2 // Default medium
    }
}

private fun getAccuracyLevel(profile: SettingsPresetProfile): Int {
    return when (profile) {
        SettingsPresetProfile.BATTERY_SAVER -> 1 // Low accuracy
        SettingsPresetProfile.DAILY_COMMUTER -> 2 // Medium
        SettingsPresetProfile.TRAVELER -> 3 // High accuracy
        SettingsPresetProfile.CUSTOM -> 2 // Default medium
    }
}
