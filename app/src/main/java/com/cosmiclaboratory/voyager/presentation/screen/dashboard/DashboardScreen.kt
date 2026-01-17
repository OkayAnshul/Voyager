package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.presentation.theme.MatrixCard
import com.cosmiclaboratory.voyager.presentation.theme.MatrixButton
import com.cosmiclaboratory.voyager.presentation.theme.MatrixFilledButton
import com.cosmiclaboratory.voyager.presentation.theme.MatrixDivider
import com.cosmiclaboratory.voyager.presentation.theme.MatrixBadge
import com.cosmiclaboratory.voyager.presentation.theme.PulsingDot
import com.cosmiclaboratory.voyager.presentation.theme.LoadingDots
import com.cosmiclaboratory.voyager.ui.theme.Teal
import com.cosmiclaboratory.voyager.ui.theme.TealDim

/**
 * Dashboard Screen - Matrix UI Redesign
 *
 * Terminal-inspired dashboard with:
 * - Live status indicator with pulsing dot
 * - Collapsible sections for better organization
 * - Matrix green on black aesthetic
 * - Sharp corners and uppercase titles
 * - Inline review cards
 * - Event-driven updates (no polling)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    onNavigateToReview: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Collapsible sections state
    var expandedSections by remember {
        mutableStateOf(setOf("status", "stats", "current"))
    }

    // Real-time seconds update for current visit duration
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(uiState.isAtPlace) {
        if (uiState.isAtPlace) {
            while (true) {
                kotlinx.coroutines.delay(1000) // Update every second
                currentTime = System.currentTimeMillis()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DASHBOARD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDashboard() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Dashboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            // Loading state with Matrix-style loading dots
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LoadingDots()
                    Text(
                        text = "INITIALIZING DASHBOARD...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ==============================================================
                // LIVE STATUS BADGE
                // ==============================================================
                item {
                    LiveStatusBadge(
                        isTracking = uiState.isLocationTrackingActive,
                        currentPlace = uiState.currentPlace
                    )
                }

                // ==============================================================
                // TRACKING STATS SECTION (Collapsible)
                // ==============================================================
                item {
                    CollapsibleSection(
                        title = "TRACKING STATS",
                        isExpanded = "stats" in expandedSections,
                        onToggle = {
                            expandedSections = if ("stats" in expandedSections) {
                                expandedSections - "stats"
                            } else {
                                expandedSections + "stats"
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Stats Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Total Locations
                                MatrixCard(
                                    modifier = Modifier.weight(1f),
                                    padding = 12.dp
                                ) {
                                    Text(
                                        text = "LOCATIONS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = uiState.totalLocations.toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "GPS points",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Total Places
                                MatrixCard(
                                    modifier = Modifier.weight(1f),
                                    padding = 12.dp
                                ) {
                                    Text(
                                        text = "PLACES",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = uiState.totalPlaces.toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Unique",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Total Time
                            MatrixCard(
                                modifier = Modifier.fillMaxWidth(),
                                padding = 12.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "TOTAL TIME TRACKED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val hours = uiState.totalTimeTracked / (1000 * 60 * 60)
                                        val minutes =
                                            (uiState.totalTimeTracked % (1000 * 60 * 60)) / (1000 * 60)
                                        Text(
                                            text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==============================================================
                // CURRENT VISIT SECTION (Always visible when at place)
                // ==============================================================
                if (uiState.isAtPlace && uiState.currentPlace != null) {
                    item {
                        MatrixCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "CURRENT LOCATION",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    PulsingDot(size = 10.dp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Place name
                                Text(
                                    text = uiState.currentPlace!!.name.uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Duration with real-time seconds
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    // Calculate duration with real-time updates (triggers recomposition every second)
                                    @Suppress("UNUSED_VARIABLE")
                                    val tick = currentTime // Reference to trigger recomposition
                                    val totalSeconds = uiState.currentVisitDuration / 1000
                                    val hours = totalSeconds / 3600
                                    val mins = (totalSeconds % 3600) / 60
                                    val secs = totalSeconds % 60
                                    Text(
                                        text = when {
                                            hours > 0 -> "${hours}h ${mins}m ${secs}s"
                                            mins > 0 -> "${mins}m ${secs}s"
                                            else -> "${secs}s"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ==============================================================
                // PENDING REVIEWS (Inline - only if count > 0)
                // ==============================================================
                if (uiState.pendingReviewCount > 0) {
                    item {
                        MatrixCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onNavigateToReview
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
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "REVIEW PLACES",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${uiState.pendingReviewCount} places need review",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            MatrixBadge(count = uiState.pendingReviewCount.toString())
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Go to reviews",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ==============================================================
                // ADVANCED CATEGORY SETTINGS
                // ==============================================================
                item {
                    MatrixCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToCategories
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
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = "ADVANCED CATEGORY SETTINGS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Manage and assign places to categories",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Go to category settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ==============================================================
                // QUICK ACTIONS SECTION
                // ==============================================================
                item {
                    CollapsibleSection(
                        title = "QUICK ACTIONS",
                        isExpanded = "actions" in expandedSections,
                        onToggle = {
                            expandedSections = if ("actions" in expandedSections) {
                                expandedSections - "actions"
                            } else {
                                expandedSections + "actions"
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Location Tracking Toggle
                            MatrixCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "LOCATION TRACKING",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (uiState.isTracking) "● ACTIVE" else "○ STOPPED",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (uiState.isTracking) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    Switch(
                                        checked = uiState.isTracking,
                                        onCheckedChange = { viewModel.toggleLocationTracking() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }
                            }

                            // Place Detection Trigger
                            MatrixCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "PLACE DETECTION",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (uiState.isDetectingPlaces) {
                                                "ANALYZING LOCATIONS..."
                                            } else {
                                                "Process location data to find places"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MatrixFilledButton(
                                        onClick = { viewModel.triggerPlaceDetection() },
                                        enabled = !uiState.isDetectingPlaces
                                    ) {
                                        if (uiState.isDetectingPlaces) {
                                            LoadingDots(dotSize = 6.dp)
                                        } else {
                                            Text("DETECT")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Error message display (if any)
                uiState.errorMessage?.let { error ->
                    item {
                        MatrixCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

// ==============================================================================
// COMPOSABLE COMPONENTS
// ==============================================================================

/**
 * Live Status Badge
 *
 * Shows current tracking status with pulsing green dot when active.
 */
@Composable
private fun LiveStatusBadge(
    isTracking: Boolean,
    currentPlace: Place?
) {
    MatrixCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pulsing indicator when tracking
            if (isTracking) {
                PulsingDot(size = 12.dp, color = Teal)
            } else {
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Status text
            Column {
                Text(
                    text = when {
                        !isTracking -> "● TRACKING DISABLED"
                        currentPlace != null -> "▲ AT ${currentPlace.name.uppercase()}"
                        else -> "◆ TRACKING ACTIVE"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isTracking) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = when {
                        !isTracking -> "Enable tracking to monitor your journey"
                        currentPlace != null -> "Currently at this location"
                        else -> "Monitoring your location in real-time"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Collapsible Section
 *
 * Section with expand/collapse functionality for better organization.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        // Header (always visible)
        MatrixCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggle
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Content (collapsible)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                content()
            }
        }
    }
}
