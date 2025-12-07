package com.cosmiclaboratory.voyager.presentation.screen.review

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
import com.cosmiclaboratory.voyager.domain.model.PlaceReview
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlaceReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingReviews by viewModel.pendingReviews.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Place Reviews ($pendingCount)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Batch approve button
                    IconButton(
                        onClick = { viewModel.batchApproveHighConfidence() },
                        enabled = !uiState.isProcessing && pendingCount > 0
                    ) {
                        Icon(Icons.Default.CheckCircle, "Batch Approve High Confidence")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = remember { SnackbarHostState() }.apply {
                    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
                        uiState.successMessage?.let { showSnackbar(it) }
                        uiState.errorMessage?.let { showSnackbar(it) }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (pendingReviews.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "All caught up!",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "No places need review",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group by priority
                pendingReviews.forEach { (priority, reviews) ->
                    // Priority header
                    item(key = "header_$priority") {
                        PriorityHeader(priority = priority, count = reviews.size)
                    }

                    // Review cards
                    items(
                        items = reviews,
                        key = { it.id }
                    ) { review ->
                        PlaceReviewCard(
                            review = review,
                            onApprove = { viewModel.approvePlace(review.id) },
                            onEdit = { viewModel.showEditDialog(review.id) },
                            onReject = { viewModel.rejectPlace(review.id) },
                            isProcessing = uiState.isProcessing
                        )
                    }
                }
            }
        }

        // Edit dialog
        uiState.editDialogReviewId?.let { reviewId ->
            val review = pendingReviews.values.flatten().find { it.id == reviewId }
            if (review != null) {
                PlaceEditDialog(
                    review = review,
                    suggestions = suggestions[reviewId] ?: emptyList(),
                    onDismiss = { viewModel.hideEditDialog() },
                    onConfirm = { newName, newCategory, customCategoryName ->
                        viewModel.editAndApprovePlace(reviewId, newName, newCategory, customCategoryName)
                    }
                )
            }
        }
    }
}

@Composable
private fun PriorityHeader(
    priority: ReviewPriority,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (priority) {
                    ReviewPriority.HIGH -> Icons.Default.Warning
                    ReviewPriority.NORMAL -> Icons.Default.CheckCircle
                    ReviewPriority.LOW -> Icons.Default.Info
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = when (priority) {
                    ReviewPriority.HIGH -> MaterialTheme.colorScheme.error
                    ReviewPriority.NORMAL -> MaterialTheme.colorScheme.primary
                    ReviewPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "${priority.name} Priority",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "$count review${if (count != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun PlaceReviewCard(
    review: PlaceReview,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit,
    isProcessing: Boolean
) {
    var showConfidenceBreakdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = review.detectedName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                ReviewTypeBadge(reviewType = review.reviewType)
            }

            // Category and confidence
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(review.detectedCategory.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                ConfidenceIndicator(confidence = review.confidence)
            }

            // Phase 1 UX: Confidence breakdown (expandable)
            if (review.confidenceBreakdown != null && review.confidenceBreakdown.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showConfidenceBreakdown = !showConfidenceBreakdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (showConfidenceBreakdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showConfidenceBreakdown) "Hide Details" else "Why was this detected?")
                }

                if (showConfidenceBreakdown) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Detection Factors",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            review.confidenceBreakdown.forEach { (factor, value) ->
                                ConfidenceBreakdownItem(factor = factor, value = value)
                            }
                        }
                    }
                }
            }

            // Location info
            Text(
                text = "📍 ${String.format("%.6f, %.6f", review.latitude, review.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Visit stats
            Text(
                text = "${review.visitCount} visit${if (review.visitCount != 1) "s" else ""} • ${review.locationCount} locations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // OSM suggestions (if available)
            if (review.osmSuggestedName != null || review.osmSuggestedCategory != null) {
                HorizontalDivider()
                Text(
                    text = "OSM Suggestions:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                review.osmSuggestedName?.let { name ->
                    Text(
                        text = "Name: $name",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                review.osmSuggestedCategory?.let { category ->
                    Text(
                        text = "Category: ${category.displayName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Detection time
            Text(
                text = "Detected ${review.detectionTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                Button(
                    onClick = onApprove,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
private fun ReviewTypeBadge(reviewType: com.cosmiclaboratory.voyager.domain.model.ReviewType) {
    val (color, text) = when (reviewType) {
        com.cosmiclaboratory.voyager.domain.model.ReviewType.NEW_PLACE -> MaterialTheme.colorScheme.primary to "New"
        com.cosmiclaboratory.voyager.domain.model.ReviewType.LOW_CONFIDENCE -> MaterialTheme.colorScheme.tertiary to "Low Confidence"
        com.cosmiclaboratory.voyager.domain.model.ReviewType.CATEGORY_UNCERTAIN -> MaterialTheme.colorScheme.error to "Uncertain"
        com.cosmiclaboratory.voyager.domain.model.ReviewType.MULTIPLE_VISITS -> MaterialTheme.colorScheme.secondary to "Multiple Visits"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "Other"
    }

    AssistChip(
        onClick = { },
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.1f),
            labelColor = color
        )
    )
}

@Composable
private fun ConfidenceIndicator(confidence: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                confidence >= 0.75f -> MaterialTheme.colorScheme.primary
                confidence >= 0.5f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
        )
        LinearProgressIndicator(
            progress = { confidence },
            modifier = Modifier.width(60.dp),
            color = when {
                confidence >= 0.75f -> MaterialTheme.colorScheme.primary
                confidence >= 0.5f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
        )
    }
}

/**
 * Phase 1 UX: Display individual confidence factor with progress bar
 */
@Composable
private fun ConfidenceBreakdownItem(factor: String, value: Float) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = factor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    value >= 0.7f -> MaterialTheme.colorScheme.primary
                    value >= 0.4f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                value >= 0.7f -> MaterialTheme.colorScheme.primary
                value >= 0.4f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
