package com.cosmiclaboratory.voyager.presentation.screen.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.*

/**
 * Screen showing detected patterns and anomalies
 *
 * Displays:
 * - Behavioral patterns (regular days, times, frequencies)
 * - Detected anomalies (missed visits, unusual behavior)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePatternsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlacePatternsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patterns & Insights") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is PlacePatternsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            is PlacePatternsUiState.Success -> {
                SuccessContent(
                    patterns = state.patterns,
                    anomalies = state.anomalies,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is PlacePatternsUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun SuccessContent(
    patterns: List<PlacePattern>,
    anomalies: List<Anomaly>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Anomalies section (if any)
        if (anomalies.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Anomalies",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unusual behavior detected in the last 2 weeks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(anomalies.take(5)) { anomaly ->
                AnomalyCard(anomaly)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Patterns section
        item {
            Text(
                text = "Your Patterns",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = if (anomalies.isNotEmpty()) 8.dp else 0.dp)
            )
            Text(
                text = "Detected from your visit history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (patterns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No patterns detected yet. Keep using Voyager to build your history!",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(patterns) { pattern ->
                PatternCard(pattern)
            }
        }

        // Empty state for anomalies
        if (anomalies.isEmpty() && patterns.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✅ No anomalies detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your recent behavior matches your usual patterns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternCard(pattern: PlacePattern) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Place name and pattern type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pattern.place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = pattern.patternType.name.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Pattern description
            Text(
                text = pattern.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // Confidence bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Confidence: ${pattern.confidence.formatConfidence()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pattern.confidence.toConfidenceStrength(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                LinearProgressIndicator(
                    progress = { pattern.confidence },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AnomalyCard(anomaly: Anomaly) {
    val containerColor = when (anomaly.severity) {
        AnomalySeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
        AnomalySeverity.LOW -> MaterialTheme.colorScheme.tertiaryContainer
        AnomalySeverity.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        AnomalySeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when (anomaly.severity) {
        AnomalySeverity.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        AnomalySeverity.LOW -> MaterialTheme.colorScheme.onTertiaryContainer
        AnomalySeverity.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
        AnomalySeverity.HIGH -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji indicator
            Text(
                text = anomaly.severity.toEmoji(),
                style = MaterialTheme.typography.headlineSmall
            )

            Column(modifier = Modifier.weight(1f)) {
                // Place name
                Text(
                    text = anomaly.place.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                // Anomaly message
                Text(
                    text = anomaly.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )

                // Additional details based on anomaly type
                when (anomaly) {
                    is Anomaly.MissedPlace -> {
                        Text(
                            text = "Usually ${anomaly.expectedFrequency.toInt()}x per week",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                    is Anomaly.UnusualDuration -> {
                        Text(
                            text = "${anomaly.percentageDifference.toInt()}% different from usual",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
