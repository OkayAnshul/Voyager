package com.cosmiclaboratory.voyager.presentation.screen.review

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
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
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge

@Composable
fun PlaceReviewScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PlaceReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val highConfidencePending = uiState.pendingPlaces.filter { it.confidence >= 0.6f }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        // Hero — review summary (big mono count + high-confidence subline)
        if (uiState.pendingPlaces.isNotEmpty()) {
            VoyagerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                variant = CardVariant.GLASS
            ) {
                VoyagerEyebrow("Review Queue")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${uiState.pendingPlaces.size}",
                        style = MonoStatLarge,
                        color = VoyagerColors.OnSurface
                    )
                    Text(
                        text = "pending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (highConfidencePending.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${highConfidencePending.size} high-confidence · ready to confirm",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.RateReview,
                    contentDescription = null,
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Review Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
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
                AllCaughtUp(onDone = onNavigateBack)
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (highConfidencePending.isNotEmpty()) {
                        item {
                            VoyagerOutlinedButton(
                                onClick = { highConfidencePending.forEach { viewModel.confirmPlace(it.placeId) } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Confirm all high-confidence (${highConfidencePending.size})")
                            }
                        }
                    }
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
    val reasonColor = if (place.confidence < 0.4f) VoyagerColors.Error else VoyagerColors.Warning

    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    maxLines = 1
                )
                if (place.category != PlaceCategory.UNKNOWN) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CategoryChip(categoryName = place.category.displayName)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Confidence bar (text + bar + %)
        ConfidenceBar(
            confidence = place.confidence,
            source = "Detection confidence"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Plain-language flag reason — why this is in the queue.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = reasonColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Flagged: ${flagReason(place)}",
                style = MaterialTheme.typography.labelMedium,
                color = reasonColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons — Rename + Confirm (both mirror real intents)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoyagerOutlinedButton(
                onClick = { showRenameDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename", style = MaterialTheme.typography.labelLarge)
            }
            VoyagerButton(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm", style = MaterialTheme.typography.labelLarge)
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

/** Satisfying clear-down state — the queue is empty. */
@Composable
private fun AllCaughtUp(onDone: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(VoyagerColors.Success.copy(alpha = 0.10f), VoyagerShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(VoyagerColors.Success.copy(alpha = 0.18f), VoyagerShapes.pill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VoyagerColors.Success,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = "All Caught Up",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Your spatial history is confirmed and tidy. No pending reviews right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            VoyagerPrimaryButton(onClick = onDone) {
                Text("Return to Timeline")
            }
        }
    }
}

/** Honest, plain-language reason this place landed in the review queue. */
private fun flagReason(place: TimelinePlace): String = when {
    place.confidence < 0.4f -> "Weak GPS signal"
    place.category == PlaceCategory.UNKNOWN -> "New place — needs a category"
    place.nameSource.contains("Coordinate", ignoreCase = true) -> "Unnamed — only coordinates"
    else -> "Low confidence match"
}
