package com.cosmiclaboratory.voyager.presentation.screen.mileage

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.MileageEntry
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.model.MileageRateConfig
import com.cosmiclaboratory.voyager.domain.model.formatDistance
import com.cosmiclaboratory.voyager.domain.model.formatMoney
import com.cosmiclaboratory.voyager.domain.usecase.MileageDeduction
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.billing.FeatureGate
import com.cosmiclaboratory.voyager.presentation.components.EvidenceSheet
import com.cosmiclaboratory.voyager.presentation.components.VoyagerEvidence
import com.cosmiclaboratory.voyager.presentation.components.WhyChip
import com.cosmiclaboratory.voyager.presentation.theme.BadgeStyle
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerBadge
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSpacing
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSurfaces
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge
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
 * Mileage log — classify drives (swipe or tap), see the deductible value in your own currency,
 * and export a tax PDF or CSV.
 *
 * A Pro feature: the screen is wrapped in a [FeatureGate], so free users see a locked card with
 * an "Unlock Pro" path to the paywall instead of the log.
 */
@Composable
fun MileageScreen(
    onNavigateToPaywall: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: MileageViewModel = hiltViewModel(),
    entitlementViewModel: EntitlementViewModel = hiltViewModel()
) {
    val isPro by entitlementViewModel.isPro.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(state.exportUri) {
        val uri = state.exportUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = state.exportMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share mileage report"))
        viewModel.onAction(MileageAction.ConsumeExportResult)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        FeatureGate(
            isPro = isPro,
            featureName = "Mileage log",
            description = "Classify your drives as business or personal and " +
                "export an IRS/HMRC-ready tax PDF or CSV.",
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            onUnlock = onNavigateToPaywall
        ) {
            MileageContent(
                state = state,
                onAction = viewModel::onAction,
                onNavigateToSettings = onNavigateToSettings
            )
        }
    }
}

