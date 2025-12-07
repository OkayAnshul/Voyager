package com.cosmiclaboratory.voyager.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory

/**
 * Phase 3: Dialog for naming/renaming places
 * Shows OSM suggestions and allows custom input
 */
@Composable
fun PlaceNameDialog(
    place: Place,
    onDismiss: () -> Unit,
    onSave: (String, PlaceCategory) -> Unit
) {
    var customName by remember { mutableStateOf(place.name) }
    var selectedCategory by remember { mutableStateOf(place.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Name This Place",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Show OSM suggestion if available
                if (!place.osmSuggestedName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "Suggested name:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                place.osmSuggestedName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Show OSM suggested category if different
                            place.osmSuggestedCategory?.let { osmCat ->
                                if (osmCat != place.category) {
                                    Text(
                                        "Suggested category: ${osmCat.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            customName = place.osmSuggestedName
                            place.osmSuggestedCategory?.let { selectedCategory = it }
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("Use suggested name")
                    }
                }

                // Show address if available
                if (!place.address.isNullOrBlank()) {
                    Text(
                        "Address: ${place.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Show location details
                Text(
                    "Location: ${String.format("%.4f", place.latitude)}, ${String.format("%.4f", place.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Custom name input
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Place Name") },
                    placeholder = { Text("e.g., Joe's Coffee Shop") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                // Category selector
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        listOf(
                            PlaceCategory.HOME,
                            PlaceCategory.WORK,
                            PlaceCategory.GYM,
                            PlaceCategory.RESTAURANT,
                            PlaceCategory.SHOPPING,
                            PlaceCategory.ENTERTAINMENT,
                            PlaceCategory.SOCIAL,
                            PlaceCategory.OUTDOOR,
                            PlaceCategory.TRANSPORT,
                            PlaceCategory.SERVICES,
                            PlaceCategory.UNKNOWN
                        )
                    ) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    category.displayName,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                }

                // Show activity insights if available
                place.dominantSemanticContext?.let { context ->
                    Text(
                        "Activity detected: ${context.name.lowercase().replace("_", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customName.isNotBlank()) {
                        onSave(customName.trim(), selectedCategory)
                        onDismiss()
                    }
                },
                enabled = customName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
