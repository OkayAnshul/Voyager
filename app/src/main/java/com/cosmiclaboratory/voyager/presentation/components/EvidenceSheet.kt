package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerShapes
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSpacing

/**
 * Evidence payload — Voyager's trust moat. Mirrors the fields of [EvidenceCard]
 * so it can be populated straight from an inference explanation. Surfaced via
 * [EvidenceSheet] from any "Why?" affordance (Timeline rows, Mileage drives,
 * Insights stats, place names).
 */
data class VoyagerEvidence(
    val title: String,
    val humanExplanation: String,
    val confidence: Float? = null,
    val supportingMetrics: Map<String, String> = emptyMap(),
    val counterEvidence: List<String> = emptyList(),
    val sources: Set<String> = emptySet(),
    val ruleVersion: String? = null
)

/**
 * A small persistent "Why?" chip. Always visible next to a claim so explainability
 * is never hidden behind a long-press. Opens an [EvidenceSheet].
 */
@Composable
fun WhyChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Why?",
    icon: ImageVector = Icons.Filled.AutoAwesome
) {
    Row(
        modifier = modifier
            .clip(VoyagerShapes.pill)
            .background(VoyagerColors.Primary.copy(alpha = 0.12f), VoyagerShapes.pill)
            .border(1.dp, VoyagerColors.Primary.copy(alpha = 0.32f), VoyagerShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = VoyagerSpacing.sm, vertical = VoyagerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
    ) {
        Icon(icon, contentDescription = null, tint = VoyagerColors.Primary, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.Primary, fontWeight = FontWeight.Medium)
    }
}

/**
 * Bottom-sheet that explains a single claim end-to-end: confidence, plain-language
 * reasoning, the metrics Voyager saw, what argues against the conclusion, and the
 * rule version — closing with the on-device privacy reassurance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceSheet(
    evidence: VoyagerEvidence,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VoyagerColors.SurfaceOverlay,
        shape = VoyagerShapes.sheet,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VoyagerSpacing.screen)
                .padding(bottom = VoyagerSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VoyagerColors.Primary, modifier = Modifier.size(22.dp))
                Text(evidence.title, style = MaterialTheme.typography.headlineSmall, color = VoyagerColors.OnSurface)
            }

            evidence.confidence?.let { ConfidenceBar(confidence = it, source = evidence.sources.firstOrNull()) }

            // Human explanation
            EvidenceSectionLabel("Why Voyager thinks so")
            Text(evidence.humanExplanation, style = MaterialTheme.typography.bodyMedium, color = VoyagerColors.OnSurface)

            // Supporting metrics
            if (evidence.supportingMetrics.isNotEmpty()) {
                EvidenceSectionLabel("What Voyager measured")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VoyagerShapes.card)
                        .background(VoyagerColors.SurfaceVariant.copy(alpha = 0.6f))
                        .padding(VoyagerSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
                ) {
                    evidence.supportingMetrics.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = VoyagerColors.OnSurfaceVariant)
                            Text(v, style = com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp, color = VoyagerColors.OnSurface)
                        }
                    }
                }
            }

            // Counter-evidence — honest seams
            if (evidence.counterEvidence.isNotEmpty()) {
                EvidenceSectionLabel("What argues against", color = VoyagerColors.Warning)
                evidence.counterEvidence.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)) {
                        Text("•", color = VoyagerColors.Warning)
                        Text(line, style = MaterialTheme.typography.bodySmall, color = VoyagerColors.OnSurfaceVariant)
                    }
                }
            }

            evidence.ruleVersion?.let {
                Text("Rule version $it", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
            }

            // Privacy reassurance
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = VoyagerColors.AccentGreen, modifier = Modifier.size(13.dp))
                Text(
                    "Computed on your device — never uploaded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.AccentGreen
                )
            }
        }
    }
}

@Composable
private fun EvidenceSectionLabel(text: String, color: androidx.compose.ui.graphics.Color = VoyagerColors.Primary) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
}