@Composable
fun MileageContent(
    state: MileageUiState,
    onAction: (MileageAction) -> Unit,
    onNavigateToSettings: () -> Unit
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Mileage rates & units",
                                tint = VoyagerColors.OnSurfaceVariant
                            )
                        }
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

        // ── Batch-classify action bar (only in selection mode) ────────
        if (state.inSelectionMode) {
            item {
                SelectionActionBar(
                    count = state.selectedIds.size,
                    onClassify = { purpose -> onAction(MileageAction.ClassifyBatch(purpose)) },
                    onClear = { onAction(MileageAction.ClearSelection) }
                )
            }
        }

        // ── Summary ───────────────────────────────────────────────────
        item { MileageSummaryCard(state.log, state.estimate) }

        // ── Export ────────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                VoyagerButton(
                    onClick = { onAction(MileageAction.ExportPdf) },
                    enabled = !state.isExporting && state.log.entries.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp,
                            color = VoyagerColors.Primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Export PDF")
                }
                VoyagerButton(
                    onClick = { onAction(MileageAction.ExportCsv) },
                    enabled = !state.isExporting && state.log.entries.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export CSV")
                }
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
                VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
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
                MileageDriveRow(
                    entry = entry,
                    config = state.config,
                    selected = entry.segmentId in state.selectedIds,
                    inSelectionMode = state.inSelectionMode,
                    onClassify = { purpose ->
                        onAction(MileageAction.Classify(entry.segmentId, purpose))
                    },
                    onToggleSelect = { onAction(MileageAction.ToggleSelection(entry.segmentId)) }
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onClassify: (MileagePurpose) -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS, tint = VoyagerSurfaces.premiumWash) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.OnSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    VoyagerButton(onClick = { expanded = true }) { Text("Classify…") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MileagePurpose.entries.forEach { purpose ->
                            DropdownMenuItem(
                                text = { Text(purpose.displayName) },
                                onClick = {
                                    expanded = false
                                    onClassify(purpose)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = VoyagerColors.OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MileageSummaryCard(log: MileageLog, estimate: MileageDeduction.Estimate) {
    val unit = estimate.distanceUnit
    val currency = estimate.currencyCode
    val deductibleDistance = log.deductibleDistance(unit)
    val businessAmount = estimate.lines.firstOrNull { it.purpose == MileagePurpose.BUSINESS }?.amount ?: 0.0
    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.GLASS,
        tint = VoyagerSurfaces.premiumWash
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Estimated deduction",
                    style = MaterialTheme.typography.labelMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = formatMoney(estimate.totalAmount, currency),
                    style = MonoStatLarge,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "${formatDistance(deductibleDistance, unit)} deductible",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            if (log.unclassifiedCount == 0 && log.entries.isNotEmpty()) {
                VoyagerBadge(
                    text = "Audit-ready",
                    color = VoyagerColors.Premium,
                    contentColor = Color.Black,
                    style = BadgeStyle.FILLED,
                    icon = Icons.Filled.Verified
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SummaryLine("Business", formatDistance(log.distanceFor(MileagePurpose.BUSINESS, unit), unit), formatMoney(businessAmount, currency))
        SummaryLine("Personal", formatDistance(log.distanceFor(MileagePurpose.PERSONAL, unit), unit), null)
        SummaryLine("Total driven", formatDistance(log.totalDistance(unit), unit), null)
        if (log.unclassifiedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = VoyagerColors.Warning,
                    modifier = Modifier.height(14.dp).width(14.dp)
                )
                Text(
                    text = "${log.unclassifiedCount} drive(s) still need classifying for an audit-ready export",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.Warning
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, distanceText: String, amountText: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VoyagerColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (amountText != null) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.Premium
                )
            }
            Text(
                text = distanceText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.OnSurface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MileageDriveRow(
    entry: MileageEntry,
    config: MileageRateConfig,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClassify: (MileagePurpose) -> Unit,
    onToggleSelect: () -> Unit
) {
    // Swipe = quick classify: right (start→end) = Business, left (end→start) = Personal.
    // We never actually dismiss the row — confirmValueChange fires the classify and returns
    // false so the row animates back into place. Disabled while multi-selecting.
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onClassify(MileagePurpose.BUSINESS)
                SwipeToDismissBoxValue.EndToStart -> onClassify(MileagePurpose.PERSONAL)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = !inSelectionMode,
        enableDismissFromEndToStart = !inSelectionMode,
        backgroundContent = { SwipeBackground(swipeState.dismissDirection) }
    ) {
        MileageEntryRow(
            entry = entry,
            config = config,
            selected = selected,
            inSelectionMode = inSelectionMode,
            onClassify = onClassify,
            onToggleSelect = onToggleSelect
        )
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, icon, alignment, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            Quad(VoyagerColors.AccentGreen, Icons.Filled.Business, Alignment.CenterStart, "Business")
        SwipeToDismissBoxValue.EndToStart ->
            Quad(VoyagerColors.OnSurfaceVariant, Icons.Filled.Person, Alignment.CenterEnd, "Personal")
        SwipeToDismissBoxValue.Settled ->
            Quad(Color.Transparent, null, Alignment.Center, "")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color)
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MileageEntryRow(
    entry: MileageEntry,
    config: MileageRateConfig,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClassify: (MileagePurpose) -> Unit,
    onToggleSelect: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(entry.startAt) { Instant.ofEpochMilli(entry.startAt).atZone(zone) }
    var showEvidence by remember { mutableStateOf(false) }

    val unit = config.distanceUnit
    val distance = entry.distanceIn(unit)
    val rate = config.rateFor(entry.purpose)
    val amount = rate?.let { it * distance }

    if (showEvidence) {
        EvidenceSheet(evidence = entry.toEvidence(config), onDismiss = { showEvidence = false })
    }

    VoyagerCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (inSelectionMode) onToggleSelect() },
                onLongClick = onToggleSelect
            ),
        variant = CardVariant.GLASS,
        tint = if (selected) VoyagerSurfaces.premiumWash else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (inSelectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) VoyagerColors.Premium else VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier.height(20.dp).width(20.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DATE_FMT.format(start),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "${TIME_FMT.format(start)} · ${formatDistance(distance, unit)}" +
                        (amount?.let { " · ${formatMoney(it, config.currencyCode)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (amount != null) VoyagerColors.Premium else VoyagerColors.OnSurfaceVariant
                )
            }
            if (!inSelectionMode) {
                PurposeSelector(selected = entry.purpose, onSelect = onClassify)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)) {
            Box(modifier = Modifier.clickable { showEvidence = true }) {
                VoyagerBadge(
                    text = "GPS evidence",
                    color = VoyagerColors.AccentGreen,
                    contentColor = VoyagerColors.AccentGreen,
                    style = BadgeStyle.OUTLINE,
                    icon = Icons.Filled.GpsFixed
                )
            }
            WhyChip(onClick = { showEvidence = true })
        }
    }
}

/** Honest, GPS-backed evidence for a single drive — opens from the row badge. */
private fun MileageEntry.toEvidence(config: MileageRateConfig): VoyagerEvidence {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startAt).atZone(zone)
    val durationMin = ((endAt - startAt) / 60000L).coerceAtLeast(0L)
    val unit = config.distanceUnit
    val distanceText = formatDistance(distanceIn(unit), unit)
    return VoyagerEvidence(
        title = "Drive · ${DATE_FMT.format(start)}",
        humanExplanation = "Voyager detected this drive automatically from your GPS track — " +
            "$distanceText over about $durationMin min. Your classification below is exactly what the tax export reports.",
        confidence = null,
        supportingMetrics = linkedMapOf(
            "Date" to DATE_FMT.format(start),
            "Start" to TIME_FMT.format(start),
            "Distance" to distanceText,
            "Duration" to "$durationMin min",
            "Classification" to purpose.displayName
        ),
        counterEvidence = emptyList(),
        sources = setOf("On-device GPS"),
        ruleVersion = null
    )
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
