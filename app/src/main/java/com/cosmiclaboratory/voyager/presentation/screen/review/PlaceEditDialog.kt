package com.cosmiclaboratory.voyager.presentation.screen.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.PlaceReview
import com.cosmiclaboratory.voyager.domain.model.PlaceNameSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditDialog(
    review: PlaceReview,
    suggestions: List<PlaceNameSuggestion> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (newName: String?, newCategory: PlaceCategory?, customCategoryName: String?) -> Unit
) {
    var editedName by remember { mutableStateOf(review.detectedName) }
    var editedCategory by remember { mutableStateOf<PlaceCategory?>(null) }
    var customCategoryName by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAllSuggestions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Place") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Place Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // ISSUE #2: Name suggestions section
                if (suggestions.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Name Suggestions (${suggestions.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showAllSuggestions = !showAllSuggestions },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (showAllSuggestions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        if (showAllSuggestions) "Collapse" else "Expand"
                                    )
                                }
                            }

                            if (showAllSuggestions) {
                                Spacer(Modifier.height(8.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestions.forEach { suggestion ->
                                        SuggestionChip(
                                            suggestion = suggestion,
                                            isSelected = editedName == suggestion.name,
                                            onClick = { editedName = suggestion.name }
                                        )
                                    }
                                }
                            } else {
                                // Show top 2 suggestions when collapsed
                                Spacer(Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(suggestions.take(2)) { suggestion ->
                                        SuggestionChip(
                                            suggestion = suggestion,
                                            isSelected = editedName == suggestion.name,
                                            onClick = { editedName = suggestion.name }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Legacy OSM suggestion (if available and not in suggestions list)
                if (review.osmSuggestedName != null &&
                    review.osmSuggestedName != editedName &&
                    suggestions.none { it.name == review.osmSuggestedName }) {
                    AssistChip(
                        onClick = { editedName = review.osmSuggestedName!! },
                        label = { Text("Use OSM: ${review.osmSuggestedName}") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                // ISSUE #3: Mandatory category selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Category *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { showCategoryPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editedCategory == null)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            editedCategory?.displayName ?: "Select Category (Required)",
                            color = if (editedCategory == null)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (editedCategory == null) {
                        Text(
                            text = "Please select a category before saving",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // OSM category suggestion
                if (review.osmSuggestedCategory != null && editedCategory == null) {
                    AssistChip(
                        onClick = { editedCategory = review.osmSuggestedCategory },
                        label = { Text("Use OSM: ${review.osmSuggestedCategory!!.displayName}") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                // ISSUE #3: Custom category name input (only if CUSTOM selected)
                if (editedCategory == PlaceCategory.CUSTOM) {
                    OutlinedTextField(
                        value = customCategoryName,
                        onValueChange = { customCategoryName = it },
                        label = { Text("Custom Category Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = customCategoryName.isBlank(),
                        supportingText = if (customCategoryName.isBlank()) {
                            { Text("Required for custom category", color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = if (editedName != review.detectedName) editedName else null
                    val finalCustomName = if (editedCategory == PlaceCategory.CUSTOM && customCategoryName.isNotBlank()) {
                        customCategoryName
                    } else null
                    onConfirm(finalName, editedCategory, finalCustomName)
                },
                // ISSUE #3: Disable save button until category selected
                enabled = editedCategory != null &&
                         (editedCategory != PlaceCategory.CUSTOM || customCategoryName.isNotBlank())
            ) {
                Text("Save & Approve")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Category picker dialog
    if (showCategoryPicker) {
        CategoryPickerDialog(
            currentCategory = editedCategory ?: review.detectedCategory,
            onCategorySelected = { category ->
                editedCategory = category
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerDialog(
    currentCategory: PlaceCategory,
    onCategorySelected: (PlaceCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(400.dp)
            ) {
                items(PlaceCategory.values()) { category ->
                    CategoryCard(
                        category = category,
                        isSelected = category == currentCategory,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoryCard(
    category: PlaceCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder()
        } else null
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun getCategoryIcon(category: PlaceCategory) = when (category) {
    PlaceCategory.HOME -> Icons.Default.Home
    PlaceCategory.WORK -> Icons.Default.Build
    PlaceCategory.GYM -> Icons.Default.Star
    PlaceCategory.RESTAURANT -> Icons.Default.Star
    PlaceCategory.SHOPPING -> Icons.Default.ShoppingCart
    PlaceCategory.ENTERTAINMENT -> Icons.Default.Star
    PlaceCategory.HEALTHCARE -> Icons.Default.Favorite
    PlaceCategory.EDUCATION -> Icons.Default.Info
    PlaceCategory.TRANSPORT -> Icons.Default.Place
    PlaceCategory.TRAVEL -> Icons.Default.Place
    PlaceCategory.OUTDOOR -> Icons.Default.Star
    PlaceCategory.SOCIAL -> Icons.Default.Person
    PlaceCategory.SERVICES -> Icons.Default.Build
    PlaceCategory.UNKNOWN -> Icons.Default.Info
    PlaceCategory.CUSTOM -> Icons.Default.Star
}

/**
 * ISSUE #2: Chip for displaying a place name suggestion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionChip(
    suggestion: PlaceNameSuggestion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Column {
                Text(
                    text = suggestion.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = suggestion.source.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingIcon = {
            Icon(
                Icons.Default.CheckCircle,
                null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(android.graphics.Color.parseColor(suggestion.source.getBadgeColor()))
                }
            )
        },
        trailingIcon = {
            Badge(
                containerColor = Color(android.graphics.Color.parseColor(suggestion.source.getBadgeColor()))
            ) {
                Text(
                    text = "${(suggestion.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
