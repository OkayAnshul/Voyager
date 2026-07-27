package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.Heatmap
import com.cosmiclaboratory.voyager.domain.model.OnThisDayMemory
import com.cosmiclaboratory.voyager.domain.model.YearInReview
import com.cosmiclaboratory.voyager.domain.usecase.CommuteLeg
import com.cosmiclaboratory.voyager.domain.usecase.CommuteStats
import com.cosmiclaboratory.voyager.domain.usecase.DayRhythm
import com.cosmiclaboratory.voyager.domain.usecase.DayState
import com.cosmiclaboratory.voyager.domain.usecase.HourSlot
import com.cosmiclaboratory.voyager.domain.usecase.RoutineBreak
import com.cosmiclaboratory.voyager.domain.usecase.SleepRhythm
import com.cosmiclaboratory.voyager.domain.usecase.TimeBudget
import com.cosmiclaboratory.voyager.domain.usecase.UpcomingVisit
import com.cosmiclaboratory.voyager.domain.model.formatDays
import com.cosmiclaboratory.voyager.domain.model.formatTime
import com.cosmiclaboratory.voyager.domain.model.toConfidenceStrength
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val tabs = StatisticsTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Free tier: Overview + Weekly + Highlights. The other six lenses are Pro.
    val freeTabs = setOf(StatisticsTab.OVERVIEW, StatisticsTab.WEEKLY, StatisticsTab.HIGHLIGHTS)
    val gatedIndices = if (isPro) emptySet()
        else tabs.filter { it !in freeTabs }.map { it.ordinal }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background) // flat OLED — no gradient backdrop
    ) {
        // Chapter tabs — a tap shortcut kept in sync with the swipe pager.
        VoyagerFilterChipRow(
            items = tabs.map { it.title },
            selectedIndex = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            proGatedIndices = gatedIndices,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Period selector + flip-book position indicator.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PeriodSelectorBar(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onSelectPeriod,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ChapterPosition(index = pagerState.currentPage, count = tabs.size)
        }

        // One chapter per swipe. Free lenses render directly; Pro lenses gate.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it }
        ) { page ->
            when (tabs[page]) {
                StatisticsTab.OVERVIEW -> OverviewContent(uiState)
                StatisticsTab.WEEKLY -> WeeklyComparisonContent(uiState.weeklyComparison, uiState.weeklyNarrative, uiState.periodLabel)
                StatisticsTab.HIGHLIGHTS -> HighlightsContent(uiState.notableHighlights, uiState.memories, uiState.highlightsNarrative)
                StatisticsTab.PATTERNS -> ProInsight(isPro, onUnlock) {
                    RoutinesContent(uiState.routines, uiState.upcoming, uiState.placePatterns, uiState.periodLabel)
                }
                StatisticsTab.RHYTHM -> ProInsight(isPro, onUnlock) {
                    RhythmContent(uiState.sleepRhythm, uiState.dayRhythm, uiState.commute)
                }
                StatisticsTab.MOVEMENT -> ProInsight(isPro, onUnlock) {
                    MovementAnalyticsContent(
                        uiState.movementStats,
                        uiState.trackingStreak,
                        uiState.explorationScore,
                        uiState.heatmap,
                        uiState.yearInReview,
                        uiState.periodLabel
                    )
                }
                StatisticsTab.BALANCE -> ProInsight(isPro, onUnlock) {
                    BalanceContent(uiState.timeBudget, uiState.socialStats, uiState.periodLabel)
                }
                StatisticsTab.CARBON -> ProInsight(isPro, onUnlock) {
                    CarbonFootprintContent(uiState.carbonFootprint, uiState.periodLabel)
                }
                StatisticsTab.ANOMALIES -> ProInsight(isPro, onUnlock) {
                    AnomaliesContent(uiState.anomalies, uiState.routineBreaks, uiState.periodLabel)
                }
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
    if (isPro) content() else ChapterLockCard(onUnlock = onUnlock)
}

enum class StatisticsTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    // Free "relive it" trio first, kept contiguous so swiping never hits a
    // paywall between them; the six Pro lenses follow.
    OVERVIEW("Overview", Icons.Default.Lightbulb),
    WEEKLY("Weekly", Icons.Default.DateRange),
    HIGHLIGHTS("Highlights", Icons.Default.Star),
    PATTERNS("Routines", Icons.Default.Repeat),
    RHYTHM("Rhythm", Icons.Default.Bedtime),
    MOVEMENT("Movement", Icons.AutoMirrored.Filled.TrendingUp),
    BALANCE("Balance", Icons.Default.PieChart),
    CARBON("Carbon", Icons.Default.Eco),
    ANOMALIES("Anomalies", Icons.Default.Warning)
}

// ============================================================================
// OVERVIEW TAB — AI insights + personalized messages
// ============================================================================

@Composable
private fun OverviewContent(uiState: StatisticsUiState) {
    val accent = chapterAccent(StatisticsTab.OVERVIEW)
    val weekly = uiState.weeklyComparison
    val hasData = weekly != null || uiState.movementStats != null ||
            (uiState.placePatterns?.isNotEmpty() == true)

    if (uiState.isLoading && !hasData) {
        StoryPageSkeleton()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Chapter lead: the sentence ───────────────────────────────────
        item {
            uiState.overviewNarrative?.let { StoryHero(it, accent) }
        }

        if (hasData) {
            // ── Data ledger — dense, typeset; replaces the 2×2 tiles ──
            item {
                val rows = buildList {
                    if (weekly != null) {
                        add(LedgerRow("Distance", "%.1f km".format(weekly.distanceThisWeek / 1000), deltaLabel(weekly.distanceChange), weekly.distanceChange >= 0))
                        add(LedgerRow("Places", "${weekly.placesThisWeek}", deltaLabel(weekly.placesChange), weekly.placesChange >= 0))
                        add(LedgerRow("Time away", "${weekly.timeAwayThisWeek} h", deltaLabel(weekly.timeAwayChange), weekly.timeAwayChange >= 0))
                    } else {
                        uiState.movementStats?.let {
                            add(LedgerRow("Distance", "%.1f km".format(it.totalDistanceKm)))
                        }
                    }
                    add(LedgerRow("Anomalies", "${uiState.anomalies.size}"))
                }
                DataLedger(rows)
            }

            // ── Featured anomaly — the "stood out" moment ──
            uiState.anomalies.firstOrNull()?.let { anomaly ->
                item { SectionHeader(title = "Notable") }
                item {
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

            // ── Top places ──
            uiState.placePatterns?.take(3)?.takeIf { it.isNotEmpty() }?.let { patterns ->
                item { SectionHeader(title = "Top Places") }
                items(patterns) { pattern ->
                    VoyagerCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = CardVariant.GLASS,
                        padding = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pattern.placeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VoyagerColors.OnSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${pattern.visitCount} visits · avg ${pattern.avgDurationMinutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VoyagerColors.OnSurfaceVariant
                                )
                            }
                            CategoryChip(categoryName = pattern.category)
                        }
                    }
                }
            }
        }
    }
}

