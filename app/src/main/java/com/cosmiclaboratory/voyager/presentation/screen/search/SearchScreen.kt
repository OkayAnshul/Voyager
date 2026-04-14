package com.cosmiclaboratory.voyager.presentation.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlaceClick: (Long) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val immediateQuery by viewModel.immediateQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background)
    ) {
        // Search bar
        VoyagerCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            padding = 0.dp
        ) {
            OutlinedTextField(
                value = immediateQuery,
                onValueChange = { viewModel.onIntent(SearchIntent.UpdateQuery(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search places, visits, days...",
                        color = VoyagerColors.OnSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = VoyagerColors.Primary
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VoyagerColors.Primary,
                    unfocusedBorderColor = VoyagerColors.SurfaceVariant,
                    cursorColor = VoyagerColors.Primary,
                    focusedTextColor = VoyagerColors.OnSurface,
                    unfocusedTextColor = VoyagerColors.OnSurface
                )
            )
        }

        // Loading indicator
        if (uiState.isSearching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = VoyagerColors.Primary,
                trackColor = VoyagerColors.SurfaceVariant
            )
        }

        // Results
        uiState.results?.let { results ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Place results
                if (results.places.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Places (${results.places.size})")
                    }
                    items(results.places) { place ->
                        VoyagerCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPlaceClick(place.placeId) },
                            padding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        place.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VoyagerColors.OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryChip(
                                            categoryName = place.category.displayName
                                        )
                                        Text(
                                            "${place.visitCount} visits",
                                            style = MonoTimestamp,
                                            color = VoyagerColors.OnSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = VoyagerColors.OnSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Visit results
                if (results.visits.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(title = "Visits (${results.visits.size})")
                    }
                    items(results.visits) { visit ->
                        VoyagerCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = 12.dp
                        ) {
                            Text(
                                visit.placeDisplayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VoyagerColors.OnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                visit.dayKey,
                                style = MonoTimestamp,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }
                }

                // Day results
                if (results.days.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(title = "Days (${results.days.size})")
                    }
                    items(results.days) { day ->
                        VoyagerCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = 12.dp
                        ) {
                            Text(
                                day.dayKey,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VoyagerColors.OnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${day.matchingPlaceCount} places, ${day.matchingSegmentCount} segments",
                                style = MonoTimestamp,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Prompt state — before any search has been run
        if (!uiState.hasSearched && uiState.query.isBlank() && !uiState.isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "Search your timeline",
                        style = MaterialTheme.typography.titleSmall,
                        color = VoyagerColors.OnSurface
                    )
                    Text(
                        "Find places by name, visits by date, or days when you went somewhere new.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Empty state
        if (uiState.hasSearched && uiState.results?.totalCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "No results for \"${uiState.query}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }
    }
}
