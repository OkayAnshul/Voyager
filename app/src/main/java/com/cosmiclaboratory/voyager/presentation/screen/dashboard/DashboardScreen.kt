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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.screen.reliability.ForceStopBanner
import com.cosmiclaboratory.voyager.presentation.screen.reliability.shouldShowForceStopBanner
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
    onNavigateToSearch: () -> Unit = {},
    onNavigateToDayStory: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {},
    onRunPlaceDetection: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    DashboardContent(
        uiState = uiState,
        onNavigateToInsights = onNavigateToInsights,
        onNavigateToExport = onNavigateToExport,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToDayStory = onNavigateToDayStory,
        onNavigateToTrips = onNavigateToTrips,
        onRunPlaceDetection = onRunPlaceDetection
    )
}

/**
 * Stateless dashboard body — takes [DashboardUiState] instead of collecting it,
 * so it can be rendered in @Preview and exercised in tests.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToDayStory: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {},
    onRunPlaceDetection: () -> Unit = {}
) {
    var staggerVisible by remember { mutableStateOf(false) }
    var forceStopBannerDismissed by rememberSaveable { mutableStateOf(false) }
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
        // ── SEARCH BAR ───────────────────────────────────────────────────
        item {
            VoyagerCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSearch,
                variant = CardVariant.GLASS
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Search places, days, trips…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }

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

        // ── 0b. FORCE-STOP RECOVERY BANNER ───────────────────────────────
        if (!forceStopBannerDismissed &&
            shouldShowForceStopBanner(uiState.lastSampleAt, System.currentTimeMillis())
        ) {
            item {
                ForceStopBanner(onDismiss = { forceStopBannerDismissed = true })
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
        val activeVisit = uiState.activeVisit
        val pendingCandidate = uiState.pendingCandidate
        if (activeVisit != null) {
            item {
                ActiveVisitCard(
                    placeName = activeVisit.placeName,
                    category = activeVisit.category,
                    arrivalAt = activeVisit.arrivalAt
                )
            }
        } else if (pendingCandidate != null && uiState.isTracking) {
            item {
                DetectingLocationCard(
                    sampleCount = pendingCandidate.sampleCount,
                    accumulationStartAt = pendingCandidate.accumulationStartAt
                )
            }
        }

        // ── 2. HERO — "Capturing Now" stat card + activity rings, or the
        // "Awaiting Signal" welcome when today has no data yet. The first hour
        // never shows a dead, all-zero screen — the welcome carries the privacy
        // promise until real data arrives.
        item {
            val hasDataToday = uiState.topPlaces.isNotEmpty() ||
                (uiState.dailySummary?.totalDistanceM ?: 0.0) > 50.0 ||
                uiState.totalStepsToday > 30 ||
                uiState.activeVisit != null ||
                uiState.dailySummary?.firstActivityAt != null
            AnimatedVisibility(
                visible = staggerVisible,
                enter = fadeIn(tween(400, delayMillis = 120)) + slideInVertically(tween(500, delayMillis = 120)) { it / 4 }
            ) {
                if (!hasDataToday) {
                    AwaitingSignalCard(isTracking = uiState.isTracking, isPaused = uiState.isPaused)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.lg)) {
                        CapturingNowCard(
                            distanceM = uiState.dailySummary?.totalDistanceM ?: 0.0,
                            steps = uiState.totalStepsToday,
                            isTracking = uiState.isTracking
                        )
                        RingsBlock(
                            distanceM = uiState.dailySummary?.totalDistanceM ?: 0.0,
                            steps = uiState.totalStepsToday,
                            firstActivityAt = uiState.dailySummary?.firstActivityAt,
                            lastActivityAt = uiState.dailySummary?.lastActivityAt,
                            sessionStartedAt = uiState.sessionStartedAt,
                            isTracking = uiState.isTracking
                        )
                    }
                }
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

        // ── 4-6. JOB-AWARE SECTIONS (places / insights / anomalies) ──────
        // The chosen Job decides which of these the user sees first.
        val placesSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            if (uiState.topPlaces.isNotEmpty()) {
                item { SectionHeader(title = "Today's Places", accent = VoyagerColors.Primary) }
                items(uiState.topPlaces, key = { it.placeId }) { place ->
                    TopPlaceCard(place = place)
                }
            }
        }
        val insightsSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            if (uiState.insights.isNotEmpty()) {
                item { SectionHeader(title = "Insights", accent = VoyagerColors.AccentPurple) }
                items(uiState.insights.take(3), key = { it.title }) { insight ->
                    InsightCard(insight = insight)
                }
            }
        }
        val anomaliesSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            if (uiState.anomalies.isNotEmpty()) {
                item { SectionHeader(title = "Anomalies", accent = VoyagerColors.AccentAmber) }
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
        }
        val orderedSections = when (uiState.activeJob) {
            Job.MEMORY -> listOf(placesSection, insightsSection, anomaliesSection)
            Job.HABITS -> listOf(insightsSection, placesSection, anomaliesSection)
            Job.PROOF -> listOf(placesSection, anomaliesSection, insightsSection)
        }
        orderedSections.forEach { section -> section() }

        // ── 6b. BATTERY SELF-REPORT ──────────────────────────────────────
        // The figure is withheld until BatteryUsageReporter is confident; while it
        // gathers signal we show a calm "measuring" card rather than a vanished one.
        val batteryPerDay = uiState.batteryPercentPerDay
        if (batteryPerDay != null) {
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = VoyagerColors.AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery while tracking",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = VoyagerColors.OnSurface
                            )
                            Text(
                                text = "Your phone's overall drain during tracking hours — not Voyager alone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                        Text(
                            text = "~$batteryPerDay%/day",
                            style = MonoStatMedium,
                            color = VoyagerColors.OnSurface
                        )
                    }
                }
            }
        } else if (uiState.isTracking) {
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = VoyagerColors.OnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery while tracking",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = VoyagerColors.OnSurface
                            )
                            Text(
                                text = "Measuring your honest drain — appears after a full day of tracking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── 7. SHORTCUTS ─────────────────────────────────────────────────
        item {
            SectionHeader(title = "Shortcuts", accent = VoyagerColors.AccentGreen)
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)
            ) {
                ShortcutChip(icon = Icons.Default.Luggage, label = "Trips", onClick = onNavigateToTrips)
                ShortcutChip(icon = Icons.Default.Share, label = "Export", onClick = onNavigateToExport)
                ShortcutChip(icon = Icons.Default.PhotoLibrary, label = "Photos", onClick = onNavigateToDayStory)
                ShortcutChip(icon = Icons.Default.Insights, label = "Stats", onClick = onNavigateToInsights)
                ShortcutChip(icon = Icons.Default.MyLocation, label = "Detect", onClick = onRunPlaceDetection)
            }
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ============================================================================
// PRIVATE COMPOSABLE COMPONENTS
// ============================================================================

/**
 * The signature "Capturing Now" stat card: a frosted-glass surface carrying
 * today's two headline numbers — distance + steps — in JetBrains Mono, with a
 * live pulse when tracking is active. The numbers roll up on change.
 */
