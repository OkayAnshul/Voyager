package com.cosmiclaboratory.voyager.presentation.screen.visit

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.ConfidenceBlock
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.billing.FeatureGate
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEvidenceEntity
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailSheet(
    viewModel: VisitDetailViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onNavigateToPlace: (Long) -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    entitlementViewModel: EntitlementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPro by entitlementViewModel.isPro.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = VoyagerColors.Surface,
        contentColor = VoyagerColors.OnSurface
    ) {
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerCard(height = 60.dp)
                    ShimmerCard(height = 80.dp)
                    ShimmerCard(height = 60.dp)
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.Error
                    )
                }
            }

            uiState.visit != null && uiState.place != null -> {
                val visit = uiState.visit
                val place = uiState.place
                if (visit != null && place != null) {
                    VisitDetailContent(
                        visit = visit,
                        place = place,
                        evidence = uiState.evidence,
                        confidenceBlock = uiState.confidenceBlock,
                        isPro = isPro,
                        onUnlock = onNavigateToPaywall,
                        onIntent = viewModel::onIntent,
                        onNavigateToPlace = onNavigateToPlace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun VisitDetailContent(
    visit: VisitEntity,
    place: PlaceEntity,
    evidence: VisitEvidenceEntity?,
    confidenceBlock: ConfidenceBlock?,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onIntent: (VisitDetailIntent) -> Unit,
    onNavigateToPlace: (Long) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    "Rename Place",
                    style = MaterialTheme.typography.titleMedium,
                    color = VoyagerColors.Primary
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Place name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onIntent(VisitDetailIntent.RenamePlace(renameText.trim()))
                        showRenameDialog = false
                    }
                }) {
                    Text("Rename", color = VoyagerColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = VoyagerColors.OnSurfaceVariant)
                }
            },
            containerColor = VoyagerColors.SurfaceOverlay
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaceHeader(place, onNavigateToPlace)
        TimeSection(visit)
        ConfidenceSection(visit, confidenceBlock)
        if (evidence != null) {
            FeatureGate(
                isPro = isPro,
                featureName = "Visit evidence",
                description = "See the inside/outside sample counts and confirmation " +
                    "rule behind this visit.",
                onUnlock = onUnlock
            ) {
                EvidenceSection(evidence)
            }
        }
        ActionRow(
            visit = visit,
            place = place,
            onConfirm = { onIntent(VisitDetailIntent.ConfirmVisit) },
            onRename = {
                renameText = place.userDisplayName ?: place.bestProviderName ?: ""
                showRenameDialog = true
            },
            onDelete = { onIntent(VisitDetailIntent.DeleteVisit) }
        )
    }
}

@Composable
private fun PlaceHeader(place: PlaceEntity, onNavigateToPlace: (Long) -> Unit) {
    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onNavigateToPlace(place.placeId) }
    ) {
        val displayName = place.userDisplayName ?: place.bestProviderName ?: "Unknown Place"
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.Primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val nameSource = when {
                place.userDisplayName != null -> "USER"
                place.bestProviderSource != null -> place.bestProviderSource ?: "COORDINATES"
                else -> "COORDINATES"
            }
            NameSourceIndicator(nameSource = nameSource)

            val category = try {
                PlaceCategory.valueOf(place.category)
            } catch (_: Exception) {
                PlaceCategory.UNKNOWN
            }
            CategoryChip(categoryName = category.displayName)
        }
    }
}

@Composable
private fun TimeSection(visit: VisitEntity) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Time")
        Spacer(modifier = Modifier.height(8.dp))

        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Arrival",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = timeFormat.format(Date(visit.arrivalAt)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.Primary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Departure",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = visit.departureAt?.let { timeFormat.format(Date(it)) } ?: "Ongoing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (visit.departureAt != null) VoyagerColors.Primary
                    else VoyagerColors.AccentGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = VoyagerColors.SurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        val dwellMs = visit.dwellMs ?: visit.departureAt?.let { it - visit.arrivalAt }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Duration: ${dwellMs?.let { formatDurationMs(it) } ?: "In progress"}",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurface
            )
        }
    }
}

@Composable
private fun ConfidenceSection(visit: VisitEntity, confidenceBlock: ConfidenceBlock?) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Confidence")
        Spacer(modifier = Modifier.height(8.dp))

        val overall = confidenceBlock?.overall ?: visit.confidence
        ConfidenceBar(confidence = overall)

        if (confidenceBlock != null) {
            Spacer(modifier = Modifier.height(8.dp))
            confidenceBlock.arrival?.let { ConfidenceRow("Arrival", it) }
            confidenceBlock.departure?.let { ConfidenceRow("Departure", it) }
            confidenceBlock.category?.let { ConfidenceRow("Category", it) }
            confidenceBlock.label?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfidenceRow(label: String, value: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .padding(horizontal = 8.dp),
            color = when {
                value >= 0.8f -> VoyagerColors.ConfidenceHigh
                value >= 0.5f -> VoyagerColors.ConfidenceMedium
                else -> VoyagerColors.ConfidenceLow
            },
            trackColor = VoyagerColors.SurfaceVariant
        )
        Text(
            text = "${(value * 100).toInt()}%",
            style = MonoStatSmall,
            color = VoyagerColors.Primary
        )
    }
}

@Composable
private fun EvidenceSection(evidence: VisitEvidenceEntity) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Visit Evidence")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = evidence.insideCount.toString(),
                label = "Inside"
            )
            StatItem(
                value = evidence.outsideCount.toString(),
                label = "Outside"
            )
            StatItem(
                value = "${(evidence.arrivalConfidence * 100).toInt()}%",
                label = "Arrival"
            )
            StatItem(
                value = "${(evidence.departureConfidence * 100).toInt()}%",
                label = "Departure"
            )
        }

        evidence.confirmationRuleUsed?.let { rule ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rule: $rule",
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionRow(
    visit: VisitEntity,
    place: PlaceEntity,
    onConfirm: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    "Delete Visit",
                    style = MaterialTheme.typography.titleMedium,
                    color = VoyagerColors.Error
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this visit? This action cannot be undone.",
                    color = VoyagerColors.OnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) {
                    Text("Delete", color = VoyagerColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = VoyagerColors.OnSurfaceVariant)
                }
            },
            containerColor = VoyagerColors.SurfaceOverlay
        )
    }

    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Actions")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoyagerButton(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = !visit.isUserCorrected
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm")
            }

            VoyagerButton(
                onClick = onRename,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoyagerOutlinedButton(
                onClick = { /* Time adjustment UI is a future enhancement */ },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Times")
            }

            VoyagerOutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = VoyagerColors.Error)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", color = VoyagerColors.Error)
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
