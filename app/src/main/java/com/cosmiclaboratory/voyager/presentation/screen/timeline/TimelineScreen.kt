package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.ActiveVisitInfo
import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.DayArcSummary
import kotlinx.coroutines.delay
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    onSegmentClick: (Long) -> Unit = {},
    onPlaceClick: (Long) -> Unit = {},
    onShowOnMap: (segmentId: Long) -> Unit = {},
    onNavigateToDayStory: (dayKey: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    TimelineContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onSegmentClick = onSegmentClick,
        onPlaceClick = onPlaceClick,
        onShowOnMap = onShowOnMap,
        onNavigateToDayStory = onNavigateToDayStory
    )
}

/**
 * Stateless timeline body — takes state + an intent sink instead of the ViewModel,
 * so it can be rendered in @Preview and exercised in tests.
 */
@Composable
fun TimelineContent(
    uiState: TimelineUiState,
    onIntent: (TimelineIntent) -> Unit,
    onSegmentClick: (Long) -> Unit = {},
    onPlaceClick: (Long) -> Unit = {},
    onShowOnMap: (segmentId: Long) -> Unit = {},
    onNavigateToDayStory: (dayKey: String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        // ── Rough-timeline-mode banner (approximate location only) ──────
        com.cosmiclaboratory.voyager.presentation.screen.onboarding.RoughLocationBanner(
            visible = uiState.isRoughMode,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // ── Day Navigator ────────────────────────────────────────────────
        DayNavigator(
            dayLabel = formatDayKey(uiState.dayKey),
            onPrevious = { onIntent(TimelineIntent.NavigatePrevious) },
            onNext = { onIntent(TimelineIntent.NavigateNext) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            isToday = uiState.dayKey == java.time.LocalDate.now().toString(),
            trailingContent = {
                IconButton(
                    onClick = { onNavigateToDayStory(uiState.dayKey) },
                    enabled = uiState.dayKey.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Photo Day Story",
                        tint = VoyagerColors.Primary
                    )
                }
            }
        )

        // ── Day overview header — fixed above scroll ────────────────────
        if (uiState.segments.isNotEmpty()) {
            TimelineDayHeader(
                segments = uiState.segments,
                totalDistanceM = uiState.totalDistanceM,
                totalSteps = uiState.totalSteps,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // ── Current Location — fixed above scroll (today only) ──────────
        val activeVisit = uiState.activeVisit
        val pendingCandidate = uiState.pendingCandidate
        if (activeVisit != null) {
            CurrentLocationCard(
                visit = activeVisit,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else if (pendingCandidate != null && uiState.isTracking) {
            PendingLocationCard(
                candidate = pendingCandidate,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(5) {
                        ShimmerCard(height = 72.dp)
                    }
                }
            }

            uiState.errorMessage != null -> {
                ErrorStateComposable(
                    message = uiState.errorMessage ?: "",
                    modifier = Modifier.fillMaxSize()
                )
            }

            uiState.segments.isEmpty() -> {
                TimelineEmptyState(
                    reason = uiState.emptyReason,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                var swipeDeltaX by remember { mutableFloatStateOf(0f) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipeDeltaX > 80f) {
                                        onIntent(TimelineIntent.NavigatePrevious)
                                    } else if (swipeDeltaX < -80f) {
                                        onIntent(TimelineIntent.NavigateNext)
                                    }
                                    swipeDeltaX = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    swipeDeltaX += dragAmount
                                }
                            )
                        },
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Visual timeline rail — historical segments only
                    itemsIndexed(
                        items = uiState.segments,
                        key = { _, seg -> seg.segmentId }
                    ) { index, segment ->
                        TimelineRailItem(
                            segment = segment,
                            isFirst = index == 0,
                            isLast = index == uiState.segments.lastIndex,
                            isFocused = segment.segmentId == uiState.focusedSegmentId,
                            // Reviews are no longer surfaced on the timeline — they live
                            // quietly under the dashboard's notification (bell) instead.
                            isReview = false,
                            onClick = { onSegmentClick(segment.segmentId) },
                            onPlaceClick = {
                                segment.place?.let { place -> onPlaceClick(place.placeId) }
                            },
                            onSelectGeocodeName = { placeId, name ->
                                onIntent(TimelineIntent.SelectGeocodeName(placeId, name))
                            },
                            onShowOnMap = { segmentId ->
                                onIntent(TimelineIntent.SelectSegment(segmentId))
                                onShowOnMap(segmentId)
                            },
                            onRenamePlace = { placeId, newName ->
                                onIntent(TimelineIntent.RenamePlace(placeId, newName))
                            },
                            onConfirmPlace = { placeId ->
                                onIntent(TimelineIntent.ConfirmPlace(placeId))
                            },
                            onReclassify = { newType ->
                                onIntent(TimelineIntent.CorrectSegmentType(segment.segmentId, newType))
                            },
                            nextSegmentType = uiState.segments.getOrNull(index + 1)?.type
                        )
                    }

                }
            }
        }
    }
}

// ── Timeline Rail Item ───────────────────────────────────────────────────────

@Composable
private fun TimelineRailItem(
    segment: TimelineSegment,
    isFirst: Boolean,
    isLast: Boolean,
    isFocused: Boolean,
    isReview: Boolean,
    onClick: () -> Unit,
    onPlaceClick: () -> Unit,
    onSelectGeocodeName: (placeId: Long, name: String) -> Unit = { _, _ -> },
    onShowOnMap: (segmentId: Long) -> Unit = {},
    onRenamePlace: (placeId: Long, newName: String) -> Unit = { _, _ -> },
    onConfirmPlace: (placeId: Long) -> Unit = {},
    onReclassify: (newType: String) -> Unit = {},
    nextSegmentType: SegmentType? = null
) {
    val nodeColor = transportColor(segment.type)
    val isVisit = segment.type == SegmentType.VISIT
    val isGap = segment.type == SegmentType.GAP
    val nextColor = nextSegmentType?.let { transportColor(it) } ?: nodeColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // ── Left rail: timestamp + node + vertical line ──────────────
        Box(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawSpine(
                        isFirst = isFirst,
                        isLast = isLast,
                        isVisit = isVisit,
                        isGap = isGap,
                        isReview = isReview,
                        nodeColor = nodeColor,
                        nextColor = nextColor
                    )
                }
        ) {
            Text(
                text = formatTime(segment.startAt),
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )
        }

        // Right content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 4.dp, bottom = if (isLast) 4.dp else 14.dp)
        ) {
            when {
                isVisit -> VisitSegmentContent(
                    segment = segment,
                    isFocused = isFocused,
                    isReview = isReview,
                    onPlaceClick = onPlaceClick,
                    onShowOnMap = onShowOnMap,
                    onRenamePlace = onRenamePlace,
                    onConfirmPlace = onConfirmPlace
                )
                isGap -> QuietGapContent(segment)
                else -> MovementSegmentContent(segment, onReclassify = onReclassify)
            }
        }
    }
}

