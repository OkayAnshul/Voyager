package com.cosmiclaboratory.voyager.presentation.screen.review

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*

@Composable
fun PlaceReviewScreen(
    viewModel: PlaceReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background)
    ) {
        // Header
        VoyagerCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.RateReview,
                    contentDescription = null,
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Place Review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VoyagerColors.OnSurface
                    )
                    Text(
                        text = "${uiState.pendingPlaces.size} places need review",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShimmerCard(height = 80.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        ShimmerCard(height = 80.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        ShimmerCard(height = 80.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            uiState.pendingPlaces.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VoyagerColors.Success,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "All Caught Up",
                            style = MaterialTheme.typography.titleMedium,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "No places need review right now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VoyagerColors.OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.pendingPlaces, key = { it.placeId }) { place ->
                        PlaceReviewCard(
                            place = place,
                            onConfirm = { viewModel.confirmPlace(place.placeId) },
                            onRename = { newName -> viewModel.renamePlace(place.placeId, newName) },
                            onSetCategory = { category -> viewModel.setCategory(place.placeId, category) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Snackbar for messages
    uiState.message?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }
}

@Composable
private fun PlaceReviewCard(
    place: TimelinePlace,
    onConfirm: () -> Unit,
    onRename: (String) -> Unit,
    onSetCategory: (PlaceCategory) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryChip(categoryName = place.category.name)
                    Text(
                        text = place.nameSource,
                        style = MaterialTheme.typography.labelSmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Confidence bar
        ConfidenceBar(
            confidence = place.confidence,
            source = "Detection confidence"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoyagerButton(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm", style = MaterialTheme.typography.labelSmall)
            }
            VoyagerOutlinedButton(
                onClick = { showRenameDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(place.displayName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Place") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Place name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(newName)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
            containerColor = VoyagerColors.Surface
        )
    }
}