// SynthesisHero was replaced by the storybook StoryHero + InsightNarrators
// (see StorybookComponents.kt / InsightNarrators.kt).

// ============================================================================
// WEEKLY TAB — ComparisonResult with MetricDeltas and trend arrows
// ============================================================================

@Composable
private fun WeeklyComparisonContent(
    weeklyComparison: WeeklyComparisonData?,
    narrative: InsightNarrative?,
    periodLabel: String
) {
    val accent = chapterAccent(StatisticsTab.WEEKLY)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { narrative?.let { StoryHero(it, accent) } }

        if (weeklyComparison != null) {
            item {
                DataLedger(
                    listOf(
                        LedgerRow(
                            "Places", "${weeklyComparison.placesThisWeek}",
                            deltaLabel(weeklyComparison.placesChange), weeklyComparison.placesChange >= 0,
                            previous = "${weeklyComparison.placesLastWeek}"
                        ),
                        LedgerRow(
                            "Distance", "%.1f km".format(weeklyComparison.distanceThisWeek / 1000),
                            deltaLabel(weeklyComparison.distanceChange), weeklyComparison.distanceChange >= 0,
                            previous = "%.1f km".format(weeklyComparison.distanceLastWeek / 1000)
                        ),
                        LedgerRow(
                            "Time away", "${weeklyComparison.timeAwayThisWeek} h",
                            deltaLabel(weeklyComparison.timeAwayChange), weeklyComparison.timeAwayChange >= 0,
                            previous = "${weeklyComparison.timeAwayLastWeek} h"
                        )
                    )
                )
            }
        }
    }
}

// ============================================================================
// ROUTINES TAB — recurring patterns ("Gym every Tue ~7pm") + top places
// ============================================================================

