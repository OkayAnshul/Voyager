package com.cosmiclaboratory.voyager.presentation.screen.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.Teal

/**
 * Categories Screen - Matrix UI
 *
 * Manage per-category visibility settings.
 * Controls which categories show on Map, Timeline, and whether notifications are enabled.
 *
 * Default: All categories HIDDEN until user explicitly enables them.
 */
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedCategory by remember { mutableStateOf<PlaceCategory?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        MatrixCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "CATEGORY SETTINGS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Categories are hidden by default. Enable them below to show on Map and Timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stats
                Spacer(modifier = Modifier.height(12.dp))
                MatrixDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatChip(
                        label = "MAP",
                        count = uiState.visibleOnMapCount,
                        total = 15
                    )
                    StatChip(
                        label = "TIMELINE",
                        count = uiState.visibleOnTimelineCount,
                        total = 15
                    )
                    StatChip(
                        label = "NOTIFY",
                        count = uiState.notificationsEnabledCount,
                        total = 15
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MatrixButton(
                onClick = { viewModel.showAllCategories() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("SHOW ALL")
            }

            MatrixButton(
                onClick = { viewModel.hideAllCategories() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("HIDE ALL")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category List
        if (uiState.isLoading) {
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
                        text = "LOADING CATEGORIES...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = PlaceCategory.values().toList(),
                    key = { it.name }
                ) { category ->
                    val visibility = uiState.categorySettings[category] ?: CategoryVisibility()
                    val placesInCategory = uiState.categoryPlaces[category] ?: emptyList()
                    val isExpanded = expandedCategory == category

                    CategorySettingsCard(
                        category = category,
                        visibility = visibility,
                        places = placesInCategory,
                        isExpanded = isExpanded,
                        onToggleExpanded = {
                            expandedCategory = if (isExpanded) null else category
                        },
                        onVisibilityChange = { newVisibility ->
                            viewModel.updateCategoryVisibility(category, newVisibility)
                        },
                        onAssignPlaces = {
                            viewModel.showAssignDialog(category)
                        },
                        onRemovePlace = { place ->
                            viewModel.assignPlaceToCategory(place, PlaceCategory.UNKNOWN)
                        }
                    )
                }
            }
        }

        // Error display
        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
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
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Assignment Dialog
        if (uiState.showAssignDialog && uiState.selectedCategoryForAssignment != null) {
            AssignPlaceDialog(
                category = uiState.selectedCategoryForAssignment!!,
                availablePlaces = viewModel.getAssignablePlaces(uiState.selectedCategoryForAssignment!!),
                onDismiss = { viewModel.hideAssignDialog() },
                onAssignPlace = { place ->
                    viewModel.assignPlaceToCategory(place, uiState.selectedCategoryForAssignment!!)
                    viewModel.hideAssignDialog()
                }
            )
        }
    }
}

/**
 * Assignment Dialog
 *
 * Shows a dialog with list of places that can be assigned to a category.
 */
@Composable
private fun AssignPlaceDialog(
    category: PlaceCategory,
    availablePlaces: List<com.cosmiclaboratory.voyager.domain.model.Place>,
    onDismiss: () -> Unit,
    onAssignPlace: (com.cosmiclaboratory.voyager.domain.model.Place) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ASSIGN TO ${category.displayName.uppercase()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Select a place to assign to this category:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (availablePlaces.isEmpty()) {
                    Text(
                        text = "No places available to assign",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availablePlaces) { place ->
                            MatrixCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onAssignPlace(place) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = place.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Teal
                                        )
                                        Text(
                                            text = if (place.category == PlaceCategory.UNKNOWN) {
                                                "UNASSIGNED"
                                            } else {
                                                "Currently: ${place.category.displayName}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Assign",
                                        tint = Teal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    )
}

/**
 * Category Settings Card
 *
 * Individual card for each category with toggle switches and place management.
 */
@Composable
private fun CategorySettingsCard(
    category: PlaceCategory,
    visibility: CategoryVisibility,
    places: List<com.cosmiclaboratory.voyager.domain.model.Place>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onVisibilityChange: (CategoryVisibility) -> Unit,
    onAssignPlaces: () -> Unit,
    onRemovePlace: (com.cosmiclaboratory.voyager.domain.model.Place) -> Unit
) {
    MatrixCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header: Category name and icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category icon (use appropriate icon for each category)
                    Icon(
                        imageVector = getCategoryIcon(category),
                        contentDescription = null,
                        tint = if (visibility.isVisible()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )

                    Column {
                        Text(
                            text = category.displayName.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (visibility.isVisible()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    visibility.isCompletelyHidden() -> "● HIDDEN"
                                    visibility.showOnMap && visibility.showOnTimeline -> "▲ EVERYWHERE"
                                    visibility.showOnMap -> "◆ MAP ONLY"
                                    visibility.showOnTimeline -> "◆ TIMELINE ONLY"
                                    else -> "● HIDDEN"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MatrixBadge(count = "${places.size} PLACES")
                        }
                    }
                }

                // Expand/Collapse icon
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expanded content
            if (isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Assigned Places
                    if (places.isNotEmpty()) {
                        Text(
                            text = "ASSIGNED PLACES (${places.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            places.take(3).forEach { place ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${place.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onRemovePlace(place) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (places.size > 3) {
                                Text(
                                    text = "...and ${places.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Assign button
                    MatrixButton(
                        onClick = onAssignPlaces,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ASSIGN MORE PLACES")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            MatrixDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Switches
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Show on Map
                ToggleRow(
                    label = "SHOW ON MAP",
                    description = "Display ${category.displayName} places on the map",
                    checked = visibility.showOnMap,
                    onCheckedChange = {
                        onVisibilityChange(visibility.copy(showOnMap = it))
                    }
                )

                // Show on Timeline
                ToggleRow(
                    label = "SHOW ON TIMELINE",
                    description = "Display ${category.displayName} visits in timeline",
                    checked = visibility.showOnTimeline,
                    onCheckedChange = {
                        onVisibilityChange(visibility.copy(showOnTimeline = it))
                    }
                )

                // Enable Notifications
                ToggleRow(
                    label = "ENABLE NOTIFICATIONS",
                    description = "Get notified about ${category.displayName} visits",
                    checked = visibility.enableNotifications,
                    onCheckedChange = {
                        onVisibilityChange(visibility.copy(enableNotifications = it))
                    }
                )
            }
        }
    }
}

/**
 * Toggle Row
 *
 * Label + description + switch in a row.
 */
@Composable
private fun ToggleRow(
    label: String,
    description: String,
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
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * Stat Chip
 *
 * Shows count/total for each stat category.
 */
@Composable
private fun StatChip(
    label: String,
    count: Int,
    total: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MatrixBadge(count = "$count/$total")
    }
}

/**
 * Get icon for each category
 *
 * Maps PlaceCategory to Material Icon.
 */
private fun getCategoryIcon(category: PlaceCategory) = when (category) {
    PlaceCategory.HOME -> Icons.Default.Home
    PlaceCategory.WORK -> Icons.Default.Work
    PlaceCategory.GYM -> Icons.Default.FitnessCenter
    PlaceCategory.RESTAURANT -> Icons.Default.Restaurant
    PlaceCategory.SHOPPING -> Icons.Default.ShoppingCart
    PlaceCategory.ENTERTAINMENT -> Icons.Default.Movie
    PlaceCategory.HEALTHCARE -> Icons.Default.LocalHospital
    PlaceCategory.EDUCATION -> Icons.Default.School
    PlaceCategory.TRANSPORT -> Icons.Default.DirectionsBus
    PlaceCategory.TRAVEL -> Icons.Default.Flight
    PlaceCategory.OUTDOOR -> Icons.Default.Park
    PlaceCategory.SOCIAL -> Icons.Default.People
    PlaceCategory.SERVICES -> Icons.Default.Build
    PlaceCategory.UNKNOWN -> Icons.Default.QuestionMark
    PlaceCategory.CUSTOM -> Icons.Default.Star
}
