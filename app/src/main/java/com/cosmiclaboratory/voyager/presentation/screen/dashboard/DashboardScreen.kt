package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.screen.tracking.TrackingControlBanner
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp

/**
 * Home Screen — merged Dashboard + Track.
 *
 * Shows: live tracking status (via TrackingControlBanner), daily stats,
 * top places, insights, and anomalies. NO Scaffold/TopBar — persistent
 * top bar lives at NavHost level in MainActivity.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToInsights: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onRunPlaceDetection: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var staggerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            delay(50)
            staggerVisible = true
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VoyagerColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShimmerCard(height = 100.dp, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShimmerCard(height = 72.dp, modifier = Modifier.weight(1f))
                    ShimmerCard(height = 72.dp, modifier = Modifier.weight(1f))
                    ShimmerCard(height = 72.dp, modifier = Modifier.weight(1f))
                }
                ShimmerCard(height = 60.dp, modifier = Modifier.padding(horizontal = 16.dp))
                ShimmerCard(height = 60.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height))
            },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 0. GREETING HEADER ───────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = staggerVisible,
                enter = fadeIn(tween(300)) + slideInVertically(tween(400, delayMillis = 0)) { it / 4 }
            ) {
                GreetingHeader(
                    places = uiState.dailySummary?.uniquePlacesVisited ?: 0,
                    distanceM = uiState.dailySummary?.totalDistanceM ?: 0.0
                )
            }
        }

        // ── 1. LIVE TRACKING BANNER ──────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = staggerVisible,
                enter = fadeIn(tween(300, delayMillis = 60)) + slideInVertically(tween(400, delayMillis = 60)) { it / 4 }
            ) {
                TrackingControlBanner()
            }
        }

        // ── 1b. ACTIVE VISIT / DETECTING LOCATION ──────────────────────
        if (uiState.activeVisit != null) {
            item {
                ActiveVisitCard(
                    placeName = uiState.activeVisit!!.placeName,
                    category = uiState.activeVisit!!.category,
                    arrivalAt = uiState.activeVisit!!.arrivalAt
                )
            }
        } else if (uiState.pendingCandidate != null && uiState.isTracking) {
            item {
                DetectingLocationCard(
                    sampleCount = uiState.pendingCandidate!!.sampleCount,
                    accumulationStartAt = uiState.pendingCandidate!!.accumulationStartAt
                )
            }
        }

        // ── 2. ACTIVITY RING HERO ────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = staggerVisible,
                enter = fadeIn(tween(400, delayMillis = 120)) + slideInVertically(tween(500, delayMillis = 120)) { it / 4 }
            ) {
                DayHeroCard(
                    distanceM = uiState.dailySummary?.totalDistanceM ?: 0.0,
                    steps = uiState.totalStepsToday,
                    firstActivityAt = uiState.dailySummary?.firstActivityAt,
                    lastActivityAt = uiState.dailySummary?.lastActivityAt,
                    sessionStartedAt = uiState.sessionStartedAt,
                    isTracking = uiState.isTracking
                )
            }
        }

        // ── 2a. STREAK PILL ─────────────────────────────────────────────
        if (uiState.streakDays >= 2) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = VoyagerColors.AccentOrange.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "🔥", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${uiState.streakDays}-day streak",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VoyagerColors.AccentOrange
                            )
                        }
                    }
                }
            }
        }

        // ── 2b. TODAY'S MODES BAR ──────────────────────────────────────
        item {
            TodayModesBar(
                summary = uiState.dailySummary,
                isTracking = uiState.isTracking,
                isPaused = uiState.sessionStartedAt != null && !uiState.isTracking
            )
        }

        // ── 3. STEPS CHART ───────────────────────────────────────────────
        if (uiState.stepChart.isNotEmpty()) {
            item {
                StepsChartCard(
                    stepChart = uiState.stepChart,
                    totalStepsToday = uiState.totalStepsToday
                )
            }
        }

        // ── 4. TOP PLACES ────────────────────────────────────────────────
        if (uiState.topPlaces.isNotEmpty()) {
            item {
                SectionHeader(title = "Today's Places")
            }
            items(uiState.topPlaces, key = { it.placeId }) { place ->
                TopPlaceCard(place = place)
            }
        }

        // ── 5. INSIGHTS ─────────────────────────────────────────────────
        if (uiState.insights.isNotEmpty()) {
            item {
                SectionHeader(title = "Insights")
            }
            items(uiState.insights.take(3), key = { it.title }) { insight ->
                InsightCard(insight = insight)
            }
        }

        // ── 6. ANOMALIES ────────────────────────────────────────────────
        if (uiState.anomalies.isNotEmpty()) {
            item {
                SectionHeader(title = "Anomalies")
            }
            items(uiState.anomalies.take(3), key = { it.metricKey + it.impactedDay }) { anomaly ->
                AnomalyAlertCard(
                    metricKey = anomaly.metricKey,
                    humanExplanation = anomaly.humanExplanation,
                    severity = when (anomaly.severity) {
                        AnomalySeverity.SIGNIFICANT -> "HIGH"
                        AnomalySeverity.NOTABLE -> "MEDIUM"
                        AnomalySeverity.MILD -> "LOW"
                    },
                    deviationSigma = anomaly.deviationSigma.toDouble(),
                    impactedDay = anomaly.impactedDay
                )
            }
        }

        // ── 7. QUICK ACTIONS ─────────────────────────────────────────────
        item {
            SectionHeader(title = "Quick Actions")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoyagerOutlinedButton(
                    onClick = onRunPlaceDetection,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Detect", style = MaterialTheme.typography.labelSmall)
                }
                VoyagerOutlinedButton(
                    onClick = onNavigateToInsights,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stats", style = MaterialTheme.typography.labelSmall)
                }
                VoyagerOutlinedButton(
                    onClick = onNavigateToExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ============================================================================
// PRIVATE COMPOSABLE COMPONENTS
// ============================================================================

/** Live-ticking active time card — increments every second while tracking is active */
@Composable
private fun LiveActiveTimeCard(
    firstActivityAt: Long?,
    lastActivityAt: Long?,
    sessionStartedAt: Long?,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    // Determine the effective start time:
    // 1. firstActivityAt from segments (best — actual pipeline data)
    // 2. sessionStartedAt (tracking session exists but no segments closed yet)
    // 3. null → show static "0m"
    val effectiveStart = firstActivityAt ?: if (isTracking) sessionStartedAt else null

    if (!isTracking && effectiveStart == null) {
        DataCard(value = "0m", label = "Active Time", modifier = modifier)
        return
    }

    // Live-ticking clock when tracking is active
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (isTracking) {
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val elapsedMs = when {
        isTracking && effectiveStart != null -> now - effectiveStart
        isTracking -> 0L // Tracking active, waiting for first sample
        else -> {
            val start = effectiveStart ?: 0L
            (lastActivityAt ?: start) - start
        }
    }

    // Animated border glow when tracking is active
    val borderColor = if (isTracking) {
        val infiniteTransition = rememberInfiniteTransition(label = "activeBorder")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderAlpha"
        )
        VoyagerColors.AccentGreen.copy(alpha = animatedAlpha)
    } else {
        VoyagerColors.PrimaryDim.copy(alpha = 0.3f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isTracking) VoyagerColors.AccentGreen.copy(alpha = 0.08f) else VoyagerColors.Surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isTracking) 1.5.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isTracking) {
                    PulsingDot(size = 6.dp, color = VoyagerColors.AccentGreen)
                }
                Text(
                    text = if (isTracking && effectiveStart == null) "0:00"
                           else formatDurationWithSeconds(elapsedMs),
                    style = MonoStatMedium,
                    color = if (isTracking) VoyagerColors.AccentGreen else VoyagerColors.Primary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isTracking) "Tracking" else "Active Time",
                style = MaterialTheme.typography.labelSmall,
                color = if (isTracking) VoyagerColors.AccentGreen.copy(alpha = 0.8f)
                        else VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodayModesBar(
    summary: com.cosmiclaboratory.voyager.domain.model.DailySummary?,
    isTracking: Boolean,
    isPaused: Boolean
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        if (summary == null || (summary.uniquePlacesVisited == 0 && summary.dominantTransportMode == null)) {
            Text(
                text = if (isTracking) "Moving… activity will appear here" else "No movement recorded yet",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Places pill
                if (summary.uniquePlacesVisited > 0) {
                    ModePill(
                        label = "${summary.uniquePlacesVisited} place${if (summary.uniquePlacesVisited != 1) "s" else ""}",
                        color = VoyagerColors.AccentAmber
                    )
                }
                // Dominant mode pill
                summary.dominantTransportMode?.let { mode ->
                    val modeColor = when (mode.uppercase()) {
                        "WALK", "RUN" -> VoyagerColors.TransportWalk
                        "DRIVE", "CAR" -> VoyagerColors.TransportDrive
                        "CYCLE", "BIKE" -> VoyagerColors.TransportCycle
                        "TRANSIT", "BUS", "TRAIN" -> VoyagerColors.TransportTransit
                        else -> VoyagerColors.Primary
                    }
                    ModePill(
                        label = mode.lowercase().replaceFirstChar { it.uppercase() },
                        color = modeColor
                    )
                }
                // Tracking status pill
                val statusLabel = when {
                    isTracking -> "Live"
                    isPaused -> "Paused"
                    else -> null
                }
                statusLabel?.let {
                    ModePill(
                        label = it,
                        color = if (isTracking) VoyagerColors.AccentGreen else VoyagerColors.AccentAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun ModePill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TrackingStatusCard(
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isTracking) VoyagerColors.AccentGreen.copy(alpha = 0.08f) else VoyagerColors.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isTracking) VoyagerColors.AccentGreen.copy(alpha = 0.4f)
            else VoyagerColors.PrimaryDim.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isTracking) {
                    PulsingDot(size = 8.dp, color = VoyagerColors.AccentGreen)
                }
                Text(
                    text = if (isTracking) "Active" else "Off",
                    style = MonoStatMedium,
                    color = if (isTracking) VoyagerColors.AccentGreen else VoyagerColors.OnSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tracking",
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepsChartCard(
    stepChart: List<HourlySteps>,
    totalStepsToday: Int
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Steps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.Primary
            )
            Text(
                text = "$totalStepsToday total",
                style = MonoStatSmall,
                color = VoyagerColors.OnSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (stepChart.isEmpty()) {
            Text(
                text = "No step data yet",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        } else {
            val maxSteps = stepChart.maxOf { it.steps }.coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                stepChart.forEach { hourly ->
                    val fraction = hourly.steps.toFloat() / maxSteps
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                if (hourly.steps > 0) VoyagerColors.Primary
                                else VoyagerColors.SurfaceVariant
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0h",
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = "12h",
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = "23h",
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TopPlaceCard(place: PlaceSummary) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    place.emoji?.let { emoji ->
                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = place.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VoyagerColors.OnSurface
                    )
                }
                if (place.category != "UNKNOWN" && place.category != "Unknown Place") {
                    Spacer(modifier = Modifier.height(4.dp))
                    CategoryChip(categoryName = place.category)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${place.visitCount} visits",
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = formatDuration(place.totalDwellMs),
                    style = MonoTimestamp,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InsightCard(insight: DashboardInsight) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            if (insight.metricValue != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = insight.metricValue,
                    style = MonoStatSmall,
                    color = when (insight.trend) {
                        Trend.UP -> VoyagerColors.AccentGreen
                        Trend.DOWN -> VoyagerColors.AccentRed
                        else -> VoyagerColors.Primary
                    }
                )
            }
        }
    }
}

/** Active visit card — shows place name, category, and live-ticking dwell timer */
@Composable
private fun ActiveVisitCard(
    placeName: String,
    category: com.cosmiclaboratory.voyager.domain.model.PlaceCategory,
    arrivalAt: Long,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val dwellMs = (now - arrivalAt).coerceAtLeast(0)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.AccentBlue.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoyagerColors.AccentBlue.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulsingDot(size = 10.dp, color = VoyagerColors.AccentBlue)
                Text(
                    text = placeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (category.name != "UNKNOWN") {
                    CategoryChip(categoryName = category.displayName)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Since ${formatTimeOfDay(arrivalAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Text(
                    text = formatDurationWithSeconds(dwellMs),
                    style = MonoStatSmall,
                    color = VoyagerColors.AccentBlue
                )
            }
        }
    }
}

/** Detecting location card — shown during PendingVisitCandidate accumulation phase */
@Composable
private fun DetectingLocationCard(
    sampleCount: Int,
    accumulationStartAt: Long,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedMs = (now - accumulationStartAt).coerceAtLeast(0)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingDot(size = 8.dp, color = VoyagerColors.PrimaryDim)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detecting location...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "$sampleCount samples collected",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Text(
                text = formatDurationWithSeconds(elapsedMs),
                style = MonoTimestamp,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

// ============================================================================
// GREETING & HERO
// ============================================================================

@Composable
private fun GreetingHeader(places: Int, distanceM: Double) {
    val hour = java.time.LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val subLine = when {
        places == 0 -> "Nothing tracked yet — start moving"
        places == 1 -> "1 place · ${formatDistance(distanceM)} today"
        else -> "$places places · ${formatDistance(distanceM)} today"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.OnSurface
        )
        Text(
            text = subLine,
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun DayHeroCard(
    distanceM: Double,
    steps: Int,
    firstActivityAt: Long?,
    lastActivityAt: Long?,
    sessionStartedAt: Long?,
    isTracking: Boolean
) {
    val distanceProgress = (distanceM / 5000.0).toFloat().coerceIn(0f, 1f)
    val stepsProgress = (steps / 10000f).coerceIn(0f, 1f)

    // Active time progress — uses live clock when tracking
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (isTracking) {
        LaunchedEffect(Unit) {
            while (true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    val effectiveStart = firstActivityAt ?: if (isTracking) sessionStartedAt else null
    val activeMs = when {
        effectiveStart != null && isTracking -> now - effectiveStart
        effectiveStart != null -> (lastActivityAt ?: effectiveStart) - effectiveStart
        else -> 0L
    }.coerceAtLeast(0L)
    val activeTimeProgress = (activeMs / 1_800_000f).coerceIn(0f, 1f) // goal: 30 min

    // Animated count-up values
    val animatedDistance = remember { Animatable(0f) }
    val animatedSteps = remember { Animatable(0f) }
    LaunchedEffect(distanceM) {
        animatedDistance.snapTo(0f)
        animatedDistance.animateTo(distanceM.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(steps) {
        animatedSteps.snapTo(0f)
        animatedSteps.animateTo(steps.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VoyagerColors.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, VoyagerColors.Primary.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    VoyagerGradients.heroCard
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            ActivityRings(
                distanceProgress = distanceProgress,
                stepsProgress = stepsProgress,
                activeTimeProgress = activeTimeProgress,
                size = 140.dp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeroStat(
                    value = formatDistance(animatedDistance.value.toDouble()),
                    label = "Distance",
                    color = VoyagerColors.Primary
                )
                HeroStat(
                    value = animatedSteps.value.toInt().toString(),
                    label = "Steps",
                    color = VoyagerColors.AccentBlue
                )
                HeroStat(
                    value = formatDuration(activeMs),
                    label = "Active",
                    color = VoyagerColors.AccentGreen
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MonoStatMedium, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

// ============================================================================
// FORMAT HELPERS
// ============================================================================

private fun formatDuration(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Includes seconds for live ticking display */
private fun formatDurationWithSeconds(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

private fun formatTimeOfDay(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val local = java.time.LocalTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    return local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) String.format("%.1f km", meters / 1000) else String.format("%.0f m", meters)
}
