package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.billing.FeatureGate
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall

/**
 * Insights Screen — unified 7-tab analytics hub.
 *
 * Tabs: Overview, Weekly, Patterns, Movement, Social, Carbon, Anomalies
 */
@Composable
fun StatisticsScreen(
    onNavigateToPaywall: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
    entitlementViewModel: EntitlementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val isPro by entitlementViewModel.isPro.collectAsState()
    StatisticsContent(
        uiState = uiState,
        selectedPeriod = selectedPeriod,
        onSelectPeriod = viewModel::selectPeriod,
        isPro = isPro,
        onUnlock = onNavigateToPaywall
    )
}

/**
 * Stateless insights body — takes state instead of collecting it,
 * so it can be rendered in @Preview and exercised in tests.
 */
@Composable
fun StatisticsContent(
    uiState: StatisticsUiState,
    selectedPeriod: DateRangePeriod,
    onSelectPeriod: (DateRangePeriod) -> Unit,
    isPro: Boolean = false,
    onUnlock: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(StatisticsTab.OVERVIEW) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        // Tab Selector
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
            containerColor = VoyagerColors.Surface,
            contentColor = VoyagerColors.Primary,
            edgePadding = 8.dp
        ) {
            StatisticsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) },
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    selectedContentColor = VoyagerColors.Primary,
                    unselectedContentColor = VoyagerColors.OnSurfaceVariant
                )
            }
        }

        // Period Selector
        PeriodSelectorBar(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = onSelectPeriod,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Content based on selected tab. Overview is free; the advanced tabs are Pro.
        when (selectedTab) {
            StatisticsTab.OVERVIEW -> OverviewContent(uiState)
            StatisticsTab.WEEKLY -> ProInsight(isPro, onUnlock) {
                WeeklyComparisonContent(uiState.weeklyComparison, uiState.periodLabel)
            }
            StatisticsTab.PATTERNS -> ProInsight(isPro, onUnlock) {
                PlacePatternSummaryContent(uiState.placePatterns, uiState.periodLabel)
            }
            StatisticsTab.MOVEMENT -> ProInsight(isPro, onUnlock) {
                MovementAnalyticsContent(uiState.movementStats, uiState.periodLabel)
            }
            StatisticsTab.SOCIAL -> ProInsight(isPro, onUnlock) {
                SocialHealthContent(uiState.socialStats, uiState.periodLabel)
            }
            StatisticsTab.CARBON -> ProInsight(isPro, onUnlock) {
                CarbonFootprintContent(uiState.carbonFootprint, uiState.periodLabel)
            }
            StatisticsTab.ANOMALIES -> ProInsight(isPro, onUnlock) {
                AnomaliesContent(uiState.anomalies, uiState.periodLabel)
            }
        }
    }
}

/**
 * Gates an advanced Insights tab. Pro users see the tab content unchanged; free
 * users get the locked card with a path to the paywall.
 */
@Composable
private fun ProInsight(
    isPro: Boolean,
    onUnlock: () -> Unit,
    content: @Composable () -> Unit
) {
    FeatureGate(
        isPro = isPro,
        featureName = "Advanced insights",
        description = "Weekly comparisons, place patterns, movement analytics and " +
            "anomaly detection across your timeline.",
        modifier = Modifier.padding(16.dp),
        onUnlock = onUnlock,
        content = content
    )
}

enum class StatisticsTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("Overview", Icons.Default.Lightbulb),
    WEEKLY("Weekly", Icons.Default.DateRange),
    PATTERNS("Patterns", Icons.Default.Place),
    MOVEMENT("Movement", Icons.AutoMirrored.Filled.TrendingUp),
    SOCIAL("Social", Icons.Default.Person),
    CARBON("Carbon", Icons.Default.Eco),
    ANOMALIES("Anomalies", Icons.Default.Warning)
}

// ============================================================================
// OVERVIEW TAB — AI insights + personalized messages
// ============================================================================

