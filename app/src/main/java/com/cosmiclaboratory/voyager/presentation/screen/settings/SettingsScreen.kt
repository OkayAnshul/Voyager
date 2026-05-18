package com.cosmiclaboratory.voyager.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp
import com.cosmiclaboratory.voyager.utils.DeveloperModeManager
import com.cosmiclaboratory.voyager.presentation.model.PermissionStatus
import com.cosmiclaboratory.voyager.presentation.screen.settings.components.GeocodingProvidersSection
import com.cosmiclaboratory.voyager.presentation.screen.settings.components.SleepScheduleSection
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

enum class SettingsTab(val title: String) {
    GENERAL("General"),
    DETECTION("Detection"),
    PRIVACY_DATA("Privacy & Data"),
    ADVANCED("Advanced")
}

/**
 * Settings Screen — 4-tab unified layout with all UserPreferences fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    onRequestNotificationPermission: () -> Unit = {},
    onNavigateToDebugDataInsertion: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToDeveloperProfile: () -> Unit = {},
    onNavigateToPipelineDebug: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onNavigateToReliability: () -> Unit = {},
    onNavigateToMileage: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
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

    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var versionTapMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = VoyagerColors.Background,
                titleContentColor = VoyagerColors.OnSurface
            ),
            windowInsets = WindowInsets(0)
        )
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 16.dp,
            containerColor = VoyagerColors.Background,
            contentColor = VoyagerColors.Primary,
            divider = {}
        ) {
            SettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            tab.title,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == tab) VoyagerColors.Primary
                            else VoyagerColors.OnSurfaceVariant
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            SettingsTab.GENERAL -> GeneralTabContent(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToCategories = onNavigateToCategories,
                onNavigateToFeedback = onNavigateToFeedback,
                onNavigateToDeveloperProfile = onNavigateToDeveloperProfile,
                onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                onNavigateToReliability = onNavigateToReliability,
                onNavigateToMileage = onNavigateToMileage,
                onNavigateToPaywall = onNavigateToPaywall
            )
            SettingsTab.DETECTION -> DetectionTabContent(
                uiState = uiState,
                viewModel = viewModel
            )
            SettingsTab.PRIVACY_DATA -> PrivacyDataTabContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowClearDataDialog = { showClearDataDialog = true },
                onShowExportDialog = onNavigateToExport
            )
            SettingsTab.ADVANCED -> AdvancedTabContent(
                uiState = uiState,
                viewModel = viewModel,
                isDeveloperMode = isDeveloperMode,
                developerModeManager = developerModeManager,
                versionTapMessage = versionTapMessage,
                onVersionTapMessage = { versionTapMessage = it },
                onNavigateToDebugDataInsertion = onNavigateToDebugDataInsertion,
                onNavigateToDeveloperProfile = onNavigateToDeveloperProfile,
                onNavigateToPipelineDebug = onNavigateToPipelineDebug
            )
        }
    }

    // Clear Data Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = {
                Text(
                    "Clear All Data?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.Error
                )
            },
            text = {
                Text(
                    "This will permanently delete all your locations, places, and visits. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = VoyagerColors.Error
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = VoyagerColors.OnSurfaceVariant)
                }
            },
            containerColor = VoyagerColors.SurfaceOverlay,
            tonalElevation = 8.dp
        )
    }

}

// ============================================================================
// GENERAL TAB
// ============================================================================

@Composable
private fun GeneralTabContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigateToCategories: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToDeveloperProfile: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onNavigateToReliability: () -> Unit,
    onNavigateToMileage: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Voyager Pro ────────────────────────────────────────────────
        item {
            VoyagerCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 0.dp,
                variant = CardVariant.HIGHLIGHTED
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPaywall() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = VoyagerColors.Premium,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voyager Pro",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "Evidence, mileage, insights & more",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Profile Preset ─────────────────────────────────────────────
        item {
            SectionHeader(title = "Profile Preset")
            Spacer(modifier = Modifier.height(8.dp))
            val presets = listOf(
                Triple("BATTERY_SAVER", "Battery Saver", "Minimal tracking, maximum battery life"),
                Triple("DAILY_COMMUTER", "Daily Commuter", "Balanced for daily routines"),
                Triple("CYCLIST_RIDER", "Cyclist / Rider", "Optimized for cycling and riding"),
                Triple("PRIVACY_MAX", "Privacy Max", "Minimal data collection"),
                Triple("PRECISION_MAX", "Precision Max", "Highest accuracy, more battery use"),
                Triple("CITY_EXPLORER", "City Explorer", "Short trips, walking-focused")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (id, name, description) ->
                    PresetCard(
                        name = name,
                        description = description,
                        isSelected = uiState.settings.activePreset == id,
                        onClick = { viewModel.applyPreset(id) }
                    )
                }
            }
        }

        // ── Tracking ───────────────────────────────────────────────────
        item {
            SectionHeader(title = "Tracking")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Tracking Enabled",
                    subtitle = "Enable continuous location tracking",
                    checked = uiState.settings.trackingEnabled,
                    onCheckedChange = { viewModel.updateSetting("trackingEnabled", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Sampling Interval",
                    value = (uiState.settings.customSamplingIntervalMs / 1000).toFloat(),
                    valueLabel = "${uiState.settings.customSamplingIntervalMs / 1000}s",
                    range = 10f..300f,
                    steps = 28,
                    onValueChange = { viewModel.updateSetting("customSamplingIntervalMs", (it * 1000).toLong()) }
                )
            }
        }

        // ── Tracking Quality ───────────────────────────────────────────
        item {
            SectionHeader(title = "Tracking Quality")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSliderRow(
                    title = "Movement Detection Confidence",
                    subtitle = "Higher = fewer missed locations but more battery use",
                    value = uiState.settings.arConfidenceThreshold.toFloat(),
                    valueLabel = "${uiState.settings.arConfidenceThreshold}%",
                    range = 25f..95f,
                    steps = 13,
                    onValueChange = { viewModel.updateSetting("arConfidenceThreshold", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Step Counting",
                    subtitle = "Count steps for walk/run detection",
                    checked = uiState.settings.stepCountingEnabled,
                    onCheckedChange = { viewModel.updateSetting("stepCountingEnabled", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Speed Heuristic",
                    subtitle = "Use speed to help classify transport mode",
                    checked = uiState.settings.speedHeuristicEnabled,
                    onCheckedChange = { viewModel.updateSetting("speedHeuristicEnabled", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Step Rate Fusion",
                    subtitle = "Combine step rate with activity recognition",
                    checked = uiState.settings.stepRateFusionEnabled,
                    onCheckedChange = { viewModel.updateSetting("stepRateFusionEnabled", it) }
                )
            }
        }

        // ── Notifications ──────────────────────────────────────────────
        item {
            SectionHeader(title = "Notifications")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Tracking Status",
                    subtitle = "Show persistent notification while tracking",
                    checked = uiState.settings.trackingStatusNotificationEnabled,
                    onCheckedChange = { viewModel.updateSetting("trackingStatusNotificationEnabled", it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Daily Insights",
                    subtitle = "Daily summary notification",
                    checked = uiState.settings.dailyInsightsEnabled,
                    onCheckedChange = { viewModel.updateSetting("dailyInsightsEnabled", it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Weekly Insights",
                    subtitle = "Weekly recap notification",
                    checked = uiState.settings.weeklyInsightsEnabled,
                    onCheckedChange = { viewModel.updateSetting("weeklyInsightsEnabled", it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Anomaly Alerts",
                    subtitle = "Notify when unusual patterns detected",
                    checked = uiState.settings.anomalyAlertsEnabled,
                    onCheckedChange = { viewModel.updateSetting("anomalyAlertsEnabled", it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Place Confirmation",
                    subtitle = "Ask to confirm low-confidence places",
                    checked = uiState.settings.placeConfirmationPromptsEnabled,
                    onCheckedChange = { viewModel.updateSetting("placeConfirmationPromptsEnabled", it) }
                )
            }
        }

        // ── Sleep Schedule ─────────────────────────────────────────────
        item {
            SleepScheduleSection(
                sleepDetectionEnabled = uiState.settings.sleepDetectionEnabled,
                sleepWindowStartHour = uiState.settings.sleepWindowStartHour,
                sleepWindowEndHour = uiState.settings.sleepWindowEndHour,
                motionDetectionEnabled = uiState.settings.motionDetectionEnabled,
                sleepScheduleDisplay = formatSleepScheduleDisplay(
                    uiState.settings.sleepWindowStartHour,
                    uiState.settings.sleepWindowEndHour
                ),
                estimatedBatterySavings = calculateBatterySavingsDisplay(
                    uiState.settings.sleepWindowStartHour,
                    uiState.settings.sleepWindowEndHour
                ),
                motionDetectionAvailable = true,
                onUpdateSleepModeEnabled = { viewModel.updateSetting("sleepDetectionEnabled", it) },
                onUpdateSleepStartHour = { viewModel.updateSetting("sleepWindowStartHour", it) },
                onUpdateSleepEndHour = { viewModel.updateSetting("sleepWindowEndHour", it) },
                onUpdateMotionDetectionEnabled = { viewModel.updateSetting("motionDetectionEnabled", it) }
            )
        }

        // ── Tax & reports ──────────────────────────────────────────────
        item {
            SectionHeader(title = "Tax & reports")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToMileage() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mileage log",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "Classify drives & export a tax PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── About & Feedback ───────────────────────────────────────────
        item {
            SectionHeader(title = "About & Feedback")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToReliability() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tracking reliability",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "OEM check & keep tracking running",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant
                    )
                }
                HorizontalDivider(color = VoyagerColors.SurfaceVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDeveloperProfile() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "About Voyager & Developer",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "Story, philosophy, and get in touch",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant
                    )
                }
                HorizontalDivider(color = VoyagerColors.SurfaceVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToFeedback() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Send Feedback",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "Bug reports, feature ideas, kind words",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant
                    )
                }
                HorizontalDivider(color = VoyagerColors.SurfaceVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToOpenSourceLicenses() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Open Source Licenses",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// DETECTION TAB
// ============================================================================

@Composable
private fun DetectionTabContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Place Detection ────────────────────────────────────────────
        item {
            SectionHeader(title = "Place Detection")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSliderRow(
                    title = "Min Dwell Time",
                    subtitle = "Minimum time to recognize a place visit",
                    value = uiState.settings.minDwellMinutes.toFloat(),
                    valueLabel = "${uiState.settings.minDwellMinutes} min",
                    range = 1f..30f,
                    steps = 28,
                    onValueChange = { viewModel.updateSetting("minDwellMinutes", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Place Radius",
                    subtitle = "Geofence radius for place boundaries",
                    value = uiState.settings.placeRadiusM.toFloat(),
                    valueLabel = "${uiState.settings.placeRadiusM}m",
                    range = 20f..300f,
                    steps = 27,
                    onValueChange = { viewModel.updateSetting("placeRadiusM", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Entry Hysteresis",
                    subtitle = "Consecutive in-range samples before confirming entry",
                    value = uiState.settings.entryHysteresisCount.toFloat(),
                    valueLabel = "${uiState.settings.entryHysteresisCount} samples",
                    range = 1f..5f,
                    steps = 3,
                    onValueChange = { viewModel.updateSetting("entryHysteresisCount", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Exit Hysteresis",
                    subtitle = "Consecutive out-of-range samples before confirming exit",
                    value = uiState.settings.exitHysteresisCount.toFloat(),
                    valueLabel = "${uiState.settings.exitHysteresisCount} samples",
                    range = 1f..6f,
                    steps = 4,
                    onValueChange = { viewModel.updateSetting("exitHysteresisCount", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Exit Buffer",
                    subtitle = "Extra distance beyond place radius before exit detection",
                    value = uiState.settings.exitBufferM.toFloat(),
                    valueLabel = "${uiState.settings.exitBufferM}m",
                    range = 10f..100f,
                    steps = 8,
                    onValueChange = { viewModel.updateSetting("exitBufferM", it.toInt()) }
                )
            }
        }

        // ── Activity Recognition ───────────────────────────────────────
        item {
            SectionHeader(title = "Activity Recognition")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Activity Recognition",
                    subtitle = "Detect walking, driving, stationary to improve accuracy",
                    checked = uiState.settings.activityRecognitionEnabled,
                    onCheckedChange = { viewModel.updateSetting("activityRecognitionEnabled", it) }
                )

                if (uiState.settings.activityRecognitionEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsToggleRow(
                        title = "Motion Detection",
                        subtitle = "Resume tracking if motion detected during sleep mode",
                        checked = uiState.settings.motionDetectionEnabled,
                        onCheckedChange = { viewModel.updateSetting("motionDetectionEnabled", it) }
                    )
                }
            }
        }

        // ── Auto Discovery ─────────────────────────────────────────────
        item {
            SectionHeader(title = "Auto Discovery")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Auto-Discover Places",
                    subtitle = "Automatically discover new places from location data",
                    checked = uiState.settings.autoDiscoveryEnabled,
                    onCheckedChange = { viewModel.updateSetting("autoDiscoveryEnabled", it) }
                )

                if (uiState.settings.autoDiscoveryEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSliderRow(
                        title = "Discovery Interval",
                        subtitle = "How often to scan for new places",
                        value = uiState.settings.discoveryIntervalHours.toFloat(),
                        valueLabel = "${uiState.settings.discoveryIntervalHours}h",
                        range = 1f..24f,
                        steps = 22,
                        onValueChange = { viewModel.updateSetting("discoveryIntervalHours", it.toInt()) }
                    )
                }
            }
        }

        // ── Geocoding Providers ────────────────────────────────────────
        item {
            SectionHeader(title = "Geocoding Providers")
            Spacer(modifier = Modifier.height(8.dp))
            GeocodingProvidersSection(
                providerOrder = uiState.settings.providerOrder,
                onToggleProvider = { id, enabled ->
                    val currentOrder = uiState.settings.providerOrder.toMutableList()
                    if (enabled && id !in currentOrder) {
                        currentOrder.add(id)
                    } else if (!enabled) {
                        currentOrder.remove(id)
                    }
                    viewModel.updateSetting("providerOrder", currentOrder)
                }
            )
        }

        // ── Geocoding Settings ─────────────────────────────────────────
        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Auto-Geocode New Places",
                    subtitle = "Automatically resolve names for new places",
                    checked = uiState.settings.autoGeocodeNewPlaces,
                    onCheckedChange = { viewModel.updateSetting("autoGeocodeNewPlaces", it) }
                )
            }
        }
    }
}

// ============================================================================
// PRIVACY & DATA TAB
// ============================================================================

@Composable
private fun PrivacyDataTabContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onShowClearDataDialog: () -> Unit,
    onShowExportDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Privacy ────────────────────────────────────────────────────
        item {
            SectionHeader(title = "Privacy")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                // Encryption is mandatory in v1 — passphrase derives from a non-extractable
                // Android Keystore key, so the database can only be opened on this device.
                SettingsInfoRow(
                    title = "Database Encryption",
                    subtitle = "Always on — your database is encrypted with a key tied to this device.",
                    valueLabel = "ON"
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Strip Coordinates on Export",
                    subtitle = "Remove exact coordinates from exported data",
                    checked = uiState.settings.stripExactCoordinatesOnExport,
                    onCheckedChange = { viewModel.updateSetting("stripExactCoordinatesOnExport", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Include Raw Samples in Export",
                    subtitle = "Include raw location samples when exporting",
                    checked = uiState.settings.exportIncludeRawSamples,
                    onCheckedChange = { viewModel.updateSetting("exportIncludeRawSamples", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Hide in App Switcher",
                    subtitle = "Blocks screenshots and hides your timeline in the recents view",
                    checked = uiState.settings.flagSecureEnabled,
                    onCheckedChange = { viewModel.updateSetting("flagSecureEnabled", it) }
                )
            }
        }

        // ── Data Retention ─────────────────────────────────────────────
        item {
            SectionHeader(title = "Data Retention")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSliderRow(
                    title = "Raw Samples",
                    subtitle = "Location samples, activity readings",
                    value = uiState.settings.rawSampleRetentionDays.toFloat(),
                    valueLabel = "${uiState.settings.rawSampleRetentionDays} days",
                    range = 7f..365f,
                    steps = 50,
                    onValueChange = { viewModel.updateSetting("rawSampleRetentionDays", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Derived Data",
                    subtitle = "Visits, segments, analytics",
                    value = uiState.settings.derivedDataRetentionDays.toFloat(),
                    valueLabel = "${uiState.settings.derivedDataRetentionDays} days",
                    range = 30f..730f,
                    steps = 50,
                    onValueChange = { viewModel.updateSetting("derivedDataRetentionDays", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Correction Feedback",
                    subtitle = "User corrections and learning data",
                    value = uiState.settings.correctionFeedbackRetentionDays.toFloat(),
                    valueLabel = "${uiState.settings.correctionFeedbackRetentionDays} days",
                    range = 30f..365f,
                    steps = 32,
                    onValueChange = { viewModel.updateSetting("correctionFeedbackRetentionDays", it.toInt()) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Auto Cleanup",
                    subtitle = "Automatically remove data past retention limits",
                    checked = uiState.settings.autoCleanupEnabled,
                    onCheckedChange = { viewModel.updateSetting("autoCleanupEnabled", it) }
                )
            }
        }

        // ── Data Management ────────────────────────────────────────────
        item {
            SectionHeader(title = "Data Management")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VoyagerButton(
                        onClick = onShowExportDialog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export & Import")
                    }

                    VoyagerOutlinedButton(
                        onClick = onShowClearDataDialog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = VoyagerColors.Error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Data", color = VoyagerColors.Error)
                    }
                }
            }
        }

        // Export message / error feedback
        uiState.exportMessage?.let { message ->
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.AccentGreen
                    )
                }
            }
        }

        uiState.errorMessage?.let { error ->
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.Error
                    )
                }
            }
        }
    }
}

// ============================================================================
// ADVANCED TAB
// ============================================================================

@Composable
private fun AdvancedTabContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isDeveloperMode: Boolean,
    developerModeManager: DeveloperModeManager,
    versionTapMessage: String?,
    onVersionTapMessage: (String?) -> Unit,
    onNavigateToDebugDataInsertion: () -> Unit,
    onNavigateToDeveloperProfile: () -> Unit,
    onNavigateToPipelineDebug: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Timeline ───────────────────────────────────────────────────
        item {
            SectionHeader(title = "Timeline")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Show Gap Segments",
                    subtitle = "Display gaps in timeline when tracking is interrupted",
                    checked = uiState.settings.showGapSegments,
                    onCheckedChange = { viewModel.updateSetting("showGapSegments", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Show Low Confidence Segments",
                    subtitle = "Display segments with low inference confidence",
                    checked = uiState.settings.showLowConfidenceSegments,
                    onCheckedChange = { viewModel.updateSetting("showLowConfidenceSegments", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Unify Travel Segments",
                    subtitle = "Combine bike/walk/drive into a single travel segment with per-leg breakdown",
                    checked = uiState.settings.unifyTravelSegments,
                    onCheckedChange = { viewModel.updateSetting("unifyTravelSegments", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Min Segment Duration",
                    subtitle = "Ignore segments shorter than this",
                    value = (uiState.settings.minSegmentDurationMs / 1000).toFloat(),
                    valueLabel = "${uiState.settings.minSegmentDurationMs / 1000}s",
                    range = 10f..300f,
                    steps = 28,
                    onValueChange = { viewModel.updateSetting("minSegmentDurationMs", (it * 1000).toLong()) }
                )
            }
        }

        // ── Map ────────────────────────────────────────────────────────
        item {
            SectionHeader(title = "Map")
            Spacer(modifier = Modifier.height(8.dp))
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "Route Polylines",
                    subtitle = "Show route lines on the map",
                    checked = uiState.settings.showRoutePolylines,
                    onCheckedChange = { viewModel.updateSetting("showRoutePolylines", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Color by Transport Mode",
                    subtitle = "Color routes by walk, drive, cycle, etc.",
                    checked = uiState.settings.routeColorByTransportMode,
                    onCheckedChange = { viewModel.updateSetting("routeColorByTransportMode", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Visit Markers",
                    subtitle = "Show markers for visited places",
                    checked = uiState.settings.showVisitMarkers,
                    onCheckedChange = { viewModel.updateSetting("showVisitMarkers", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Marker Numbering",
                    subtitle = "Number visit markers chronologically",
                    checked = uiState.settings.visitMarkerNumbering,
                    onCheckedChange = { viewModel.updateSetting("visitMarkerNumbering", it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    title = "Cluster Markers at Zoom",
                    subtitle = "Cluster nearby markers below this zoom level",
                    value = uiState.settings.clusterMarkersAtZoom.toFloat(),
                    valueLabel = "Zoom ${uiState.settings.clusterMarkersAtZoom}",
                    range = 8f..18f,
                    steps = 9,
                    onValueChange = { viewModel.updateSetting("clusterMarkersAtZoom", it.toInt()) }
                )
            }
        }

        // Battery Saver / Charging Boost toggles intentionally not exposed in v1 —
        // their backing logic isn't wired (the threshold/flag would only be persisted,
        // not acted on). Re-add this section once AdaptiveSamplingPolicy consults them.


        // ── Reset ──────────────────────────────────────────────────────
        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                VoyagerButton(
                    onClick = { viewModel.resetToDefaults() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset to Defaults")
                }
            }
        }

        // ── Debug Tools (Developer Mode only) ──────────────────────────
        if (isDeveloperMode) {
            item {
                SectionHeader(title = "Debug Tools")
                Spacer(modifier = Modifier.height(8.dp))
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "Show Pipeline Latency",
                        subtitle = "Display processing latency overlay",
                        checked = uiState.settings.showPipelineLatency,
                        onCheckedChange = { viewModel.updateSetting("showPipelineLatency", it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Sample Accuracy Overlay",
                        subtitle = "Show accuracy circles on map",
                        checked = uiState.settings.showSampleAccuracyOverlay,
                        onCheckedChange = { viewModel.updateSetting("showSampleAccuracyOverlay", it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Confidence Scores",
                        subtitle = "Show confidence values on timeline segments",
                        checked = uiState.settings.showConfidenceScores,
                        onCheckedChange = { viewModel.updateSetting("showConfidenceScores", it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Log Pipeline Decisions",
                        subtitle = "Log detailed pipeline decision trace",
                        checked = uiState.settings.logPipelineDecisions,
                        onCheckedChange = { viewModel.updateSetting("logPipelineDecisions", it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Export Diagnostics",
                        subtitle = "Include diagnostic data in exports",
                        checked = uiState.settings.exportDiagnostics,
                        onCheckedChange = { viewModel.updateSetting("exportDiagnostics", it) }
                    )
                }
            }

            item {
                VoyagerCard(
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
                                tint = VoyagerColors.AccentAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Debug Data Insertion",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VoyagerColors.OnSurface
                                )
                                Text(
                                    text = "Insert test data for development",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VoyagerColors.OnSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go",
                            tint = VoyagerColors.AccentAmber
                        )
                    }
                }
            }

            item {
                VoyagerCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToPipelineDebug
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
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = VoyagerColors.AccentAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Pipeline Debug Panel",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VoyagerColors.OnSurface
                                )
                                Text(
                                    text = "Live pipeline state, sampling, visit detection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VoyagerColors.OnSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go",
                            tint = VoyagerColors.AccentAmber
                        )
                    }
                }
            }
        }

        // ── About ──────────────────────────────────────────────────────
        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Flight,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Voyager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0",
                        style = MonoTimestamp,
                        color = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.clickable {
                            val remainingTaps = developerModeManager.registerTap()
                            onVersionTapMessage(
                                remainingTaps?.let {
                                    "$it more tap${if (it > 1) "s" else ""} for developer mode"
                                } ?: "Developer mode enabled!"
                            )
                        }
                    )

                    versionTapMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        VoyagerBadge(
                            text = message,
                            color = VoyagerColors.PrimaryContainer,
                            contentColor = VoyagerColors.Primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Your location story, told by you, kept by you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf("No Cloud", "No Accounts", "No Ads", "Open Source").forEach { label ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        VoyagerColors.PrimaryContainer,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VoyagerColors.Primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Powered by OpenStreetMap. Runs entirely on your device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoyagerColors.OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    VoyagerOutlinedButton(
                        onClick = onNavigateToDeveloperProfile,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About Developer")
                    }
                }
            }
        }
    }
}

// ============================================================================
// REUSABLE SETTINGS COMPONENTS
// ============================================================================

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.OnSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VoyagerColors.Primary,
                checkedTrackColor = VoyagerColors.PrimaryContainer,
                uncheckedThumbColor = VoyagerColors.OnSurfaceVariant,
                uncheckedTrackColor = VoyagerColors.SurfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String? = null,
    valueLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.OnSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.AccentGreen
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = valueLabel,
                style = MonoStatSmall,
                color = VoyagerColors.Primary
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = VoyagerColors.Primary,
                activeTrackColor = VoyagerColors.Primary,
                inactiveTrackColor = VoyagerColors.SurfaceVariant
            )
        )
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

private fun formatSleepScheduleDisplay(startHour: Int, endHour: Int): String {
    val startPeriod = if (startHour >= 12) "PM" else "AM"
    val endPeriod = if (endHour >= 12) "PM" else "AM"
    val displayStartHour = if (startHour > 12) startHour - 12 else if (startHour == 0) 12 else startHour
    val displayEndHour = if (endHour > 12) endHour - 12 else if (endHour == 0) 12 else endHour
    return "${displayStartHour}:00 $startPeriod - ${displayEndHour}:00 $endPeriod"
}

private fun calculateBatterySavingsDisplay(startHour: Int, endHour: Int): Int {
    val sleepDuration = if (endHour > startHour) {
        endHour - startHour
    } else {
        (24 - startHour) + endHour
    }
    return (sleepDuration * 4).coerceIn(0, 100)
}