/**
 * Draws the continuous timeline spine for one row: an unbroken vertical line (top
 * half skipped on the first row, bottom half on the last) with the node on top, so
 * adjacent rows connect into a single Arc-style spine. The node carries the
 * confidence cue — an amber ring when the row needs review; gaps render as a faint
 * dashed span with a hollow node (no error card).
 */
private fun DrawScope.drawSpine(
    isFirst: Boolean,
    isLast: Boolean,
    isVisit: Boolean,
    isGap: Boolean,
    isReview: Boolean,
    nodeColor: Color,
    nextColor: Color
) {
    val cx = size.width - 10.dp.toPx()
    val nodeCy = 14.dp.toPx()
    val strokeW = 2.dp.toPx()
    val dash = if (isGap) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
    val lineColor = (if (isGap) VoyagerColors.TransportGap else nodeColor)
        .copy(alpha = if (isGap) 0.35f else 0.45f)
    val nextLineColor = nextColor.copy(alpha = 0.45f)

    val top = if (isFirst) nodeCy else 0f
    val bottom = if (isLast) nodeCy else size.height

    // Above the node — single colour.
    if (top < nodeCy) {
        drawLine(lineColor, Offset(cx, top), Offset(cx, nodeCy), strokeW, pathEffect = dash)
    }
    // Below the node — gradient into the next segment's colour when modes differ.
    if (bottom > nodeCy) {
        if (!isGap && lineColor != nextLineColor) {
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor, nextLineColor),
                    startY = nodeCy, endY = bottom
                ),
                start = Offset(cx, nodeCy), end = Offset(cx, bottom), strokeWidth = strokeW
            )
        } else {
            drawLine(lineColor, Offset(cx, nodeCy), Offset(cx, bottom), strokeW, pathEffect = dash)
        }
    }

    // Node — punch a hole in the line, then draw on top.
    if (isGap) {
        drawCircle(VoyagerColors.Background, 5.dp.toPx(), Offset(cx, nodeCy))
        drawCircle(
            color = VoyagerColors.TransportGap.copy(alpha = 0.6f),
            radius = 3.5.dp.toPx(),
            center = Offset(cx, nodeCy),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
        )
    } else {
        val nodeR = if (isVisit) 6.dp.toPx() else 4.dp.toPx()
        drawCircle(VoyagerColors.Background, nodeR + 1.5.dp.toPx(), Offset(cx, nodeCy))
        drawCircle(nodeColor, nodeR, Offset(cx, nodeCy))
        if (isReview) {
            drawCircle(
                color = VoyagerColors.Warning,
                radius = nodeR + 3.dp.toPx(),
                center = Offset(cx, nodeCy),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

// ── Visit Segment Content ────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VisitSegmentContent(
    segment: TimelineSegment,
    isFocused: Boolean,
    isReview: Boolean,
    onPlaceClick: () -> Unit,
    onShowOnMap: (segmentId: Long) -> Unit = {},
    onRenamePlace: (placeId: Long, newName: String) -> Unit = { _, _ -> },
    onConfirmPlace: (placeId: Long) -> Unit = {}
) {
    val place = segment.place
    val variant = if (isFocused) CardVariant.HIGHLIGHTED else CardVariant.GLASS
    var showRenameSheet by remember { mutableStateOf(false) }
    var showEvidence by remember { mutableStateOf(false) }

    if (showEvidence) {
        EvidenceSheet(evidence = segment.toEvidence(), onDismiss = { showEvidence = false })
    }

    val categoryTint = when (place?.category) {
        PlaceCategory.HOME -> VoyagerColors.AccentAmber.copy(alpha = 0.12f)
        PlaceCategory.WORK -> VoyagerColors.Primary.copy(alpha = 0.10f)
        PlaceCategory.GYM -> VoyagerColors.AccentGreen.copy(alpha = 0.10f)
        PlaceCategory.RESTAURANT -> VoyagerColors.AccentOrange.copy(alpha = 0.10f)
        PlaceCategory.SHOPPING -> VoyagerColors.AccentPurple.copy(alpha = 0.10f)
        PlaceCategory.ENTERTAINMENT -> VoyagerColors.AccentPurple.copy(alpha = 0.10f)
        PlaceCategory.TRANSIT_HUB, PlaceCategory.TRANSPORT, PlaceCategory.TRAVEL ->
            VoyagerColors.AccentBlue.copy(alpha = 0.10f)
        PlaceCategory.EDUCATION -> VoyagerColors.Primary.copy(alpha = 0.08f)
        PlaceCategory.HEALTHCARE -> VoyagerColors.AccentRed.copy(alpha = 0.08f)
        else -> null
    }

    if (showRenameSheet && place != null && place.placeId > 0) {
        QuickRenameSheet(
            currentName = place.displayName,
            onConfirm = { newName ->
                onRenamePlace(place.placeId, newName)
                showRenameSheet = false
            },
            onDismiss = { showRenameSheet = false }
        )
    }

    val canName = place != null && place.placeId > 0
    val title = place?.displayName?.takeIf { it.isNotBlank() } ?: "Location unavailable"
    // Secondary line: category and recurrence only — the rest moves to the Why sheet.
    val secondary = buildList {
        if (place != null && place.category != PlaceCategory.UNKNOWN) add(place.category.displayName)
        place?.recurrenceLabel?.let { add(it) }
    }.joinToString("  \u00b7  ")

    VoyagerCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlaceClick,
                onLongClick = { if (canName) showRenameSheet = true }
            ),
        padding = 10.dp,
        variant = variant,
        tintColor = categoryTint,
        onClick = null
    ) {
        // Primary line: emoji + name (bold) + duration + map shortcut.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            place?.emoji?.let { emoji ->
                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatDurationCompact(segment.durationMs),
                style = MonoStatSmall,
                color = VoyagerColors.Primary
            )
            IconButton(
                onClick = { onShowOnMap(segment.segmentId) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Show on map",
                    tint = VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Secondary line: muted category and recurrence.
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Affordances: always-visible Why?, plus confirm/name when this row needs review.
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WhyChip(onClick = { showEvidence = true })
            if (isReview && place != null && place.placeId > 0) {
                Box(modifier = Modifier.clickable { onConfirmPlace(place.placeId) }) {
                    VoyagerBadge(
                        text = "Confirm",
                        color = VoyagerColors.AccentGreen,
                        contentColor = VoyagerColors.AccentGreen,
                        style = BadgeStyle.OUTLINE,
                        icon = Icons.Default.Check
                    )
                }
            }
            if (isReview && canName) {
                Box(modifier = Modifier.clickable { showRenameSheet = true }) {
                    VoyagerBadge(
                        text = "Name it",
                        color = VoyagerColors.Primary,
                        contentColor = VoyagerColors.Primary,
                        style = BadgeStyle.OUTLINE,
                        icon = Icons.Default.Edit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickRenameSheet(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoyagerColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Rename Place",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Place name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VoyagerColors.Primary,
                    unfocusedBorderColor = VoyagerColors.SurfaceVariant
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = VoyagerColors.Primary)
                ) { Text("Save") }
            }
        }
    }
}

// ── Movement Segment Content ─────────────────────────────────────────────────

@Composable
private fun MovementSegmentContent(
    segment: TimelineSegment,
    onReclassify: (newType: String) -> Unit = {}
) {
    if (segment.isUnifiedTravel) {
        UnifiedTravelContent(segment)
    } else {
        SingleMovementContent(segment, onReclassify = onReclassify)
    }
}

private fun isReliableMovementType(type: SegmentType) =
    type == SegmentType.WALK || type == SegmentType.RUN

@Composable
private fun SingleMovementContent(
    segment: TimelineSegment,
    onReclassify: (newType: String) -> Unit = {}
) {
    val reliable = isReliableMovementType(segment.type)
    val labelText = if (reliable)
        segment.type.name.lowercase().replaceFirstChar { it.uppercase() }
    else
        "Movement"
    val labelColor = if (reliable) transportColor(segment.type) else VoyagerColors.OnSurfaceVariant

    var showEvidence by remember { mutableStateOf(false) }
    var showReclassify by remember { mutableStateOf(false) }

    if (showEvidence) {
        EvidenceSheet(evidence = segment.toEvidence(), onDismiss = { showEvidence = false })
    }
    if (showReclassify) {
        ReclassifySheet(
            current = segment.type,
            onReclassify = { newType -> onReclassify(newType); showReclassify = false },
            onDismiss = { showReclassify = false }
        )
    }

    val steps = segment.evidence?.stepCount
    val preview = segment.route?.simplifiedPolyline?.takeIf { it.isNotBlank() }
        ?: segment.route?.encodedPolyline?.takeIf { it.isNotBlank() }

    Column {
        // Primary line: mode + distance/steps + duration (speed/samples move to the Why sheet).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportModeIcon(segmentType = segment.type, size = 18.dp)
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )
            if (segment.distanceM > 0) {
                Text(
                    text = formatDistance(segment.distanceM),
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            if (steps != null && steps > 0 && reliable) {
                Text(
                    text = "$steps steps",
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Text(
                text = formatDurationCompact(segment.durationMs),
                style = MonoTimestamp,
                color = labelColor
            )
        }

        // Second line: route preview (when a path exists) + explain/reclassify affordances.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (preview != null) {
                RouteSparkline(encodedPolyline = preview, color = transportColor(segment.type))
            }
            WhyChip(onClick = { showEvidence = true })
            Box(modifier = Modifier.clickable { showReclassify = true }) {
                VoyagerBadge(
                    text = "Reclassify",
                    color = VoyagerColors.AccentPurple,
                    contentColor = VoyagerColors.AccentPurple,
                    style = BadgeStyle.OUTLINE,
                    icon = Icons.Default.Tune
                )
            }
        }
    }
}

@Composable
private fun UnifiedTravelContent(segment: TimelineSegment) {
    val subSegments = segment.subSegments ?: return

    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 10.dp,
        variant = CardVariant.GLASS
    ) {
        // Header row: "Travel" with total stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Travel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (segment.distanceM > 0) {
                    Text(
                        text = formatDistance(segment.distanceM),
                        style = MonoTimestamp,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
                Text(
                    text = formatDurationCompact(segment.durationMs),
                    style = MonoStatSmall,
                    color = VoyagerColors.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sub-segment breakdown: each leg as a compact row
        subSegments.forEach { leg ->
            val legReliable = isReliableMovementType(leg.type)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransportModeIcon(segmentType = leg.type, size = 16.dp)
                if (legReliable) {
                    Text(
                        text = leg.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = transportColor(leg.type),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (leg.distanceM > 0) {
                    Text(
                        text = formatDistance(leg.distanceM),
                        style = MonoTimestamp,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
                leg.route?.let { route ->
                    if (route.avgSpeedMps > 0) {
                        Text(
                            text = formatSpeed(route.avgSpeedMps),
                            style = MonoTimestamp,
                            color = VoyagerColors.AccentBlue.copy(alpha = 0.85f)
                        )
                    }
                }
                Text(
                    text = formatDurationCompact(leg.durationMs),
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Gap Segment Content ──────────────────────────────────────────────────────

@Composable
private fun QuietGapContent(segment: TimelineSegment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Bedtime,
            contentDescription = null,
            tint = VoyagerColors.TransportGap,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = quietGapCopy(segment.gapReason),
            style = MaterialTheme.typography.labelMedium,
            color = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDurationCompact(segment.durationMs),
            style = MonoTimestamp,
            color = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** Calm, non-alarming copy for a tracking gap — never an error message. */
private fun quietGapCopy(reason: String?): String = when (reason?.uppercase()) {
    "PERMISSION" -> "Quiet \u00b7 location was off"
    "DOZE", "DORMANT" -> "Quiet \u00b7 phone was still"
    "GPS_LOSS" -> "Quiet \u00b7 no signal"
    "PROCESS_DEAD" -> "Quiet \u00b7 tracking restarted"
    "MANUAL_PAUSE" -> "Quiet \u00b7 tracking paused"
    else -> "Quiet hours"
}


// ── Current Location Card (fixed, not scrollable) ──────────────────────────

@Composable
private fun CurrentLocationCard(
    visit: ActiveVisitInfo,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val dwellMs = (now - visit.arrivalAt).coerceAtLeast(0)

    VoyagerCard(
        modifier = modifier.fillMaxWidth(),
        padding = 12.dp,
        variant = CardVariant.HIGHLIGHTED
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingDot(size = 10.dp, color = VoyagerColors.AccentBlue)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = visit.placeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Since ${formatTime(visit.arrivalAt)}",
                        style = MonoTimestamp,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                    if (visit.category.name != "UNKNOWN") {
                        CategoryChip(categoryName = visit.category.displayName)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDurationCompact(dwellMs),
                    style = MonoStatSmall,
                    color = VoyagerColors.AccentBlue
                )
                VoyagerBadge(
                    text = "Now",
                    color = VoyagerColors.AccentBlue.copy(alpha = 0.15f),
                    contentColor = VoyagerColors.AccentBlue
                )
            }
        }
    }
}

// ── Pending Location Card (fixed, not scrollable) ──────────────────────────

@Composable
private fun PendingLocationCard(
    candidate: PendingVisitCandidate,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedMs = (now - candidate.accumulationStartAt).coerceAtLeast(0)

    VoyagerCard(
        modifier = modifier.fillMaxWidth(),
        padding = 12.dp,
        variant = CardVariant.FLAT
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingDot(size = 8.dp, color = VoyagerColors.PrimaryDim)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detecting location...",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Text(
                text = "${candidate.sampleCount} samples",
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant
            )
            Text(
                text = formatDurationCompact(elapsedMs),
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

// ── Day Summary Bar ─────────────────────────────────────────────────────────

@Composable
private fun TimelineDayHeader(
    segments: List<TimelineSegment>,
    totalDistanceM: Double,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val summary = remember(segments) { DayArcSummary.summarize(segments) }
    VoyagerCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.GLASS,
        padding = 12.dp
    ) {
        // Day-arc: the whole day's shape at a glance.
        DayArcBar(slices = summary.slices)
        Spacer(modifier = Modifier.height(8.dp))

        // Dominant mode + active span.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dominantModeLabel(summary.dominantMode).uppercase(),
                fontFamily = com.cosmiclaboratory.voyager.ui.theme.JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = summary.dominantMode?.let { transportColor(it) } ?: VoyagerColors.Primary
            )
            activeSpanLabel(summary.firstActivityAt, summary.lastActivityAt)?.let { span ->
                Text(text = span, style = MonoTimestamp, color = VoyagerColors.OnSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Stats.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryPill(value = formatDistance(totalDistanceM), label = "Distance")
            SummaryPill(value = "%,d".format(totalSteps), label = "Steps")
            SummaryPill(value = "${summary.visitCount}", label = "Places")
            SummaryPill(value = "${summary.tripCount}", label = "Trips")
        }
    }
}

private fun dominantModeLabel(mode: SegmentType?): String = when (mode) {
    SegmentType.WALK -> "Mostly walking"
    SegmentType.RUN -> "Mostly running"
    SegmentType.CYCLE -> "Mostly cycling"
    SegmentType.DRIVE -> "Mostly driving"
    SegmentType.TRANSIT -> "Mostly transit"
    SegmentType.FLIGHT -> "Mostly flying"
    else -> "Mostly stationary"
}

private fun activeSpanLabel(firstAt: Long?, lastAt: Long?): String? {
    if (firstAt == null || lastAt == null) return null
    return "${formatTime(firstAt)} \u2192 ${formatTime(lastAt)}"
}

@Composable
private fun TimelineEmptyState(
    reason: TimelineEmptyReason,
    modifier: Modifier = Modifier
) {
    when (reason) {
        TimelineEmptyReason.NO_PERMISSION ->
            EmptyStateComposable(type = EmptyStateType.NO_PERMISSION, modifier = modifier)
        TimelineEmptyReason.TRACKING_OFF ->
            EmptyStateComposable(type = EmptyStateType.NO_TRACKING, modifier = modifier)
        TimelineEmptyReason.CAPTURING_NOW ->
            CalmEmptyState(
                title = "Capturing now",
                subtitle = "Your day will appear here as you move.",
                showPulse = true,
                modifier = modifier
            )
        else ->
            CalmEmptyState(
                title = "A quiet day",
                subtitle = "Nothing was recorded for this day.",
                showPulse = false,
                modifier = modifier
            )
    }
}

@Composable
private fun CalmEmptyState(
    title: String,
    subtitle: String,
    showPulse: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showPulse) {
                PulsingDot(size = 12.dp, color = VoyagerColors.AccentBlue)
            } else {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = VoyagerColors.OnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SummaryPill(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MonoStatSmall,
            color = VoyagerColors.Primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

// ── Utility Functions ────────────────────────────────────────────────────────

private fun formatTime(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val local = java.time.LocalTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    return local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatDayKey(dayKey: String): String {
    if (dayKey.isBlank()) return ""
    return try {
        val date = java.time.LocalDate.parse(dayKey)
        val today = java.time.LocalDate.now()
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        }
    } catch (_: Exception) {
        dayKey
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) String.format("%.1f km", meters / 1000) else String.format("%.0f m", meters)
}

private fun formatSpeed(mps: Float): String {
    val kmh = mps * 3.6f
    return if (kmh >= 1f) String.format("%.0f km/h", kmh) else String.format("%.1f m/s", mps)
}

/** Confidence color ramp — red <40% · amber 40–70% · green >70%. Always paired with the % text. */
private fun confidenceColor(confidence: Float): Color = when {
    confidence < 0.4f -> VoyagerColors.Error
    confidence < 0.7f -> VoyagerColors.Warning
    else -> VoyagerColors.AccentGreen
}

// ── Evidence + Correction ─────────────────────────────────────────────────────

/**
 * Builds a reusable [VoyagerEvidence] from a segment's on-device inference data.
 * Prefers the real [InferenceExplanation]; otherwise composes an honest fallback
 * from the raw [EvidenceBlock] metrics.
 */
private fun TimelineSegment.toEvidence(): VoyagerEvidence {
    val ev = evidence
    val exp = ev?.explanation
    val title = when {
        place != null -> place.displayName
        type == SegmentType.GAP -> "Tracking gap"
        else -> type.name.lowercase().replaceFirstChar { it.uppercase() } + " segment"
    }
    val metrics = linkedMapOf<String, String>()
    exp?.supportingMetrics?.forEach { (k, v) -> metrics[k] = v.toString() }
    if (metrics.isEmpty() && ev != null) {
        metrics["GPS samples"] = ev.sampleCount.toString()
        ev.avgSpeed?.takeIf { it > 0f }?.let { metrics["Avg speed"] = formatSpeed(it) }
        ev.maxSpeed?.takeIf { it > 0f }?.let { metrics["Max speed"] = formatSpeed(it) }
        ev.stepCount?.takeIf { it > 0 }?.let { metrics["Steps"] = it.toString() }
        ev.headingConsistency?.takeIf { it > 0f }?.let { metrics["Heading consistency"] = "${(it * 100).toInt()}%" }
        if (distanceM > 0) metrics["Distance"] = formatDistance(distanceM)
    }
    val sources = exp?.sourceSet ?: ev?.providerMix?.keys ?: emptySet()
    return VoyagerEvidence(
        title = title,
        humanExplanation = exp?.humanExplanation ?: defaultExplanation(),
        confidence = confidence.takeIf { it > 0f },
        supportingMetrics = metrics,
        counterEvidence = exp?.counterEvidence ?: emptyList(),
        sources = sources,
        ruleVersion = exp?.ruleVersion
    )
}

private fun TimelineSegment.defaultExplanation(): String {
    val samples = evidence?.sampleCount ?: 0
    val dwell = formatDurationCompact(durationMs)
    return when (type) {
        SegmentType.VISIT, SegmentType.DWELL ->
            "Voyager logged a visit here because your location stayed within a small radius for $dwell" +
                if (samples > 0) ", supported by $samples GPS samples." else "."
        SegmentType.WALK, SegmentType.RUN, SegmentType.CYCLE,
        SegmentType.DRIVE, SegmentType.TRANSIT, SegmentType.FLIGHT ->
            "Classified as ${type.name.lowercase()} from ${if (samples > 0) "$samples " else ""}GPS samples" +
                (evidence?.avgSpeed?.takeIf { it > 0f }?.let { " at about ${formatSpeed(it)}." } ?: ".")
        SegmentType.GAP ->
            "Tracking paused here. ${gapReason ?: "Reason unknown"}."
        else ->
            "Voyager grouped these samples into one ${type.name.lowercase()} segment."
    }
}

/** Inline movement reclassification — wired to [TimelineIntent.CorrectSegmentType]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReclassifySheet(
    current: SegmentType,
    onReclassify: (newType: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val options = listOf(
        SegmentType.WALK, SegmentType.RUN, SegmentType.CYCLE,
        SegmentType.DRIVE, SegmentType.TRANSIT
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoyagerColors.SurfaceOverlay,
        shape = VoyagerShapes.sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Reclassify movement",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Pick the correct mode. Your correction teaches Voyager and stays on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            options.forEach { opt ->
                val selected = opt == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VoyagerShapes.button)
                        .clickable {
                            android.widget.Toast.makeText(
                                context,
                                "Saved — this helps Voyager learn",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onReclassify(opt.name)
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TransportModeIcon(segmentType = opt, size = 22.dp)
                    Text(
                        text = opt.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Current",
                            tint = VoyagerColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
