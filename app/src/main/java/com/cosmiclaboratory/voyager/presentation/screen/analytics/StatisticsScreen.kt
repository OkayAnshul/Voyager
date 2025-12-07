package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.theme.GlassChip
import com.cosmiclaboratory.voyager.presentation.theme.GlassGradientContainer
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

/**
 * Statistics Screen
 *
 * Consolidated analytics screen that merges:
 * - Weekly Comparison (week-over-week trends)
 * - Place Patterns (routine identification)
 * - Movement Analytics (distance, speed stats)
 * - Social Health Analytics (unique places, variety metrics)
 *
 * Provides comprehensive insights with glassmorphism design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(StatisticsTab.WEEKLY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Selector
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatisticsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = null) }
                    )
                }
            }

            // Content based on selected tab
            when (selectedTab) {
                StatisticsTab.WEEKLY -> WeeklyComparisonContent(uiState.weeklyComparison)
                StatisticsTab.PATTERNS -> PlacePatternsContent(uiState.placePatterns)
                StatisticsTab.MOVEMENT -> MovementAnalyticsContent(uiState.movementStats)
                StatisticsTab.SOCIAL -> SocialHealthContent(uiState.socialStats)
            }
        }
    }
}

enum class StatisticsTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    WEEKLY("Weekly", Icons.Default.DateRange),
    PATTERNS("Patterns", Icons.Default.Place),
    MOVEMENT("Movement", Icons.Default.Star),
    SOCIAL("Social", Icons.Default.Person)
}

@Composable
private fun WeeklyComparisonContent(weeklyComparison: WeeklyComparisonData?) {
    if (weeklyComparison == null) {
        EmptyStateMessage("Loading weekly comparison...")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassGradientContainer {
                Column {
                    Text(
                        text = "This Week vs Last Week",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = weeklyComparison.dateRange,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Places Visited",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("This Week: ${weeklyComparison.placesThisWeek}")
                        Text("Last Week: ${weeklyComparison.placesLastWeek}")
                    }
                    ComparisonIndicator(
                        change = weeklyComparison.placesChange,
                        label = "places"
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Distance Traveled",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("This Week: %.1f km".format(weeklyComparison.distanceThisWeek / 1000))
                        Text("Last Week: %.1f km".format(weeklyComparison.distanceLastWeek / 1000))
                    }
                    ComparisonIndicator(
                        change = weeklyComparison.distanceChange,
                        label = "km"
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Time Away from Home",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("This Week: ${weeklyComparison.timeAwayThisWeek}h")
                        Text("Last Week: ${weeklyComparison.timeAwayLastWeek}h")
                    }
                    ComparisonIndicator(
                        change = weeklyComparison.timeAwayChange,
                        label = "hours"
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacePatternsContent(placePatterns: List<PlacePattern>?) {
    if (placePatterns == null || placePatterns.isEmpty()) {
        EmptyStateMessage("No patterns detected yet. Visit more places to see patterns.")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassGradientContainer {
                Text(
                    text = "Your Place Patterns",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(placePatterns) { pattern ->
            GlassCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pattern.placeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        GlassChip(
                            label = pattern.category,
                            selected = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Visited ${pattern.visitCount} times",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Typical days: ${pattern.typicalDays.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Avg duration: ${pattern.avgDurationMinutes} min",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementAnalyticsContent(movementStats: MovementStats?) {
    if (movementStats == null) {
        EmptyStateMessage("Loading movement statistics...")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassGradientContainer {
                Text(
                    text = "Movement Statistics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Total Distance (30 days)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%.2f km".format(movementStats.totalDistanceKm),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Average Speed",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%.1f km/h".format(movementStats.avgSpeedKmh),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Most Active Day",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = movementStats.mostActiveDay,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialHealthContent(socialStats: SocialHealthStats?) {
    if (socialStats == null) {
        EmptyStateMessage("Loading social health statistics...")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassGradientContainer {
                Text(
                    text = "Social & Health Insights",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Unique Places Visited",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${socialStats.uniquePlaces}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "in the last 30 days",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Place Variety Score",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${socialStats.varietyScore}/100",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { socialStats.varietyScore / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            GlassCard {
                Column {
                    Text(
                        text = "Category Distribution",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    socialStats.categoryBreakdown.forEach { (category, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(category)
                            Text("$count visits")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonIndicator(change: Double, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (change > 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
        val color = if (change > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${if (change > 0) "+" else ""}%.1f%% $label".format(change),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
