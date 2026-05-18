package com.cosmiclaboratory.voyager.presentation.screen.mileage

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.MileageEntry
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d")
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** Date-range presets offered for the mileage log. */
private val RANGE_OPTIONS: List<DateRangePeriod> = listOf(
    DateRangePeriod.Today,
    DateRangePeriod.ThisWeek,
    DateRangePeriod.ThisMonth,
    DateRangePeriod.Last30Days
)

/**
 * Mileage log — classify drives as business/personal/etc. and export a tax PDF.
 *
 * Pro feature: when [com.cosmiclaboratory.voyager.presentation.screen.mileage] is
 * gated (Phase 5 billing), the whole screen will be wrapped in a `FeatureGate`. Until
 * billing ships it is reachable but flagged "Pro" in the UI.
 */
@Composable
fun MileageScreen(
    viewModel: MileageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(state.exportUri) {
        val uri = state.exportUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share mileage PDF"))
        viewModel.onAction(MileageAction.ConsumeExportResult)
    }

    MileageContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun MileageContent(
    state: MileageUiState,
    onAction: (MileageAction) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Mileage log",
                trailingAction = {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VoyagerColors.Premium.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VoyagerColors.Premium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            )
        }

        // ── Date-range selector ───────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGE_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = state.range::class == option::class,
                        onClick = { onAction(MileageAction.SelectRange(option)) },
                        label = { Text(option.displayLabel()) }
                    )
                }
            }
        }

        // ── Summary ───────────────────────────────────────────────────
        item { MileageSummaryCard(state.log) }

        // ── Export ────────────────────────────────────────────────────
        item {
            VoyagerButton(
                onClick = { onAction(MileageAction.ExportPdf) },
                enabled = !state.isExporting && state.log.entries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp,
                        color = VoyagerColors.Primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isExporting) "Building PDF…" else "Export tax PDF")
            }
        }

        state.error?.let { error ->
            item {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.Error
                )
            }
        }

        // ── Drive list ────────────────────────────────────────────────
        if (state.log.entries.isEmpty()) {
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (state.isLoading) "Loading drives…"
                        else "No drives recorded in this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        } else {
            items(state.log.entries, key = { it.segmentId }) { entry ->
                MileageEntryRow(
                    entry = entry,
                    onClassify = { purpose ->
                        onAction(MileageAction.Classify(entry.segmentId, purpose))
                    }
                )
            }
        }
    }
}

@Composable
private fun MileageSummaryCard(log: MileageLog) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.HIGHLIGHTED) {
        Text(
            text = "%.1f mi total".format(log.totalMiles),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.OnSurface
        )
        Spacer(Modifier.height(8.dp))
        SummaryLine("Business", log.milesFor(MileagePurpose.BUSINESS))
        SummaryLine("Personal", log.milesFor(MileagePurpose.PERSONAL))
        SummaryLine("Deductible total", log.deductibleMeters / com.cosmiclaboratory.voyager.domain.model.METERS_PER_MILE)
        if (log.unclassifiedCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${log.unclassifiedCount} drive(s) still unclassified",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.Warning
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, miles: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VoyagerColors.OnSurfaceVariant)
        Text(
            text = "%.1f mi".format(miles),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = VoyagerColors.OnSurface
        )
    }
}

@Composable
private fun MileageEntryRow(
    entry: MileageEntry,
    onClassify: (MileagePurpose) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(entry.startAt) { Instant.ofEpochMilli(entry.startAt).atZone(zone) }

    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DATE_FMT.format(start),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "${TIME_FMT.format(start)} · %.1f mi".format(entry.distanceMiles),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            PurposeSelector(selected = entry.purpose, onSelect = onClassify)
        }
    }
}

@Composable
private fun PurposeSelector(
    selected: MileagePurpose,
    onSelect: (MileagePurpose) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = if (selected.deductible) VoyagerColors.Premium else VoyagerColors.OnSurfaceVariant

    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VoyagerColors.SurfaceVariant,
            onClick = { expanded = true }
        ) {
            Text(
                text = selected.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = tint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MileagePurpose.entries.forEach { purpose ->
                DropdownMenuItem(
                    text = { Text(purpose.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(purpose)
                    }
                )
            }
        }
    }
}