@Composable
private fun CapturingNowCard(
    distanceM: Double,
    steps: Int,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
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

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = if (isTracking) VoyagerGradients.activeCard else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTracking) "CAPTURING NOW" else "TODAY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = if (isTracking) VoyagerColors.AccentGreen else VoyagerColors.OnSurfaceVariant
            )
            if (isTracking) PulsingDot(size = 8.dp, color = VoyagerColors.AccentBlue)
        }
        Spacer(Modifier.height(VoyagerSpacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xl)
        ) {
            CapturingStat(
                modifier = Modifier.weight(1f),
                value = String.format("%.1f", animatedDistance.value / 1000.0),
                label = "KM DISTANCE",
                color = VoyagerColors.OnSurface
            )
            CapturingStat(
                modifier = Modifier.weight(1f),
                value = "%,d".format(animatedSteps.value.toInt()),
                label = "STEPS TODAY",
                color = VoyagerColors.AccentGreen
            )
        }
    }
}

@Composable
private fun CapturingStat(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MonoStatLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

/**
 * Standalone activity-rings block: three concentric rings (Distance · Steps ·
 * Active time) with a walking glyph at the centre and a colour legend beneath.
 * The active-time ring ticks live while tracking.
 */
@Composable
private fun RingsBlock(
    distanceM: Double,
    steps: Int,
    firstActivityAt: Long?,
    lastActivityAt: Long?,
    sessionStartedAt: Long?,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
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

    val distanceProgress = (distanceM / 5000.0).toFloat().coerceIn(0f, 1f)
    val stepsProgress = (steps / 10000f).coerceIn(0f, 1f)
    val activeProgress = (activeMs / 1_800_000f).coerceIn(0f, 1f) // goal: 30 min

    val rings = listOf(
        ActivityRing(progress = distanceProgress, color = VoyagerColors.AccentPurple, label = "Distance", valueText = formatDistance(distanceM)),
        ActivityRing(progress = stepsProgress, color = VoyagerColors.AccentGreen, label = "Steps", valueText = "%,d".format(steps)),
        ActivityRing(progress = activeProgress, color = VoyagerColors.AccentBlue, label = "Active", valueText = formatDuration(activeMs))
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.lg)
    ) {
        VoyagerActivityRings(
            rings = rings,
            ringSize = 184.dp,
            strokeWidth = 14.dp,
            gap = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(VoyagerColors.SurfaceBright, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = VoyagerColors.OnSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xl)) {
            LegendDot("Dist", VoyagerColors.AccentPurple)
            LegendDot("Steps", VoyagerColors.AccentGreen)
            LegendDot("Time", VoyagerColors.AccentBlue)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

/**
 * "Awaiting Signal" welcome — shown before today has any data. Pin glyph in a
 * primary glow, reassurance copy, two zeroed stat cards, and the privacy promise
 * (on-device, low-power) so the first hour never feels empty.
 */
@Composable
private fun AwaitingSignalCard(
    isTracking: Boolean,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val heroTitle = when {
        isTracking -> "Awaiting signal"
        isPaused -> "Tracking paused"
        else -> "Ready when you are"
    }
    val heroBody = when {
        isTracking -> "Move around for a few minutes —\nyour first places will appear here."
        isPaused -> "Resume above to keep\nrecording your day."
        else -> "Start tracking above to begin\nrecording your day."
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = VoyagerSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.lg)
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .drawBehind { drawRect(VoyagerGradients.primaryGlow(size.width, size.height)) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(VoyagerColors.SurfaceBright, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            text = heroTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.OnSurface
        )
        Text(
            text = heroBody,
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)
        ) {
            WelcomeStatCard(modifier = Modifier.weight(1f), label = "DISTANCE", value = "0.0", unit = "km")
            WelcomeStatCard(modifier = Modifier.weight(1f), label = "LOCATIONS", value = "0", unit = "stops")
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = VoyagerColors.AccentGreen,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Private by Design",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VoyagerColors.OnSurface
                    )
                    Text(
                        text = "Everything stays on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(VoyagerSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)) {
                EngineChip(label = "Kinetic Engine", dot = true)
                EngineChip(label = "Ultra-low power", icon = Icons.Default.Bolt)
            }
        }
    }
}

@Composable
private fun WelcomeStatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    VoyagerCard(modifier = modifier, variant = CardVariant.GLASS, padding = VoyagerSpacing.lg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = VoyagerColors.OnSurfaceVariant
        )
        Spacer(Modifier.height(VoyagerSpacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MonoStatLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
                color = VoyagerColors.OnSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun EngineChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dot: Boolean = false,
    color: Color = VoyagerColors.AccentGreen
) {
    Surface(
        shape = VoyagerShapes.pill,
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VoyagerSpacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
        ) {
            if (dot) Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

/** Pill shortcut to a Proof/utility screen. */
@Composable
private fun ShortcutChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = VoyagerShapes.pill,
        color = VoyagerSurfaces.glassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, VoyagerColors.PrimaryDim.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VoyagerSpacing.lg, vertical = VoyagerSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)
        ) {
            Icon(icon, contentDescription = null, tint = VoyagerColors.Primary, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = VoyagerColors.OnSurface
            )
        }
    }
}

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
    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.GLASS,
        padding = VoyagerSpacing.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)
        ) {
            // Leading category tile
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(VoyagerColors.Primary.copy(alpha = 0.15f), VoyagerShapes.badge),
                contentAlignment = Alignment.Center
            ) {
                val emoji = place.emoji
                if (emoji != null) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    maxLines = 1
                )
                val category = place.category.takeIf { it != "UNKNOWN" && it != "Unknown Place" }
                Text(
                    text = buildString {
                        if (category != null) append("$category · ")
                        append("${place.visitCount} visit${if (place.visitCount != 1) "s" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = formatDuration(place.totalDwellMs),
                style = MonoStatSmall,
                color = VoyagerColors.OnSurface
            )
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
