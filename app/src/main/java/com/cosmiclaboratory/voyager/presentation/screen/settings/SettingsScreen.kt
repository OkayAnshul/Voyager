package com.cosmiclaboratory.voyager.presentation.screen.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.Teal
import com.cosmiclaboratory.voyager.ui.theme.TealDim
import com.cosmiclaboratory.voyager.utils.DeveloperModeManager
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for accessing DeveloperModeManager from Composables
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeveloperModeManagerEntryPoint {
    fun developerModeManager(): DeveloperModeManager
}

/**
 * Settings Screen - Matrix UI
 *
 * Enhanced with:
 * - Collapsible sections for better organization
 * - Matrix theme with green/black styling
 * - Quick profile switcher
 * - Clean, minimal design
 */
@Composable
fun SettingsScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    onRequestNotificationPermission: () -> Unit = {},
    onNavigateToDebugDataInsertion: () -> Unit = {},
    onNavigateToAdvancedSettings: () -> Unit = {},
    onNavigateToDeveloperProfile: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val developerModeManager = remember {
        EntryPointAccessors.fromApplication<DeveloperModeManagerEntryPoint>(
            context.applicationContext
        ).developerModeManager()
    }

    val uiState by viewModel.uiState.collectAsState()
    val isDeveloperMode by developerModeManager.isDeveloperModeEnabled.collectAsState()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var versionTapMessage by remember { mutableStateOf<String?>(null) }

    // Expanded sections state
    var expandedSections by remember { mutableStateOf(setOf("tracking", "data")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        MatrixCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
                MatrixIconButton(onClick = { viewModel.refreshSettings() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Location Tracking Section
            item {
                CollapsibleSection(
                    title = "LOCATION TRACKING",
                    isExpanded = "tracking" in expandedSections,
                    onToggle = { expandedSections = expandedSections.toggle("tracking") }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Tracking toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ENABLE TRACKING",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Teal
                                )
                                Text(
                                    text = if (uiState.isLocationTrackingEnabled) "Currently active" else "Currently paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.isLocationTrackingEnabled,
                                onCheckedChange = { viewModel.toggleLocationTracking() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Teal,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Tracking Quality Section
            item {
                CollapsibleSection(
                    title = "TRACKING QUALITY",
                    isExpanded = "quality" in expandedSections,
                    onToggle = { expandedSections = expandedSections.toggle("quality") }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Activity Recognition Confidence
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MOVEMENT DETECTION",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Teal
                                )
                                Text(
                                    text = "${(uiState.preferences.activityRecognitionConfidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                            }
                            Text(
                                text = "Confidence required to skip locations while driving/moving fast. Higher = fewer missed locations but more battery use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = uiState.preferences.activityRecognitionConfidence,
                                onValueChange = { viewModel.updateActivityRecognitionConfidence(it) },
                                valueRange = 0.75f..0.95f,
                                steps = 19, // 0.75, 0.76, ..., 0.95 (21 values, so 19 steps)
                                colors = SliderDefaults.colors(
                                    thumbColor = Teal,
                                    activeTrackColor = Teal,
                                    inactiveTrackColor = TealDim
                                )
                            )
                            Text(
                                text = "💡 Recommended: 90% - Balances accuracy and battery life",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealDim
                            )
                        }

                        MatrixDivider()

                        // Stationary Mode Multiplier
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STATIONARY MODE",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Teal
                                )
                                Text(
                                    text = "${uiState.preferences.stationaryModeMultiplier}x",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                            }
                            Text(
                                text = "Update interval multiplier when not moving. Higher = better battery but may miss short trips.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = uiState.preferences.stationaryModeMultiplier,
                                onValueChange = { viewModel.updateStationaryModeMultiplier(it) },
                                valueRange = 1.0f..2.0f,
                                steps = 9, // 1.0, 1.1, 1.2, ..., 2.0 (11 values, so 9 steps)
                                colors = SliderDefaults.colors(
                                    thumbColor = Teal,
                                    activeTrackColor = Teal,
                                    inactiveTrackColor = TealDim
                                )
                            )
                            Text(
                                text = "💡 Recommended: 1.5x - Good balance for most users",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealDim
                            )
                        }

                        MatrixDivider()

                        // Max Tracking Gap
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MAX TRACKING GAP",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Teal
                                )
                                Text(
                                    text = "${uiState.preferences.maxTrackingGapSeconds / 60}m",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                            }
                            Text(
                                text = "Maximum time without saving location before forcing a save. Lower = more complete data but more storage use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = uiState.preferences.maxTrackingGapSeconds.toFloat(),
                                onValueChange = { viewModel.updateMaxTrackingGapSeconds(it.toInt()) },
                                valueRange = 180f..600f,
                                steps = 6, // 180s, 240s, 300s, 360s, 420s, 480s, 540s, 600s (8 values, so 6 steps)
                                colors = SliderDefaults.colors(
                                    thumbColor = Teal,
                                    activeTrackColor = Teal,
                                    inactiveTrackColor = TealDim
                                )
                            )
                            Text(
                                text = "💡 Recommended: 5m (300s) - Prevents data loss without excessive saves",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealDim
                            )
                        }
                    }
                }
            }

            // Data Statistics Section
            item {
                CollapsibleSection(
                    title = "YOUR DATA",
                    isExpanded = "data" in expandedSections,
                    onToggle = { expandedSections = expandedSections.toggle("data") }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Stats grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DataStatItem("LOCATIONS", uiState.totalLocations.toString())
                            MatrixVerticalDivider(modifier = Modifier.height(48.dp))
                            DataStatItem("PLACES", uiState.totalPlaces.toString())
                            MatrixVerticalDivider(modifier = Modifier.height(48.dp))
                            DataStatItem("VISITS", uiState.totalVisits.toString())
                        }

                        MatrixDivider()

                        // Quick actions
                        MatrixButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXPORT DATA")
                        }

                        MatrixButton(
                            onClick = { showClearDataDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CLEAR ALL DATA", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Advanced Settings Section
            item {
                MatrixCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToAdvancedSettings
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "ADVANCED SETTINGS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                                Text(
                                    text = "Tracking, detection, timeline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
                            tint = Teal
                        )
                    }
                }
            }

            // Debug Tools (Developer Mode)
            if (isDeveloperMode) {
                item {
                    MatrixCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToDebugDataInsertion
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "DEBUG TOOLS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Developer mode enabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Go",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // App Info
            item {
                MatrixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Flight,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "VOYAGER",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Teal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "VERSION 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = TealDim,
                            modifier = Modifier.clickable {
                                val remainingTaps = developerModeManager.registerTap()
                                versionTapMessage = remainingTaps?.let {
                                    "$it more tap${if (it > 1) "s" else ""} for developer mode"
                                } ?: "Developer mode enabled!"
                            }
                        )

                        versionTapMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            MatrixBadge(count = message)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // About Developer Button
                        MatrixButton(
                            onClick = onNavigateToDeveloperProfile,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ABOUT DEVELOPER")
                        }
                    }
                }
            }
        }
    }

    // Clear Data Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = {
                Text(
                    "CLEAR ALL DATA?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will permanently delete all your locations, places, and visits. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }

    // Export Data Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    "EXPORT DATA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Export your location history as JSON file?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.exportData(com.cosmiclaboratory.voyager.domain.usecase.ExportFormat.JSON)
                        showExportDialog = false
                    }
                ) {
                    Text("EXPORT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
}

/**
 * Data Stat Item
 */
@Composable
private fun DataStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Teal
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TealDim
        )
    }
}

/**
 * Extension function to toggle set membership
 */
private fun Set<String>.toggle(item: String): Set<String> {
    return if (item in this) {
        this - item
    } else {
        this + item
    }
}
