package com.cosmiclaboratory.voyager.presentation.screen.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.ActiveVisitInfo
import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
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
    onShowOnMap: (segmentId: Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        // ── Day Navigator ────────────────────────────────────────────────
        DayNavigator(
            dayLabel = formatDayKey(uiState.dayKey),
            onPrevious = { viewModel.onIntent(TimelineIntent.NavigatePrevious) },
            onNext = { viewModel.onIntent(TimelineIntent.NavigateNext) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            isToday = uiState.dayKey == java.time.LocalDate.now().toString()
        )

        // ── Day Summary Bar — fixed above scroll ────────────────────────
        if (uiState.segments.isNotEmpty()) {
            DaySummaryBar(
                totalDistanceM = uiState.totalDistanceM,
                totalSteps = uiState.totalSteps,
                visitCount = uiState.segments.count { it.type == SegmentType.VISIT || it.type == SegmentType.DWELL },
                tripCount = uiState.segments.count { it.type != SegmentType.VISIT && it.type != SegmentType.DWELL && it.type != SegmentType.GAP },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ── Current Location — fixed above scroll (today only) ──────────
        if (uiState.activeVisit != null) {
            CurrentLocationCard(
                visit = uiState.activeVisit!!,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        } else if (uiState.pendingCandidate != null && uiState.isTracking) {
            PendingLocationCard(
                candidate = uiState.pendingCandidate!!,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VoyagerCard {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = VoyagerColors.Error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VoyagerColors.Error
                            )
                        }
                    }
                }
            }

            uiState.segments.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
                            val r = size.minDimension / 2
                            drawCircle(
                                color = VoyagerColors.SurfaceVariant,
                                radius = r,
                                style = androidx.compose.ui.graphics.drawscope.Fill
                            )
                            drawCircle(
                                color = VoyagerColors.PrimaryDim.copy(alpha = 0.3f),
                                radius = r,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                            // Route dots
                            val dotR = r * 0.12f
                            val offsets = listOf(
                                Offset(center.x, center.y - r * 0.45f),
                                Offset(center.x - r * 0.3f, center.y),
                                Offset(center.x + r * 0.3f, center.y),
                                Offset(center.x, center.y + r * 0.45f)
                            )
                            offsets.forEach { o ->
                                drawCircle(VoyagerColors.Primary.copy(alpha = 0.5f), dotR, o)
                            }
                            for (i in 0 until offsets.size - 1) {
                                drawLine(
                                    VoyagerColors.PrimaryDim.copy(alpha = 0.3f),
                                    offsets[i], offsets[i + 1],
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                                )
                            }
                        }
                        Text(
                            text = if (uiState.isTracking) "Move around to build your timeline"
                                   else "Nothing recorded here yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = VoyagerColors.OnSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = if (uiState.isTracking) "Your path will appear as you move"
                                   else "Start tracking to capture your day",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
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
                                        viewModel.onIntent(TimelineIntent.NavigatePrevious)
                                    } else if (swipeDeltaX < -80f) {
                                        viewModel.onIntent(TimelineIntent.NavigateNext)
                                    }
                                    swipeDeltaX = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    swipeDeltaX += dragAmount
                                }
                            )
                        },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                            onClick = { onSegmentClick(segment.segmentId) },
                            onPlaceClick = {
                                segment.place?.let { place -> onPlaceClick(place.placeId) }
                            },
                            onSelectGeocodeName = { placeId, name ->
                                viewModel.onIntent(TimelineIntent.SelectGeocodeName(placeId, name))
                            },
                            onShowOnMap = { segmentId ->
                                viewModel.onIntent(TimelineIntent.SelectSegment(segmentId))
                                onShowOnMap(segmentId)
                            },
                            onRenamePlace = { placeId, newName ->
                                viewModel.onIntent(TimelineIntent.RenamePlace(placeId, newName))
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
    onClick: () -> Unit,
    onPlaceClick: () -> Unit,
    onSelectGeocodeName: (placeId: Long, name: String) -> Unit = { _, _ -> },
    onShowOnMap: (segmentId: Long) -> Unit = {},
    onRenamePlace: (placeId: Long, newName: String) -> Unit = { _, _ -> },
    nextSegmentType: SegmentType? = null
) {
    val (_, nodeColor) = transportIconAndColor(segment.type)
    val isVisit = segment.type == SegmentType.VISIT
    val isGap = segment.type == SegmentType.GAP

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        // ── Left rail: timestamp + node + vertical line ──────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(72.dp)
        ) {
            // Entry time
            Text(
                text = formatTime(segment.startAt),
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant
            )
            // Exit time for visits
            if (isVisit && segment.endAt > segment.startAt) {
                Text(
                    text = "\u2192 ${formatTime(segment.endAt)}",
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Node
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isVisit -> {
                        // Filled circle for visits
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(nodeColor, CircleShape)
                        )
                    }
                    isGap -> {
                        // Dashed node for gaps
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(
                                color = VoyagerColors.TransportGap,
                                radius = size.minDimension / 2,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(4f, 4f)
                                    )
                                )
                            )
                        }
                    }
                    else -> {
                        // Horizontal dash for movement — continuous-flow feel
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawLine(
                                color = nodeColor,
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // Vertical connector line — gradient when transitioning between segment types
            if (!isLast) {
                val lineHeight = if (isVisit) 48.dp else 32.dp
                val (_, nextNodeColor) = if (nextSegmentType != null)
                    transportIconAndColor(nextSegmentType)
                else nodeColor to nodeColor

                Canvas(
                    modifier = Modifier
                        .width(2.dp)
                        .height(lineHeight)
                ) {
                    val pathEffect = if (isGap) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
                    val lineColor = nodeColor.copy(alpha = 0.5f)
                    val nextColor = nextNodeColor.copy(alpha = 0.5f)

                    if (lineColor != nextColor && pathEffect == null) {
                        drawLine(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(lineColor, nextColor),
                                startY = 0f,
                                endY = size.height
                            ),
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    } else {
                        drawLine(
                            color = lineColor,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = pathEffect
                        )
                    }
                }
            }
        }

        // ── Right content: segment details ───────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = if (isLast) 0.dp else 8.dp)
        ) {
            when {
                isVisit -> VisitSegmentContent(
                    segment = segment,
                    isFocused = isFocused,
                    onPlaceClick = onPlaceClick,
                    onSelectGeocodeName = onSelectGeocodeName,
                    onShowOnMap = onShowOnMap,
                    onRenamePlace = onRenamePlace
                )
                isGap -> GapSegmentContent(segment)
                else -> MovementSegmentContent(segment)
            }
        }
    }
}