@Composable
private fun OverviewContent(uiState: StatisticsUiState) {
    val hasData = uiState.weeklyComparison != null || uiState.movementStats != null ||
            (uiState.placePatterns?.isNotEmpty() == true)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Analytics Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = uiState.periodLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!hasData) {
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = VoyagerColors.AccentPurple,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Keep tracking to generate personalized insights",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VoyagerColors.OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Summary stats row
            uiState.movementStats?.let { movement ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataCard(
                            value = "%.1f km".format(movement.totalDistanceKm),
                            label = "Distance",
                            modifier = Modifier.weight(1f),
                            valueColor = VoyagerColors.AccentBlue
                        )
                        DataCard(
                            value = uiState.socialStats?.uniquePlaces?.toString() ?: "0",
                            label = "Places",
                            modifier = Modifier.weight(1f)
                        )
                        DataCard(
                            value = "${uiState.anomalies.size}",
                            label = "Anomalies",
                            modifier = Modifier.weight(1f),
                            valueColor = if (uiState.anomalies.isNotEmpty()) VoyagerColors.AccentRed else VoyagerColors.AccentGreen
                        )
                    }
                }
            }

            // Top patterns summary
            uiState.placePatterns?.take(3)?.let { patterns ->
                if (patterns.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Top Places")
                    }
                    patterns.forEach { pattern ->
                        item {
                            VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pattern.placeName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = VoyagerColors.OnSurface
                                        )
                                        CategoryChip(categoryName = pattern.category)
                                    }
                                    Text(
                                        text = "${pattern.visitCount} visits",
                                        style = MonoStatSmall,
                                        color = VoyagerColors.Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Weekly comparison highlight
            uiState.weeklyComparison?.let { weekly ->
                item {
                    SectionHeader(title = "Week-over-Week")
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataCard(
                            value = "${weekly.placesThisWeek}",
                            label = "Places",
                            trend = trendDirection(weekly.placesChange),
                            trendPercent = formatChangePercent(weekly.placesChange),
                            modifier = Modifier.weight(1f)
                        )
                        DataCard(
                            value = "%.1f km".format(weekly.distanceThisWeek / 1000),
                            label = "Distance",
                            trend = trendDirection(weekly.distanceChange),
                            trendPercent = formatChangePercent(weekly.distanceChange),
                            modifier = Modifier.weight(1f),
                            valueColor = VoyagerColors.AccentBlue
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// WEEKLY TAB — ComparisonResult with MetricDeltas and trend arrows
// ============================================================================

@Composable
private fun WeeklyComparisonContent(weeklyComparison: WeeklyComparisonData?, periodLabel: String) {
    if (weeklyComparison == null) {
        EmptyStateMessage(
            icon = Icons.Default.DateRange,
            title = "No Weekly Data",
            message = "Keep tracking to see week-over-week trends."
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "$periodLabel vs Previous Period")
        }

        // Stat cards row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DataCard(
                    value = "${weeklyComparison.placesThisWeek}",
                    label = "Places",
                    trend = trendDirection(weeklyComparison.placesChange),
                    trendPercent = formatChangePercent(weeklyComparison.placesChange),
                    modifier = Modifier.weight(1f)
                )
                DataCard(
                    value = "%.1f km".format(weeklyComparison.distanceThisWeek / 1000),
                    label = "Distance",
                    trend = trendDirection(weeklyComparison.distanceChange),
                    trendPercent = formatChangePercent(weeklyComparison.distanceChange),
                    modifier = Modifier.weight(1f),
                    valueColor = VoyagerColors.AccentBlue
                )
                DataCard(
                    value = "${weeklyComparison.timeAwayThisWeek}h",
                    label = "Time Away",
                    trend = trendDirection(weeklyComparison.timeAwayChange),
                    trendPercent = formatChangePercent(weeklyComparison.timeAwayChange),
                    modifier = Modifier.weight(1f),
                    valueColor = VoyagerColors.AccentGreen
                )
            }
        }

        // Detail cards
        item {
            ComparisonDetailCard(
                title = "Places Visited",
                thisWeek = weeklyComparison.placesThisWeek.toString(),
                lastWeek = weeklyComparison.placesLastWeek.toString(),
                change = weeklyComparison.placesChange
            )
        }
        item {
            ComparisonDetailCard(
                title = "Distance Traveled",
                thisWeek = "%.1f km".format(weeklyComparison.distanceThisWeek / 1000),
                lastWeek = "%.1f km".format(weeklyComparison.distanceLastWeek / 1000),
                change = weeklyComparison.distanceChange
            )
        }
        item {
            ComparisonDetailCard(
                title = "Time Away from Home",
                thisWeek = "${weeklyComparison.timeAwayThisWeek}h",
                lastWeek = "${weeklyComparison.timeAwayLastWeek}h",
                change = weeklyComparison.timeAwayChange
            )
        }
    }
}

@Composable
private fun ComparisonDetailCard(
    title: String,
    thisWeek: String,
    lastWeek: String,
    change: Double
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("This period", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
                Text(thisWeek, style = MonoStatSmall, color = VoyagerColors.Primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Previous", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
                Text(lastWeek, style = MonoStatSmall, color = VoyagerColors.OnSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ComparisonIndicator(change = change)
    }
}

// ============================================================================
// PATTERNS TAB — PlacePattern list with CategoryChips
// ============================================================================

@Composable
private fun PlacePatternSummaryContent(placePatterns: List<PlacePatternSummary>?, periodLabel: String) {
    if (placePatterns == null || placePatterns.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.Place,
            title = "No Patterns Yet",
            message = "Visit more places to see patterns."
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "Place Patterns — $periodLabel")
        }

        items(placePatterns) { pattern ->
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pattern.placeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VoyagerColors.OnSurface
                    )
                    CategoryChip(categoryName = pattern.category)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${pattern.visitCount} visits",
                        style = MonoStatSmall,
                        color = VoyagerColors.Primary
                    )
                    Text(
                        text = "avg ${pattern.avgDurationMinutes} min",
                        style = MonoStatSmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
                if (pattern.typicalDays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Typical: ${pattern.typicalDays.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// MOVEMENT TAB — Transport mode breakdown with DataCards
// ============================================================================

@Composable
private fun MovementAnalyticsContent(movementStats: MovementStats?, periodLabel: String) {
    if (movementStats == null) {
        EmptyStateMessage(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            title = "No Movement Data",
            message = "Movement statistics will appear after tracking."
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "Movement — $periodLabel")
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DataCard(
                    value = "%.1f km".format(movementStats.totalDistanceKm),
                    label = "Total Distance",
                    modifier = Modifier.weight(1f),
                    valueColor = VoyagerColors.AccentBlue
                )
                DataCard(
                    value = "%.1f km/h".format(movementStats.avgSpeedKmh),
                    label = "Avg Speed",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Most Active Day",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movementStats.mostActiveDay,
                    style = MonoStatMedium,
                    color = VoyagerColors.Primary
                )
            }
        }
    }
}

// ============================================================================
// CARBON TAB — per-transport-mode CO2 estimate
// ============================================================================

@Composable
private fun CarbonFootprintContent(
    footprint: com.cosmiclaboratory.voyager.domain.model.CarbonFootprint?,
    periodLabel: String
) {
    if (footprint == null || footprint.isEmpty) {
        EmptyStateMessage(
            icon = Icons.Default.Eco,
            title = "No Travel Yet",
            message = "Your carbon footprint appears once you've recorded some trips."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeader(title = "Carbon — $periodLabel") }

        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Estimated CO₂e",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.1f kg".format(footprint.totalKgCo2),
                    style = MonoStatLarge,
                    color = VoyagerColors.Primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "≈ %.0f tree-years to absorb · %.0f km travelled".format(
                        footprint.treeYearEquivalent, footprint.totalDistanceKm
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }

        item { SectionHeader(title = "By transport mode") }

        items(footprint.modes) { mode ->
            CarbonModeRow(mode, footprint.totalKgCo2)
        }

        item {
            Text(
                text = "Estimates use average emission factors per kilometre — " +
                    "a guide, not an audit.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun CarbonModeRow(
    mode: com.cosmiclaboratory.voyager.domain.model.ModeFootprint,
    totalKg: Double
) {
    val zeroEmission = mode.kgCo2 == 0.0
    val accent = if (zeroEmission) VoyagerColors.Success else VoyagerColors.Warning
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = carbonModeLabel(mode.mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (zeroEmission) "0 kg" else "%.1f kg".format(mode.kgCo2),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { mode.shareOf(totalKg).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = VoyagerColors.SurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "%.1f km · %.0f g/km".format(mode.distanceKm, mode.gramsPerKm),
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

private fun carbonModeLabel(
    mode: com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
): String = when (mode) {
    com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.WALK -> "Walking"
    com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.RUN -> "Running"
    com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.CYCLE -> "Cycling"
    com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.TRANSIT -> "Transit"
    com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.DRIVE -> "Driving"
    com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.FLIGHT -> "Flights"
    else -> mode.name
}

// ============================================================================
// SOCIAL TAB — uniquePlaces, varietyScore, categoryBreakdown
// ============================================================================

@Composable
private fun SocialHealthContent(socialStats: SocialHealthStats?, periodLabel: String) {
    if (socialStats == null) {
        EmptyStateMessage(
            icon = Icons.Default.Person,
            title = "No Social Data",
            message = "Social health statistics will appear after tracking."
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "Social Health — $periodLabel")
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DataCard(
                    value = "${socialStats.uniquePlaces}",
                    label = "Unique Places",
                    modifier = Modifier.weight(1f)
                )
                DataCard(
                    value = "${socialStats.varietyScore}/100",
                    label = "Variety Score",
                    modifier = Modifier.weight(1f),
                    valueColor = VoyagerColors.AccentPurple
                )
            }
        }

        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Variety Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfidenceBar(
                    confidence = socialStats.varietyScore / 100f,
                    source = "Place variety index"
                )
            }
        }

        if (socialStats.categoryBreakdown.isNotEmpty()) {
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Category Distribution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val total = socialStats.categoryBreakdown.values.sum().coerceAtLeast(1)
                    val segments = socialStats.categoryBreakdown.map { (category, count) ->
                        ProgressSegment(
                            fraction = count.toFloat() / total,
                            color = VoyagerColors.Primary, // Could map per category
                            label = category
                        )
                    }
                    SegmentedProgressBar(segments = segments)

                    Spacer(modifier = Modifier.height(8.dp))
                    socialStats.categoryBreakdown.forEach { (category, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryChip(categoryName = category)
                            Text(
                                text = "$count visits",
                                style = MonoStatSmall,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// ANOMALIES TAB
// ============================================================================

@Composable
private fun AnomaliesContent(anomalies: List<com.cosmiclaboratory.voyager.domain.model.Anomaly>, periodLabel: String) {
    if (anomalies.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Default.CheckCircle,
            title = "No Anomalies",
            message = "No anomalies detected for $periodLabel. This is a good thing!"
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "Anomalies — $periodLabel")
        }
        items(anomalies.size) { index ->
            val anomaly = anomalies[index]
            AnomalyAlertCard(
                metricKey = anomaly.metricKey,
                humanExplanation = anomaly.humanExplanation,
                severity = when (anomaly.severity) {
                    com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity.SIGNIFICANT -> "HIGH"
                    com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity.NOTABLE -> "MEDIUM"
                    com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity.MILD -> "LOW"
                },
                deviationSigma = anomaly.deviationSigma.toDouble(),
                impactedDay = anomaly.impactedDay
            )
        }
    }
}

// ============================================================================
// SHARED COMPONENTS
// ============================================================================

@Composable
private fun ComparisonIndicator(change: Double) {
    val trend = trendDirection(change)
    val color = when (trend) {
        TrendDirection.UP -> VoyagerColors.AccentGreen
        TrendDirection.DOWN -> VoyagerColors.AccentRed
        TrendDirection.STABLE -> VoyagerColors.OnSurfaceVariant
    }
    val icon = when (trend) {
        TrendDirection.UP -> Icons.Default.KeyboardArrowUp
        TrendDirection.DOWN -> Icons.Default.KeyboardArrowDown
        TrendDirection.STABLE -> Icons.Default.Remove
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (change == 0.0) "No change" else formatChangePercent(change),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyStateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VoyagerColors.Primary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================================
// HELPERS
// ============================================================================

private fun trendDirection(change: Double): TrendDirection = when {
    change > 0 -> TrendDirection.UP
    change < 0 -> TrendDirection.DOWN
    else -> TrendDirection.STABLE
}

private fun formatChangePercent(change: Double): String {
    return "${if (change > 0) "+" else ""}%.1f%%".format(change)
}