@Composable
private fun RoutinesContent(
    routines: List<RoutineSummary>?,
    upcoming: List<UpcomingVisit>,
    placePatterns: List<PlacePatternSummary>?,
    periodLabel: String
) {
    val accent = chapterAccent(StatisticsTab.PATTERNS)
    val hasRoutines = !routines.isNullOrEmpty()
    val hasPlaces = !placePatterns.isNullOrEmpty()
    val hasUpcoming = upcoming.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StoryHero(InsightNarrators.routines(hasRoutines, hasUpcoming), accent) }
        if (hasUpcoming) {
            item { SectionHeader(title = "Coming Up Today") }
            items(upcoming) { visit -> UpcomingCard(visit) }
        }
        if (hasRoutines) {
            item { SectionHeader(title = "Your Routines") }
            items(routines!!) { routine -> RoutineCard(routine) }
        }
        if (hasPlaces) {
            item { SectionHeader(title = "Top Places — $periodLabel") }
            items(placePatterns!!) { pattern -> PlacePatternCard(pattern) }
        }
    }
}

@Composable
private fun UpcomingCard(visit: UpcomingVisit) {
    val name = visit.placeName ?: "somewhere you usually go"
    val time = minuteToClock(visit.expectedHour.coerceIn(0, 23) * 60)
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = VoyagerColors.AccentBlue,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "You usually visit $name around $time",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One recurring routine, e.g. "Gym · Mon/Wed/Fri · around 6 PM · Strong".
 * Days and time are formatted with the shared [formatDays]/[formatTime] helpers
 * so the phrasing matches everywhere it appears.
 */
@Composable
private fun RoutineCard(routine: RoutineSummary) {
    val days = routine.daysIso.mapNotNull { runCatching { java.time.DayOfWeek.of(it) }.getOrNull() }
    val daysLabel = days.formatDays()
    val timeLabel = java.time.LocalTime.of(routine.typicalHour.coerceIn(0, 23), 0).formatTime()
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.placeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$daysLabel · around $timeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            CategoryChip(categoryName = routine.category)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${routine.visitCount} visits",
                style = MonoStatSmall,
                color = VoyagerColors.Primary
            )
            VoyagerBadge(
                text = routine.confidence.toConfidenceStrength(),
                color = VoyagerColors.SurfaceVariant,
                contentColor = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlacePatternCard(pattern: PlacePatternSummary) {
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

// ============================================================================
// HIGHLIGHTS TAB — notable records/firsts + "on this day" memories
// ============================================================================

@Composable
private fun HighlightsContent(
    highlights: List<NotableHighlight>,
    memories: List<OnThisDayMemory>,
    narrative: InsightNarrative?
) {
    val accent = chapterAccent(StatisticsTab.HIGHLIGHTS)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { narrative?.let { StoryHero(it, accent) } }

        // Records + firsts, then past-year memories — collectible keepsakes.
        items(highlights) { highlight ->
            KeepsakeCard(eyebrow = highlight.title, text = highlight.detail, accent = accent)
        }
        items(memories) { memory ->
            val yearsLabel = if (memory.yearsAgo == 1) "On this day · 1 year ago"
                else "On this day · ${memory.yearsAgo} years ago"
            val bits = buildList {
                if (memory.placeVisitCount > 0) add("${memory.placeVisitCount} place${if (memory.placeVisitCount == 1) "" else "s"}")
                if (memory.distanceM > 0) add("%.1f km".format(memory.distanceM / 1000))
            }
            KeepsakeCard(
                eyebrow = yearsLabel,
                text = if (bits.isEmpty()) "A calmer day back then." else bits.joinToString(" and ") + ".",
                accent = accent,
                meta = if (memory.steps > 0) "${memory.steps} steps" else null
            )
        }
    }
}

// ============================================================================
// MOVEMENT TAB — Transport mode breakdown with DataCards
// ============================================================================

@Composable
private fun MovementAnalyticsContent(
    movementStats: MovementStats?,
    trackingStreak: com.cosmiclaboratory.voyager.domain.usecase.TrackingStreak?,
    explorationScore: com.cosmiclaboratory.voyager.domain.usecase.ExplorationScore?,
    heatmap: Heatmap?,
    yearInReview: YearInReview?,
    periodLabel: String
) {
    val accent = chapterAccent(StatisticsTab.MOVEMENT)
    val hasHeatmap = heatmap != null && heatmap.days.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StoryHero(InsightNarrators.movement(movementStats, periodLabel), accent) }

        item {
            DataLedger(
                buildList {
                    movementStats?.let { stats ->
                        add(LedgerRow("Distance", "%.1f km".format(stats.totalDistanceKm)))
                        if (stats.avgSpeedKmh > 0) add(LedgerRow("Avg speed", "%.1f km/h".format(stats.avgSpeedKmh)))
                    }
                    trackingStreak?.let { streak ->
                        if (streak.currentDays > 0) add(LedgerRow("Current streak", "${streak.currentDays} d"))
                        if (streak.longestDays > 0) add(LedgerRow("Longest streak", "${streak.longestDays} d"))
                    }
                    explorationScore?.let { exp ->
                        add(LedgerRow("Exploration", "${exp.score} / 100"))
                        if (exp.newPlaces > 0) add(LedgerRow("New places", "${exp.newPlaces}"))
                    }
                }
            )
        }

        if (hasHeatmap) {
            item { SectionHeader(title = "Activity — last 26 weeks") }
            item { ActivityHeatmapCard(heatmap!!) }
        }

        yearInReview?.takeIf { it.activeDays > 0 }?.let { yir ->
            item { SectionHeader(title = "${yir.year} in review") }
            item { YearInReviewCard(yir) }
        }
    }
}

