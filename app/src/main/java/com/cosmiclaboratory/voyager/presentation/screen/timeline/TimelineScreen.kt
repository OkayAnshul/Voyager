package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.cosmiclaboratory.voyager.domain.model.DayAnalytics
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.presentation.components.RenamePlaceDialog
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
 * Timeline Screen - Matrix UI
 *
 * Enhanced with:
 * - Date selector synced with Map
 * - Category visibility filtering
 * - "View on Map" for each segment
 * - Inline place reviews
 * - Matrix theme styling
 */
@Composable
fun TimelineScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    viewModel: TimelineViewModel = hiltViewModel(),
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
    var placeToRename by remember { mutableStateOf<Place?>(null) }

    // Rename dialog
    placeToRename?.let { place ->
        RenamePlaceDialog(
            place = place,
            onDismiss = { placeToRename = null },
            onRename = { customName ->
                viewModel.renamePlace(place.id, customName)
            },
            onRevertToAutomatic = {
                viewModel.revertPlaceToAutomatic(place.id)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Date Selector Bar (synced with Map)
        DateSelectorBar(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                sharedUiState.selectDate(date)
            },
            onJumpToMap = {
                navController?.navigate(VoyagerDestination.Map.route)
            },
            onRefresh = {
                viewModel.refreshTimeline()
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
                            text = "LOADING TIMELINE...",
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

            uiState.visibleSegments.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateMessage(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(64.dp)
                            )
                        },
                        title = "NO ACTIVITY FOR THIS DAY",
                        message = "No timeline data available for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                        actionButton = if (selectedDate == LocalDate.now()) {
                            {
                                MatrixButton(onClick = { /* Toggle tracking */ }) {
                                    Text("START TRACKING")
                                }
                            }
                        } else null
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current Location Card (Real-time) - Show only for today
                    if (selectedDate == LocalDate.now() && uiState.currentPlace != null) {
                        item {
                            CurrentLocationCard(
                                currentPlace = uiState.currentPlace,
                                currentVisitDuration = uiState.currentVisitDuration,
                                isTracking = uiState.isTracking
                            )
                        }
                    }

                    // Day summary
                    uiState.dayAnalytics?.let { analytics ->
                        item {
                            DaySummaryCard(analytics)
                        }
                    }

                    // Timeline segments (filtered by category visibility)
                    items(uiState.visibleSegments) { segment ->
                        TimelineSegmentCard(
                            segment = segment,
                            onLongPress = { place ->
                                placeToRename = place
                            },
                            onViewOnMap = {
                                // Store place in SharedUiState for Map to consume
                                sharedUiState.selectPlaceForMap(segment.place)
                                // Navigate to Map (Map will auto-center on place)
                                navController?.navigate(VoyagerDestination.Map.route)
                            }
                        )
                    }

                    // Category filter info (if some categories are hidden)
                    if (uiState.visibleSegments.size != uiState.timelineSegments.size) {
                        item {
                            MatrixCard {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = Teal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${uiState.visibleSegments.size}/${uiState.timelineSegments.size} VISITS VISIBLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Teal
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    MatrixTextButton(
                                        onClick = {
                                            navController?.navigate(VoyagerDestination.Categories.route)
                                        }
                                    ) {
                                        Text("MANAGE")
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
    onJumpToMap: () -> Unit,
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
                        text = when {
                            selectedDate == LocalDate.now() -> "TODAY"
                            selectedDate == LocalDate.now().minusDays(1) -> "YESTERDAY"
                            else -> selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")).uppercase()
                        },
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
                if (selectedDate != LocalDate.now()) {
                    MatrixTextButton(
                        onClick = { onDateSelected(LocalDate.now()) }
                    ) {
                        Text("TODAY")
                    }
                }

                // Jump to Map button
                MatrixIconButton(onClick = onJumpToMap) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Jump to Map"
                    )
                }
            }
        }
    }
}

/**
 * Day Summary Card - Matrix styled
 */
@Composable
private fun DaySummaryCard(analytics: DayAnalytics) {
    MatrixCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAY SUMMARY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            MatrixDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem("PLACES", "${analytics.placesVisited}")
                MatrixVerticalDivider(modifier = Modifier.height(48.dp))
                SummaryItem("DISTANCE", "${String.format("%.1f", analytics.distanceTraveled / 1000)} km")
                MatrixVerticalDivider(modifier = Modifier.height(48.dp))
                SummaryItem("TIME", formatDuration(analytics.totalTimeTracked))
            }
        }
    }
}

/**
 * Summary Item
 */
@Composable
private fun SummaryItem(label: String, value: String) {
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
 * Current Location Card - Matrix styled
 */
@Composable
private fun CurrentLocationCard(
    currentPlace: Place?,
    currentVisitDuration: Long,
    isTracking: Boolean
) {
    MatrixCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pulsing location indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                PulsingDot(size = 24.dp, color = Teal)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CURRENT LOCATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = Teal,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = (currentPlace?.osmSuggestedName ?: currentPlace?.name ?: "Tracking...").uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )

                currentPlace?.address?.let { address ->
                    Text(
                        text = address.split(",").firstOrNull() ?: address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentVisitDuration > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Duration: ${formatDuration(currentVisitDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal
                    )
                }
            }
        }
    }
}

/**
 * Timeline Segment Card - Matrix styled with "View on Map" button
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineSegmentCard(
    segment: TimelineSegment,
    onLongPress: (Place) -> Unit,
    onViewOnMap: () -> Unit
) {
    MatrixCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(segment.place) }
            )
    ) {
        Column {
            // Place header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (segment.place.osmSuggestedName ?: segment.place.name).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Teal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    segment.place.address?.let { address ->
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // Category badge
                MatrixBadge(count = segment.place.category.displayName.uppercase())
            }

            Spacer(modifier = Modifier.height(12.dp))
            MatrixDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Time range and duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = segment.timeRange.formatTimeRange(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = segment.timeRange.formatDuration(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Teal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Multiple visits indicator
            if (segment.visits.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${segment.visits.size} VISITS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal
                    )
                }
            }

            // Travel info to next place
            if (segment.distanceToNext != null && segment.travelTimeToNext != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = TealDim,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${segment.formatDistanceToNext()} • ${segment.formatTravelTimeToNext()} to next",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "View on Map" button
            MatrixButton(
                onClick = onViewOnMap,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("VIEW ON MAP")
            }
        }
    }
}

/**
 * Format duration in milliseconds
 */
private fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
