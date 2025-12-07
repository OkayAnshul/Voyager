package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.DayAnalytics
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.presentation.components.RenamePlaceDialog
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimelineScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var placeToRename by remember { mutableStateOf<Place?>(null) }

    // Show rename dialog if place is selected
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
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Timeline",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { viewModel.refreshTimeline() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Date selector
        DateSelector(
            selectedDate = uiState.selectedDate,
            onDateSelected = { viewModel.selectDate(it) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            uiState.errorMessage != null -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            uiState.timelineEntries.isEmpty() -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No activity for this day",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            else -> {
                LazyColumn {
                    // Current Location Card (Real-time) - Show only for today
                    if (uiState.selectedDate == LocalDate.now() && uiState.currentLocation != null) {
                        item {
                            CurrentLocationCard(
                                currentPlace = uiState.currentPlace,
                                currentVisitDuration = uiState.currentVisitDuration,
                                isTracking = uiState.isTracking
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Day summary
                    uiState.dayAnalytics?.let { analytics ->
                        item {
                            DaySummaryCard(analytics)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Phase 2: Timeline Segments (if available and enabled)
                    if (uiState.useSegmentView && uiState.timelineSegments.isNotEmpty()) {
                        items(uiState.timelineSegments) { segment ->
                            TimelineSegmentCard(
                                segment = segment,
                                onLongPress = { place ->
                                    placeToRename = place
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        // Fallback: Legacy timeline entries
                        items(uiState.timelineEntries.filter { it.type != TimelineEntryType.DAY_SUMMARY }) { entry ->
                            TimelineEntryCard(
                                entry = entry,
                                onLongPress = { place ->
                                    placeToRename = place
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateSelected(selectedDate.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        
        Text(
            text = when {
                selectedDate == LocalDate.now() -> "Today"
                selectedDate == LocalDate.now().minusDays(1) -> "Yesterday"
                else -> selectedDate.toString()
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        IconButton(
            onClick = { onDateSelected(selectedDate.plusDays(1)) },
            enabled = selectedDate.isBefore(LocalDate.now())
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
private fun DaySummaryCard(analytics: DayAnalytics) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Day Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem("Places", "${analytics.placesVisited}")
                SummaryItem("Distance", "${String.format("%.1f", analytics.distanceTraveled / 1000)} km")
                SummaryItem("Time", formatDuration(analytics.totalTimeTracked))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineEntryCard(
    entry: TimelineEntry,
    onLongPress: (Place) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entry.place != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress(entry.place) }
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(getTimelineColor(entry.type)),
                contentAlignment = Alignment.Center
            ) {
                when (entry.type) {
                    TimelineEntryType.VISIT_START -> 
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                    TimelineEntryType.VISIT_END -> 
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                    TimelineEntryType.MOVEMENT -> 
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                    else -> {}
                }
            }
            
            if (entry.type != TimelineEntryType.DAY_SUMMARY) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            entry.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getTimelineColor(type: TimelineEntryType): Color {
    return when (type) {
        TimelineEntryType.VISIT_START -> MaterialTheme.colorScheme.primary
        TimelineEntryType.VISIT_END -> MaterialTheme.colorScheme.secondary
        TimelineEntryType.MOVEMENT -> MaterialTheme.colorScheme.tertiary
        TimelineEntryType.PLACE_DETECTED -> MaterialTheme.colorScheme.surface
        TimelineEntryType.DAY_SUMMARY -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun CurrentLocationCard(
    currentPlace: Place?,
    currentVisitDuration: Long,
    isTracking: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {}
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing location indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color.Green,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Current Location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currentPlace?.osmSuggestedName ?: currentPlace?.name ?: "Tracking...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineSegmentCard(
    segment: TimelineSegment,
    onLongPress: (Place) -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(segment.place) }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Place name
            Text(
                text = segment.place.osmSuggestedName ?: segment.place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // ISSUE #1: Display full address (not truncated)
            segment.place.address?.let { address ->
                Text(
                    text = address,  // Show complete address
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,  // Allow wrapping for long addresses
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = segment.timeRange.formatTimeRange(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = segment.timeRange.formatDuration(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ISSUE #1: Individual visit details when multiple visits grouped
            if (segment.visits.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Individual Visits:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                segment.visits.forEach { visit ->
                    val visitDuration = formatDuration(visit.duration)
                    val entryTime = visit.entryTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                    val exitTime = visit.exitTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "ongoing"
                    Text(
                        text = "• $entryTime - $exitTime ($visitDuration)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Travel info to next place
            if (segment.distanceToNext != null && segment.travelTimeToNext != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${segment.formatDistanceToNext()} • ${segment.formatTravelTimeToNext()} to next",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}