/**
 * GitHub-style calendar heatmap: one column per week, one cell per day, colour
 * ramped by the pre-bucketed 0–4 intensity. Days with no rollup render as an
 * empty cell so gaps read honestly. Scrolls horizontally when the window is wide.
 */
@Composable
private fun ActivityHeatmapCard(heatmap: Heatmap) {
    val dayMap = remember(heatmap) { heatmap.days.associateBy { it.dayKey } }
    val dates = remember(heatmap) { heatmap.days.map { java.time.LocalDate.parse(it.dayKey) } }
    if (dates.isEmpty()) return
    val minDate = dates.min()
    val maxDate = dates.max()
    val weeks = remember(heatmap) {
        val gridStart = minDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val gridEnd = maxDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        val cols = mutableListOf<List<java.time.LocalDate>>()
        var weekStart = gridStart
        while (!weekStart.isAfter(gridEnd)) {
            cols.add((0..6).map { weekStart.plusDays(it.toLong()) })
            weekStart = weekStart.plusWeeks(1)
        }
        cols
    }

    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Text(
            text = "Distance per day",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.OnSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        val inRange = !date.isBefore(minDate) && !date.isAfter(maxDate)
                        val intensity = dayMap[date.toString()]?.intensity ?: 0
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (inRange) heatColor(intensity) else Color.Transparent)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
            (0..4).forEach { i ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatColor(i))
                )
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
        }
    }
}

private fun heatColor(intensity: Int): Color = when (intensity.coerceIn(0, 4)) {
    0 -> VoyagerColors.SurfaceVariant.copy(alpha = 0.4f)
    1 -> VoyagerColors.AccentGreen.copy(alpha = 0.30f)
    2 -> VoyagerColors.AccentGreen.copy(alpha = 0.50f)
    3 -> VoyagerColors.AccentGreen.copy(alpha = 0.72f)
    else -> VoyagerColors.AccentGreen
}

@Composable
private fun YearInReviewCard(yir: YearInReview) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            YearStat("${yir.activeDays}", "active days")
            YearStat("%.0f km".format(yir.totalDistanceM / 1000), "travelled")
            YearStat(formatSteps(yir.totalSteps), "steps")
        }
        yir.longestDistanceDay?.let { d ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Biggest day: %.1f km on ${d.dayKey}".format(d.value / 1000),
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.YearStat(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MonoStatMedium, color = VoyagerColors.Primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
    }
}

private fun formatSteps(steps: Long): String = when {
    steps >= 1_000_000 -> "%.1fM".format(steps / 1_000_000.0)
    steps >= 1_000 -> "%.0fk".format(steps / 1_000.0)
    else -> steps.toString()
}

// ============================================================================
// CARBON TAB — per-transport-mode CO2 estimate
// ============================================================================

