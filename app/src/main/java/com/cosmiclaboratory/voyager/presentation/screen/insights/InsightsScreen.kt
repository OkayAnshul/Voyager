package com.cosmiclaboratory.voyager.presentation.screen.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.Teal
import com.cosmiclaboratory.voyager.ui.theme.TealDim
import com.cosmiclaboratory.voyager.utils.PermissionStatus

/**
 * Insights Screen - Matrix UI
 *
 * Hub for all analytics and insights features:
 * - Weekly Comparison
 * - Patterns & Insights
 * - Movement & Time Analytics
 * - Social & Health Insights
 */
@Composable
fun InsightsScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    onNavigateToWeeklyComparison: () -> Unit = {},
    onNavigateToPlacePatterns: () -> Unit = {},
    onNavigateToMovementAnalytics: () -> Unit = {},
    onNavigateToSocialHealthAnalytics: () -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        MatrixCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INSIGHTS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
                MatrixIconButton(onClick = { viewModel.refreshInsights() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingDots()
                        Text(
                            text = "LOADING INSIGHTS...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.errorMessage != null -> {
                MatrixCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = uiState.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Weekly Comparison
                    item {
                        AnalyticsCard(
                            title = "WEEKLY COMPARISON",
                            description = "Compare this week to last week",
                            icon = Icons.Default.DateRange,
                            onClick = onNavigateToWeeklyComparison
                        )
                    }

                    // Patterns & Insights
                    item {
                        AnalyticsCard(
                            title = "PATTERNS & INSIGHTS",
                            description = "Discover behavioral patterns and anomalies",
                            icon = Icons.Default.Star,
                            onClick = onNavigateToPlacePatterns
                        )
                    }

                    // Movement & Time Analytics
                    item {
                        AnalyticsCard(
                            title = "MOVEMENT & TIME PATTERNS",
                            description = "View temporal trends and movement statistics",
                            icon = Icons.Default.Timeline,
                            onClick = onNavigateToMovementAnalytics
                        )
                    }

                    // Social & Health Analytics
                    item {
                        AnalyticsCard(
                            title = "SOCIAL & HEALTH INSIGHTS",
                            description = "Analyze social activity and health patterns",
                            icon = Icons.Default.Favorite,
                            onClick = onNavigateToSocialHealthAnalytics
                        )
                    }

                    // Empty state if no data
                    if (uiState.weeklyAnalytics == null && !uiState.isLoading && uiState.errorMessage == null) {
                        item {
                            EmptyStateMessage(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = Teal,
                                        modifier = Modifier.size(64.dp)
                                    )
                                },
                                title = "NO INSIGHTS YET",
                                message = "Track your location for a few days to see insights and patterns"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Analytics Card - Navigation to analytics features
 */
@Composable
private fun AnalyticsCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    MatrixCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Teal
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Go",
                tint = Teal
            )
        }
    }
}

