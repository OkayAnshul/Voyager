package com.cosmiclaboratory.voyager.presentation.screen.segment

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.EvidenceBlock
import com.cosmiclaboratory.voyager.domain.model.InferenceExplanation
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.billing.FeatureGate
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp

/**
 * SegmentDetailSheet -- ModalBottomSheet showing full segment details,
 * evidence block, inference explanation, counter-evidence, and action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentDetailSheet(
    onDismiss: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    viewModel: SegmentDetailViewModel = hiltViewModel(),
    entitlementViewModel: EntitlementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPro by entitlementViewModel.isPro.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoyagerColors.Surface,
        tonalElevation = 8.dp
    ) {
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerCard(height = 80.dp)
                    ShimmerCard(height = 60.dp)
                    ShimmerCard(height = 60.dp)
                }
            }

            uiState.segment == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Segment not found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                uiState.segment?.let { segment ->
                    SegmentDetailContent(
                        segment = segment,
                        evidence = uiState.evidence,
                        explanation = uiState.explanation,
                        isPro = isPro,
                        onUnlock = onNavigateToPaywall,
                        onChangeType = { newType ->
                            viewModel.onIntent(SegmentDetailIntent.ChangeType(newType))
                        },
                        onSplit = { timestampMs ->
                            viewModel.onIntent(SegmentDetailIntent.SplitAt(timestampMs))
                        },
                        onMerge = { nextId ->
                            viewModel.onIntent(SegmentDetailIntent.MergeWithNext(nextId))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentDetailContent(
    segment: TimelineSegment,
    evidence: EvidenceBlock?,
    explanation: InferenceExplanation?,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onChangeType: (String) -> Unit,
    onSplit: (Long) -> Unit,
    onMerge: (Long) -> Unit
) {
    var showTypeMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            SegmentHeader(segment)
        }

        // Evidence & inference explanation — a Pro feature, gated behind FeatureGate.
        if (evidence != null || explanation != null) {
            item {
                FeatureGate(
                    isPro = isPro,
                    featureName = "Evidence",
                    description = "See why Voyager inferred this segment — metrics, " +
                        "activity votes, inference explanation and counter-evidence.",
                    onUnlock = onUnlock
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        evidence?.let { EvidenceSection(it) }
                        explanation?.let { exp ->
                            InferenceSection(exp)
                            if (exp.counterEvidence.isNotEmpty()) {
                                CounterEvidenceSection(exp.counterEvidence)
                            }
                            if (exp.sourceSet.isNotEmpty()) {
                                SourceAttributionSection(exp.sourceSet)
                            }
                        }
                    }
                }
            }
        }

        // Action buttons
        item {
            ActionButtonsSection(
                segment = segment,
                showTypeMenu = showTypeMenu,
                onShowTypeMenu = { showTypeMenu = it },
                onChangeType = onChangeType,
                onSplit = onSplit,
                onMerge = onMerge
            )
        }
    }
}

@Composable
private fun SegmentHeader(segment: TimelineSegment) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = segmentTypeIcon(segment.type),
                    contentDescription = null,
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = segment.type.name.replace('_', ' '),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                    segment.place?.let {
                        Text(
                            text = it.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (segment.isUserCorrected) {
                VoyagerBadge(
                    text = "Corrected",
                    color = VoyagerColors.AccentPurple.copy(alpha = 0.15f),
                    contentColor = VoyagerColors.AccentPurple
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        // Time range and duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationMs(segment.durationMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.Primary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Distance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDistanceM(segment.distanceM),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Confidence bar
        Text(
            text = "Confidence: ${(segment.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { segment.confidence },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = when {
                segment.confidence >= 0.8f -> VoyagerColors.Success
                segment.confidence >= 0.5f -> VoyagerColors.Warning
                else -> VoyagerColors.Error
            },
            trackColor = VoyagerColors.SurfaceVariant
        )

        // Gap reason
        segment.gapReason?.let { reason ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Gap reason: $reason",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.Warning
            )
        }

        // Place source indicator
        segment.place?.let { place ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = VoyagerColors.PrimaryDim,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Source: ${place.nameSource}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EvidenceSection(evidence: EvidenceBlock) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Evidence")
        Spacer(modifier = Modifier.height(8.dp))

        // Grid of evidence metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = evidence.sampleCount.toString(), label = "Samples")
            StatItem(
                value = evidence.avgSpeed?.let { "%.1f".format(it) } ?: "--",
                label = "Avg Speed"
            )
            StatItem(
                value = evidence.maxSpeed?.let { "%.1f".format(it) } ?: "--",
                label = "Max Speed"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = evidence.stepCount?.toString() ?: "--",
                label = "Steps"
            )
            StatItem(
                value = evidence.headingConsistency?.let { "${(it * 100).toInt()}%" } ?: "--",
                label = "Heading"
            )
        }

        // Activity votes
        if (evidence.activityVotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Activity Votes",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.PrimaryDim
            )
            Spacer(modifier = Modifier.height(4.dp))
            evidence.activityVotes.forEach { (activity, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                }
            }
        }

        // Provider mix
        if (evidence.providerMix.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Provider Mix",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.PrimaryDim
            )
            Spacer(modifier = Modifier.height(4.dp))
            evidence.providerMix.forEach { (provider, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = provider,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun InferenceSection(explanation: InferenceExplanation) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Inference Explanation")
        Spacer(modifier = Modifier.height(8.dp))

        // Label and confidence
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoyagerBadge(text = explanation.label.uppercase())
            Text(
                text = "${(explanation.confidence * 100).toInt()}% confident",
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.Primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Human-readable explanation
        Text(
            text = explanation.humanExplanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rule version
        Text(
            text = "Rule version: ${explanation.ruleVersion}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CounterEvidenceSection(counterEvidence: List<String>) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Counter-Evidence")
        Spacer(modifier = Modifier.height(8.dp))

        counterEvidence.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VoyagerColors.Warning,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SourceAttributionSection(sourceSet: Set<String>) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Sources")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sourceSet.forEach { source ->
                VoyagerBadge(text = source.uppercase())
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    segment: TimelineSegment,
    showTypeMenu: Boolean,
    onShowTypeMenu: (Boolean) -> Unit,
    onChangeType: (String) -> Unit,
    onSplit: (Long) -> Unit,
    onMerge: (Long) -> Unit
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Actions")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Change Type
            Box {
                VoyagerButton(
                    onClick = { onShowTypeMenu(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change Type")
                }

                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { onShowTypeMenu(false) }
                ) {
                    SegmentType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name.replace('_', ' ')) },
                            onClick = {
                                onChangeType(type.name)
                                onShowTypeMenu(false)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Split
            VoyagerOutlinedButton(
                onClick = {
                    // Split at midpoint
                    val midpoint = segment.startAt + (segment.durationMs / 2)
                    onSplit(midpoint)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Split")
            }

            // Merge
            VoyagerOutlinedButton(
                onClick = { onMerge(segment.segmentId + 1) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.MergeType,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Merge")
            }
        }
    }
}

// -- Utility functions --

private fun segmentTypeIcon(type: SegmentType) = when (type) {
    SegmentType.VISIT, SegmentType.DWELL -> Icons.Default.Place
    SegmentType.WALK -> Icons.Default.DirectionsWalk
    SegmentType.RUN -> Icons.Default.DirectionsRun
    SegmentType.CYCLE -> Icons.Default.DirectionsBike
    SegmentType.DRIVE -> Icons.Default.DirectionsCar
    SegmentType.TRANSIT -> Icons.Default.DirectionsTransit
    SegmentType.FLIGHT -> Icons.Default.Flight
    SegmentType.GAP -> Icons.Default.SignalWifiOff
    SegmentType.UNKNOWN_MOTION -> Icons.Default.HelpOutline
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

private fun formatDistanceM(meters: Double): String = when {
    meters < 1.0 -> "< 1 m"
    meters >= 1000 -> "%.1f km".format(meters / 1000)
    else -> "${meters.toInt()} m"
}