@Composable
private fun CarbonFootprintContent(
    footprint: com.cosmiclaboratory.voyager.domain.model.CarbonFootprint?,
    periodLabel: String
) {
    val accent = chapterAccent(StatisticsTab.CARBON)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StoryHero(InsightNarrators.carbon(footprint), accent) }

        if (footprint != null && !footprint.isEmpty) {
            item {
                DataLedger(
                    buildList {
                        add(LedgerRow("Total CO₂", "%.1f kg".format(footprint.totalKgCo2)))
                        add(LedgerRow("Distance", "%.0f km".format(footprint.totalDistanceKm)))
                        add(LedgerRow("≈ tree-years", "%.0f".format(footprint.treeYearEquivalent)))
                        footprint.modes.forEach { mode ->
                            val name = mode.mode.name.lowercase().replaceFirstChar { it.uppercase() }
                            add(LedgerRow("$name · %.1f km".format(mode.distanceKm), "%.1f kg".format(mode.kgCo2)))
                        }
                    }
                )
            }
            item {
                Text(
                    text = "Estimates use average emission factors per kilometre — a guide, not an audit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
    }
}

// CarbonModeRow / carbonModeLabel removed — the Carbon lens now renders per-mode
// rows in the typographic DataLedger.

// ============================================================================
// RHYTHM TAB — home-overnight ("sleep" proxy) + commute
// ============================================================================

@Composable
private fun RhythmContent(sleep: SleepRhythm?, dayRhythm: DayRhythm?, commute: CommuteStats?) {
    val accent = chapterAccent(StatisticsTab.RHYTHM)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StoryHero(InsightNarrators.rhythm(sleep), accent) }
        sleep?.let { s ->
            item { SectionHeader(title = "Home Overnight") }
            item { SleepCard(s) }
        }
        dayRhythm?.let { r ->
            item { SectionHeader(title = "Typical Day") }
            item { DayRhythmCard(r) }
        }
        commute?.let { c ->
            item { SectionHeader(title = "Commute") }
            c.toWork?.let { leg -> item { CommuteCard("To work", leg) } }
            c.toHome?.let { leg -> item { CommuteCard("To home", leg) } }
        }
    }
}

/**
 * "Your typical day" as two 24-hour bands (weekday / weekend), each cell coloured
 * by where you usually are that hour — Home overnight, Work by day, Out in the
 * evening, and muted gaps for time on the move.
 */
@Composable
private fun DayRhythmCard(rhythm: DayRhythm) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Text(
            text = "Where you usually are",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.OnSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Weekday", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        HourBand(rhythm.weekday)
        Spacer(modifier = Modifier.height(10.dp))
        Text("Weekend", style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        HourBand(rhythm.weekend)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "12a").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayStateLegend(DayState.HOME, "Home")
            DayStateLegend(DayState.WORK, "Work")
            DayStateLegend(DayState.OUT, "Out")
            DayStateLegend(DayState.AWAY, "Moving")
        }
    }
}

@Composable
private fun HourBand(slots: List<HourSlot>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        slots.forEach { slot ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(dayStateColor(slot.state))
            )
        }
    }
}

@Composable
private fun DayStateLegend(state: DayState, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dayStateColor(state))
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
    }
}

private fun dayStateColor(state: DayState): Color = when (state) {
    DayState.HOME -> VoyagerColors.AccentBlue
    DayState.WORK -> VoyagerColors.AccentPurple
    DayState.OUT -> VoyagerColors.AccentGreen
    DayState.AWAY -> VoyagerColors.SurfaceVariant.copy(alpha = 0.5f)
}