// ── Visit Segment Content ────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VisitSegmentContent(
    segment: TimelineSegment,
    isFocused: Boolean,
    onPlaceClick: () -> Unit,
    onSelectGeocodeName: (placeId: Long, name: String) -> Unit = { _, _ -> },
    onShowOnMap: (segmentId: Long) -> Unit = {},
    onRenamePlace: (placeId: Long, newName: String) -> Unit = { _, _ -> }
) {
    val place = segment.place
    val variant = if (isFocused) CardVariant.HIGHLIGHTED else CardVariant.FLAT
    var showRenameSheet by remember { mutableStateOf(false) }

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

    VoyagerCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlaceClick,
                onLongClick = { if (place != null && place.placeId > 0) showRenameSheet = true }
            ),
        padding = 10.dp,
        variant = variant,
        tintColor = categoryTint,
        onClick = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    segment.sequenceNumber?.let { num ->
                        Text(
                            text = "$num.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = VoyagerColors.Primary
                        )
                    }
                    place?.emoji?.let { emoji ->
                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = place?.displayName ?: "Location pending",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (place != null) {
                    NameSourceIndicator(nameSource = place.nameSource)
                }
                // Geocode hints: show 1-2 alternative names from other providers
                if (place != null && place.geocodeHints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        color = VoyagerColors.SurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            place.geocodeHints.forEach { hint ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        onSelectGeocodeName(place.placeId, hint.name)
                                    }
                                ) {
                                    VoyagerBadge(
                                        text = hint.provider,
                                        color = VoyagerColors.PrimaryDim.copy(alpha = 0.15f),
                                        contentColor = VoyagerColors.PrimaryDim
                                    )
                                    Text(
                                        text = hint.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VoyagerColors.OnSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDurationCompact(segment.durationMs),
                    style = MonoStatSmall,
                    color = VoyagerColors.Primary
                )
                IconButton(
                    onClick = { onShowOnMap(segment.segmentId) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Show on map",
                        tint = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (place != null && place.category.name != "UNKNOWN") {
                CategoryChip(categoryName = place.category.displayName)
            }
            segment.evidence?.let { ev ->
                VoyagerBadge(
                    text = "${ev.sampleCount} samples",
                    color = VoyagerColors.AccentBlue.copy(alpha = 0.1f),
                    contentColor = VoyagerColors.AccentBlue
                )
            }
            if (segment.confidence > 0f) {
                VoyagerBadge(
                    text = "${(segment.confidence * 100).toInt()}%",
                    color = VoyagerColors.AccentGreen.copy(alpha = 0.1f),
                    contentColor = VoyagerColors.AccentGreen
                )
            }
        }

        if (place != null && place.visitCount > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Visited ${place.visitCount} times",
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }

        if (segment.isUserCorrected) {
            Spacer(modifier = Modifier.height(4.dp))
            VoyagerBadge(
                text = "Corrected",
                color = VoyagerColors.AccentPurple.copy(alpha = 0.15f),
                contentColor = VoyagerColors.AccentPurple
            )
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
private fun MovementSegmentContent(segment: TimelineSegment) {
    if (segment.isUnifiedTravel) {
        UnifiedTravelContent(segment)
    } else {
        SingleMovementContent(segment)
    }
}

private fun isReliableMovementType(type: SegmentType) =
    type == SegmentType.WALK || type == SegmentType.RUN

@Composable
private fun SingleMovementContent(segment: TimelineSegment) {
    val reliable = isReliableMovementType(segment.type)
    val labelText = if (reliable)
        segment.type.name.lowercase().replaceFirstChar { it.uppercase() }
    else
        "Movement"
    val labelColor = if (reliable) transportColor(segment.type) else VoyagerColors.OnSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransportModeIcon(segmentType = segment.type, size = 20.dp)

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

        val steps = segment.evidence?.stepCount
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
    segment.evidence?.let { ev ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ev.avgSpeed?.takeIf { it > 0f }?.let { speed ->
                Text(
                    text = formatSpeed(speed),
                    style = MonoTimestamp,
                    color = VoyagerColors.AccentBlue.copy(alpha = 0.85f)
                )
            }
            Text(
                text = "${ev.sampleCount} samples",
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun UnifiedTravelContent(segment: TimelineSegment) {
    val subSegments = segment.subSegments ?: return

    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 10.dp,
        variant = CardVariant.FLAT
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
private fun GapSegmentContent(segment: TimelineSegment) {
    GapSegmentCard(
        gapReason = segment.gapReason,
        durationMs = segment.durationMs
    )
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
private fun DaySummaryBar(
    totalDistanceM: Double,
    totalSteps: Int,
    visitCount: Int,
    tripCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = VoyagerColors.SurfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryPill(value = formatDistance(totalDistanceM), label = "Distance")
            SummaryPill(value = "$totalSteps", label = "Steps")
            SummaryPill(value = "$visitCount", label = "Visits")
            SummaryPill(value = "$tripCount", label = "Trips")
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
