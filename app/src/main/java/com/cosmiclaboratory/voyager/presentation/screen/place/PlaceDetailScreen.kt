package com.cosmiclaboratory.voyager.presentation.screen.place

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.screen.review.PlaceEditDialog
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp

/**
 * PlaceDetailScreen — full screen for viewing/editing place details,
 * visit history, evidence, and geocode candidates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.place?.displayName ?: "Place Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VoyagerColors.Primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoyagerColors.Background
                )
            )
        },
        containerColor = VoyagerColors.Background
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerCard(height = 120.dp)
                    ShimmerCard(height = 80.dp)
                    ShimmerCard(height = 100.dp)
                }
            }

            uiState.place == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = VoyagerColors.OnSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Place not found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                val place = uiState.place
                if (place != null) {
                    PlaceDetailContent(
                        place = place,
                        analytics = uiState.analytics,
                        evidence = uiState.evidence,
                        geocodeCandidates = uiState.geocodeCandidates,
                        onIntent = viewModel::onIntent,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailContent(
    place: TimelinePlace,
    analytics: PlaceAnalytics?,
    evidence: ConfidenceBlock?,
    geocodeCandidates: List<GeocodeCandidate>,
    onIntent: (PlaceDetailIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        // Convert geocode candidates to PlaceNameSuggestion for the edit dialog
        val suggestions = remember(geocodeCandidates) {
            geocodeCandidates.map { candidate ->
                PlaceNameSuggestion(
                    name = candidate.displayName,
                    source = when (candidate.provider) {
                        GeocodingProviderId.ANDROID_GEOCODER -> SuggestionSource.GEOCODING
                        GeocodingProviderId.NOMINATIM -> SuggestionSource.OSM_NOMINATIM
                        GeocodingProviderId.PHOTON -> SuggestionSource.OSM_OVERPASS
                        else -> SuggestionSource.GEOCODING
                    },
                    confidence = candidate.confidence,
                    displayTitle = candidate.displayName,
                    displaySubtitle = candidate.provider.name.lowercase()
                        .replaceFirstChar { it.uppercase() }
                )
            }
        }
        PlaceEditDialog(
            place = place,
            suggestions = suggestions,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName, newCategory, _ ->
                if (newName != null && newName.isNotBlank()) {
                    onIntent(PlaceDetailIntent.Rename(newName.trim()))
                }
                if (newCategory != null) {
                    onIntent(PlaceDetailIntent.SetCategory(newCategory))
                }
                showRenameDialog = false
            }
        )
    }

    val confidence = evidence?.overall ?: place.confidence
    val needsIdentify = confidence < 0.5f ||
        place.nameSource.contains("Coordinate", ignoreCase = true)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PlaceInfoHeader(place, evidence) }

        // Rename / Change Category sit directly under the identity header.
        item {
            ActionButtons(
                currentEmoji = place.emoji,
                onRename = { showRenameDialog = true },
                showCategoryMenu = showCategoryMenu,
                onShowCategoryMenu = { showCategoryMenu = it },
                onSetCategory = { category -> onIntent(PlaceDetailIntent.SetCategory(category)) },
                onConfirm = { onIntent(PlaceDetailIntent.Confirm) },
                onRefreshGeocode = { onIntent(PlaceDetailIntent.RefreshGeocode) },
                onPickEmoji = { showEmojiPicker = true }
            )
        }

        // Low-confidence / unnamed → invite the user to name it.
        if (needsIdentify) {
            item { IdentifyLocationCard(onIdentify = { showRenameDialog = true }) }
        }

        if (analytics != null) {
            item { AnalyticsSection(analytics) }
        }

        if (evidence != null) {
            item { EvidenceSection(evidence) }
        }

        if (geocodeCandidates.isNotEmpty()) {
            item { SectionHeader(title = "Ranked Candidates") }
            items(geocodeCandidates) { candidate ->
                RankedCandidateCard(
                    candidate = candidate,
                    onAdopt = { onIntent(PlaceDetailIntent.Rename(candidate.displayName)) }
                )
            }
        }

        if (showEmojiPicker) {
            item {
                EmojiPickerCard(
                    currentEmoji = place.emoji,
                    onSelect = { emoji ->
                        onIntent(PlaceDetailIntent.SetEmoji(emoji))
                        showEmojiPicker = false
                    },
                    onDismiss = { showEmojiPicker = false }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun PlaceInfoHeader(place: TimelinePlace, evidence: ConfidenceBlock?) {
    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.GLASS,
        tint = VoyagerSurfaces.auroraSoft
    ) {
        // Category eyebrow + Home/Work semantic badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoyagerEyebrow(text = place.category.displayName)
            if (place.category == PlaceCategory.HOME || place.category == PlaceCategory.WORK) {
                VoyagerBadge(
                    text = place.category.displayName,
                    color = VoyagerColors.AccentAmber,
                    contentColor = VoyagerColors.AccentAmber,
                    style = BadgeStyle.OUTLINE,
                    icon = if (place.category == PlaceCategory.HOME) Icons.Default.Home else Icons.Default.Work
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            place.emoji?.let { Text(text = it, style = MaterialTheme.typography.headlineSmall) }
            Text(
                text = place.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface
            )
        }
        NameSourceIndicator(nameSource = place.nameSource)

        Spacer(modifier = Modifier.height(12.dp))

        val confidence = evidence?.overall ?: place.confidence
        ConfidenceBar(
            confidence = confidence,
            source = place.nameSource
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "%.5f, %.5f".format(place.lat, place.lng),
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalyticsSection(analytics: PlaceAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Visit history + temporal pattern ──
        VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Visit History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "${analytics.totalVisitCount} visits · avg ${formatDurationMs(analytics.avgDwellMs)}",
                    style = MonoStatSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }

            buildTemporalPattern(analytics)?.let { pattern ->
                Spacer(modifier = Modifier.height(12.dp))
                VoyagerEyebrow(text = "Temporal Pattern")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurface
                )
            }

            analytics.visitTrend?.let { trend ->
                Spacer(modifier = Modifier.height(8.dp))
                val (icon, color) = when (trend) {
                    Trend.UP -> Icons.Default.TrendingUp to VoyagerColors.AccentGreen
                    Trend.DOWN -> Icons.Default.TrendingDown to VoyagerColors.AccentRed
                    Trend.STABLE -> Icons.Default.TrendingFlat to VoyagerColors.OnSurfaceVariant
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Visit trend: ${trend.name.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }

        // ── Total duration ──
        VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
            VoyagerEyebrow(text = "Total Duration", color = VoyagerColors.OnSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTotalDuration(analytics.totalDwellMs),
                style = MonoStatLarge,
                color = VoyagerColors.Primary
            )
        }
    }
}

private fun buildTemporalPattern(a: PlaceAnalytics): String? {
    val day = a.dominantDayOfWeek?.let {
        java.time.DayOfWeek.of(it).name.lowercase().replaceFirstChar { c -> c.uppercase() }
    }
    val tod = a.dominantTimeOfDay
    return when {
        day != null && tod != null -> "Usually $day, $tod"
        day != null -> "Usually on $day"
        tod != null -> "Usually in the $tod"
        else -> null
    }
}

private fun formatTotalDuration(ms: Long): String {
    val hours = ms / 3_600_000.0
    return if (hours >= 1.0) "%.1f hrs".format(hours) else "${ms / 60_000} min"
}

@Composable
private fun EvidenceSection(evidence: ConfidenceBlock) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Confidence Breakdown")
        Spacer(modifier = Modifier.height(8.dp))

        ConfidenceRow("Overall", evidence.overall)
        evidence.arrival?.let { ConfidenceRow("Arrival", it) }
        evidence.departure?.let { ConfidenceRow("Departure", it) }
        evidence.category?.let { ConfidenceRow("Category", it) }
        evidence.label?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
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

/** Invites naming a low-confidence / unnamed place — taps straight into rename. */
@Composable
private fun IdentifyLocationCard(onIdentify: () -> Unit) {
    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.HIGHLIGHTED,
        onClick = onIdentify
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EditLocationAlt,
                contentDescription = null,
                tint = VoyagerColors.Primary,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Identify this location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "Add a name to improve accuracy and personal tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankedCandidateCard(candidate: GeocodeCandidate, onAdopt: () -> Unit) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS, padding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    maxLines = 1
                )
                Text(
                    text = "Source: ${providerLabel(candidate.provider)} · ${(candidate.confidence * 100).toInt()}% match",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            VoyagerOutlinedButton(onClick = onAdopt) {
                Text("Adopt", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun providerLabel(provider: GeocodingProviderId): String =
    provider.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun ActionButtons(
    currentEmoji: String?,
    onRename: () -> Unit,
    showCategoryMenu: Boolean,
    onShowCategoryMenu: (Boolean) -> Unit,
    onSetCategory: (PlaceCategory) -> Unit,
    onConfirm: () -> Unit,
    onRefreshGeocode: () -> Unit,
    onPickEmoji: () -> Unit
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Actions")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoyagerButton(
                onClick = onRename,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename")
            }

            Box(modifier = Modifier.weight(1f)) {
                VoyagerButton(
                    onClick = { onShowCategoryMenu(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Category, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Category")
                }

                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { onShowCategoryMenu(false) }
                ) {
                    PlaceCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Text(category.name.replace('_', ' ')
                                    .lowercase().replaceFirstChar { it.uppercase() })
                            },
                            onClick = {
                                onSetCategory(category)
                                onShowCategoryMenu(false)
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
            VoyagerButton(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm")
            }

            VoyagerOutlinedButton(
                onClick = onRefreshGeocode,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Re-geocode")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        VoyagerOutlinedButton(
            onClick = onPickEmoji,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(currentEmoji ?: "☐", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (currentEmoji != null) "Change emoji" else "Add emoji")
        }
    }
}

private val COMMON_EMOJIS = listOf(
    "🏠", "🏢", "🏫", "🏥", "🏪", "🏨", "🏦", "⛪",
    "☕", "🍕", "🍔", "🛒", "💊", "💪", "🎬", "🎮",
    "🌳", "🏖", "✈️", "🚉", "🚗", "🏃", "🛌", "📚"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerCard(
    currentEmoji: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Pick an Emoji")
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
            modifier = Modifier.height(112.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(COMMON_EMOJIS.size) { idx ->
                val emoji = COMMON_EMOJIS[idx]
                Surface(
                    onClick = { onSelect(emoji) },
                    shape = MaterialTheme.shapes.small,
                    color = if (emoji == currentEmoji) VoyagerColors.PrimaryContainer else VoyagerColors.SurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                        Text(text = emoji, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        if (currentEmoji != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { onSelect(null) }) {
                Text("Remove emoji", color = VoyagerColors.AccentRed)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Cancel", color = VoyagerColors.OnSurfaceVariant)
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