@Composable
private fun SleepCard(sleep: SleepRhythm) {
    val settle = sleep.settleMinuteOfDay?.let { minuteToClock(it) }
    val wake = sleep.wakeMinuteOfDay?.let { minuteToClock(it) }
    val windowText = when {
        settle != null && wake != null -> "$settle – $wake"
        wake != null -> "Up around $wake"
        settle != null -> "In around $settle"
        else -> "—"
    }
    val consistencyText = when (sleep.consistency) {
        SleepRhythm.Consistency.CONSISTENT -> "Consistent wake time"
        SleepRhythm.Consistency.VARIABLE -> "Variable wake time"
        SleepRhythm.Consistency.UNKNOWN -> null
    }
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        VoyagerEyebrow(text = "Home Overnight")
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = windowText, style = MonoStatMedium, color = VoyagerColors.Primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${formatHm(sleep.medianOvernightMs)} typical · ${sleep.nightsAnalyzed} nights",
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
        consistencyText?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Based on time at home overnight — a proxy, not sleep tracking.",
            style = MaterialTheme.typography.labelSmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun CommuteCard(title: String, leg: CommuteLeg) {
    val dep = leg.typicalDepartureMinuteOfDay?.let { minuteToClock(it) }
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = formatHm(leg.medianDurationMs), style = MonoStatSmall, color = VoyagerColors.Primary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = buildString {
                append("${leg.samples} trips")
                if (dep != null) append(" · usually leaves $dep")
            },
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Fastest ${formatHm(leg.fastestMs)} · Slowest ${formatHm(leg.slowestMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

// ============================================================================
// BALANCE TAB — where your time goes (time budget) + category distribution
// ============================================================================

@Composable
private fun BalanceContent(
    timeBudget: TimeBudget?,
    socialStats: SocialHealthStats?,
    periodLabel: String
) {
    val accent = chapterAccent(StatisticsTab.BALANCE)
    val hasBudget = timeBudget != null && !timeBudget.isEmpty
    val hasCategories = socialStats?.categoryBreakdown?.isNotEmpty() == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StoryHero(InsightNarrators.balance(timeBudget, periodLabel), accent) }
        if (hasBudget) {
            item {
                val b = timeBudget!!
                val total = b.totalMs.coerceAtLeast(1L)
                val raw = listOf(
                    "Home" to b.homeMs,
                    "Work" to b.workMs,
                    "Out" to b.outMs,
                    "Moving" to b.movingMs,
                    "Untracked" to b.untrackedMs
                ).filter { it.second > 0L }
                val maxMs = raw.maxOfOrNull { it.second } ?: 0L
                val slices = raw.map { (label, ms) ->
                    BalanceSlice(label, ((ms * 100) / total).toInt(), dominant = ms == maxMs)
                }
                BalanceType(slices, accent)
            }
        }
        if (hasCategories) {
            item { SectionHeader(title = "Places by category") }
            item { CategoryDistributionCard(socialStats!!) }
        }
    }
}

// TimeBudgetCard removed — the Balance lens now renders BalanceType (words sized
// by their share of the week).

@Composable
private fun CategoryDistributionCard(socialStats: SocialHealthStats) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        val total = socialStats.categoryBreakdown.values.sum().coerceAtLeast(1)
        val segments = socialStats.categoryBreakdown.map { (category, count) ->
            ProgressSegment(
                fraction = count.toFloat() / total,
                color = VoyagerColors.Primary,
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

private fun minuteToClock(minuteOfDay: Int): String =
    java.time.LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59)).formatTime()

private fun formatHm(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// ============================================================================
// ANOMALIES TAB
// ============================================================================

@Composable
private fun AnomaliesContent(
    anomalies: List<com.cosmiclaboratory.voyager.domain.model.Anomaly>,
    routineBreaks: List<RoutineBreak>,
    periodLabel: String
) {
    val accent = chapterAccent(StatisticsTab.ANOMALIES)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StoryHero(InsightNarrators.anomalies(anomalies.size, routineBreaks.size, periodLabel), accent) }
        if (routineBreaks.isNotEmpty()) {
            item { SectionHeader(title = "Routine Watch") }
            items(routineBreaks) { breakItem -> RoutineBreakCard(breakItem) }
        }
        if (anomalies.isNotEmpty()) {
            item { SectionHeader(title = "Anomalies — $periodLabel") }
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
}

@Composable
private fun RoutineBreakCard(breakItem: RoutineBreak) {
    val name = breakItem.placeName ?: "a place you usually visit"
    val time = minuteToClock(breakItem.expectedHour.coerceIn(0, 23) * 60)
    val (icon, message) = when (breakItem.kind) {
        RoutineBreak.Kind.MISSED ->
            Icons.Default.EventBusy to "You usually visit $name around $time — not yet today."
        RoutineBreak.Kind.LATE ->
            Icons.Default.Schedule to "You're at $name later than usual today (usually ~$time)."
        RoutineBreak.Kind.EARLY ->
            Icons.Default.Schedule to "You visited $name earlier than usual today (usually ~$time)."
    }
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.GLASS) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VoyagerColors.AccentAmber,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurface
            )
        }
    }
}

// ============================================================================
// SHARED COMPONENTS
// ============================================================================

// EmptyStateMessage removed — every lens now handles its empty state through the
// StoryHero narrator's gentle fallback copy.

// ============================================================================
// HELPERS
// ============================================================================

/** Compact ledger delta, e.g. "↑18%" / "↓4%". Direction is carried by the arrow. */
private fun deltaLabel(change: Double): String {
    val arrow = if (change >= 0) "↑" else "↓"
    return "$arrow${abs(change).roundToInt()}%"
}
