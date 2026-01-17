package com.cosmiclaboratory.voyager.presentation.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.NavController
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.presentation.components.OpenStreetMapView
import com.cosmiclaboratory.voyager.presentation.components.PermissionRequestCard
import com.cosmiclaboratory.voyager.presentation.components.PlaceMarkerBottomSheet
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerDestination
import com.cosmiclaboratory.voyager.presentation.state.SharedUiState
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.Teal
import com.cosmiclaboratory.voyager.ui.theme.TealDim
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * EntryPoint for accessing SharedUiState from Composables
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SharedUiStateEntryPoint {
    fun sharedUiState(): SharedUiState
}

/**
 * Map Screen - Matrix UI
 *
 * Enhanced with:
 * - Date selector synced with Timeline
 * - Category visibility filtering
 * - Cross-navigation to Timeline
 * - Matrix green markers and styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    viewModel: MapViewModel = hiltViewModel(),
    navController: NavController? = null
) {
    val context = LocalContext.current
    val sharedUiState = remember {
        EntryPointAccessors.fromApplication<SharedUiStateEntryPoint>(
            context.applicationContext
        ).sharedUiState()
    }

    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by sharedUiState.selectedDate.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Date Selector Bar (synced with Timeline)
        DateSelectorBar(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                sharedUiState.selectDate(date)
            },
            onJumpToTimeline = {
                navController?.navigate(VoyagerDestination.Timeline.route)
            },
            onRefresh = {
                viewModel.refreshMapData()
            }
        )

        when {
            uiState.isLoading -> {
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
                            text = "LOADING MAP DATA...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MatrixCard {
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
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            else -> {
                if (permissionStatus != PermissionStatus.ALL_GRANTED) {
                    // Permission request
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PermissionRequestCard(
                            permissionStatus = permissionStatus,
                            onRequestPermissions = { },
                            onOpenSettings = { }
                        )
                    }
                } else if (uiState.locations.isEmpty() && uiState.places.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateMessage(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = Teal,
                                    modifier = Modifier.size(64.dp)
                                )
                            },
                            title = "NO LOCATION DATA",
                            message = "Start location tracking to see your places and routes on the map",
                            actionButton = {
                                MatrixButton(onClick = { viewModel.toggleLocationTracking() }) {
                                    Text("START TRACKING")
                                }
                            }
                        )
                    }
                } else {
                    // Map view with floating controls
                    Box(modifier = Modifier.fillMaxSize()) {
                        // OpenStreetMap with filtered places
                        OpenStreetMapView(
                            modifier = Modifier.fillMaxSize(),
                            center = uiState.mapCenter,
                            zoomLevel = uiState.zoomLevel,
                            locations = uiState.locations,
                            places = uiState.visiblePlaces, // Filtered by category visibility
                            userLocation = uiState.userLocation,
                            currentPlace = uiState.currentPlace,
                            isTracking = uiState.isTracking,
                            showVisitCounts = true,
                            onPlaceClick = { place ->
                                viewModel.selectPlace(place)
                            },
                            onMapClick = { lat, lng ->
                                viewModel.onMapClick(lat, lng)
                            },
                            onMapReady = { mapView ->
                                viewModel.onMapReady(mapView)
                            }
                        )

                        // Tracking status badge (top-left)
                        if (uiState.isTracking) {
                            MatrixCard(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PulsingDot(size = 10.dp, color = Teal)
                                    Text(
                                        text = "LIVE TRACKING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Teal
                                    )
                                }
                            }
                        }

                        // Visible places count badge (top-right)
                        if (uiState.visiblePlaces.size != uiState.places.size) {
                            MatrixCard(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "${uiState.visiblePlaces.size}/${uiState.places.size} VISIBLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                            }
                        }

                        // Center on user FAB (bottom-right)
                        if (uiState.userLocation != null) {
                            FloatingActionButton(
                                onClick = { viewModel.centerOnUser() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Teal
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Center on my location"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Place details bottom sheet with Timeline navigation
        uiState.selectedPlace?.let { place ->
            EnhancedPlaceBottomSheet(
                place = place,
                visits = uiState.selectedPlaceVisits,
                onDismiss = { viewModel.selectPlace(null) },
                onViewInTimeline = {
                    // Navigate to Timeline and select this place's last visit date
                    navController?.navigate(VoyagerDestination.Timeline.route)
                }
            )
        }
    }
}

/**
 * Date Selector Bar
 *
 * Synced date selection between Map and Timeline screens.
 */
@Composable
private fun DateSelectorBar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onJumpToTimeline: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    MatrixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date navigation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onDateSelected(selectedDate.minusDays(1)) }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous day",
                        tint = Teal
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Teal
                    )
                    Text(
                        text = selectedDate.dayOfWeek.toString().uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TealDim
                    )
                }

                IconButton(
                    onClick = {
                        if (selectedDate.isBefore(LocalDate.now())) {
                            onDateSelected(selectedDate.plusDays(1))
                        }
                    },
                    enabled = selectedDate.isBefore(LocalDate.now())
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next day",
                        tint = if (selectedDate.isBefore(LocalDate.now())) Teal else TealDim
                    )
                }
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Refresh button
                MatrixIconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }

                // Today button
                MatrixTextButton(
                    onClick = { onDateSelected(LocalDate.now()) }
                ) {
                    Text("TODAY")
                }

                // Jump to Timeline button
                MatrixIconButton(onClick = onJumpToTimeline) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Jump to Timeline"
                    )
                }
            }
        }
    }
}

/**
 * Enhanced Place Bottom Sheet with Timeline navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedPlaceBottomSheet(
    place: Place,
    visits: List<com.cosmiclaboratory.voyager.domain.model.Visit>,
    onDismiss: () -> Unit,
    onViewInTimeline: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (place.osmSuggestedName ?: place.name).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Teal,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Teal
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            MatrixDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Place details
            place.address?.let { address ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MatrixBadge(
                    count = "${visits.size} VISITS"
                )
                MatrixBadge(
                    count = place.category.displayName.uppercase()
                )
                if (place.confidence < 1.0f) {
                    MatrixBadge(
                        count = "${(place.confidence * 100).toInt()}% CONF"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MatrixButton(
                    onClick = onViewInTimeline,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIEW IN TIMELINE")
                }
            }

            // Visit History Section
            if (visits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                MatrixDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "VISIT HISTORY",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visits.take(10)) { visit ->  // Limit to 10 recent visits
                        VisitListItem(visit = visit)
                    }
                }

                if (visits.size > 10) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+ ${visits.size - 10} more visits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Individual visit list item showing visit details
 */
@Composable
private fun VisitListItem(visit: com.cosmiclaboratory.voyager.domain.model.Visit) {
    MatrixCard(padding = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                // Entry time
                Text(
                    text = visit.entryTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Teal
                )
                // Duration - now supports ongoing visits
                val durationMs = visit.duration
                if (durationMs > 0) {
                    val hours = durationMs / (1000 * 60 * 60)
                    val minutes = (durationMs / (1000 * 60)) % 60
                    val durationText = when {
                        hours > 0 -> "${hours}h ${minutes}m"
                        else -> "${minutes}m"
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show "Ongoing" badge for active visits
                        if (visit.isOngoing) {
                            Text(
                                text = "• ONGOING",
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Confidence badge
            if (visit.confidence < 1.0f) {
                MatrixBadge(count = "${(visit.confidence * 100).toInt()}%")
            }
        }
    }
}
