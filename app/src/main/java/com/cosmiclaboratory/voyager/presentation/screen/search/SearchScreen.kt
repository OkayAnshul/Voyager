package com.cosmiclaboratory.voyager.presentation.screen.search

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.SearchFilters
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlaceClick: (Long) -> Unit = {},
    onDayClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val immediateQuery by viewModel.immediateQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
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

        // Filter chips — date presets, place categories, transport modes
        SearchFilterBar(
            filters = uiState.filters,
            onIntent = viewModel::onIntent,
        )

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
                                        highlightMatch(place.displayName, uiState.query),
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
                            variant = CardVariant.GLASS,
                            padding = 12.dp
                        ) {
                            Text(
                                highlightMatch(visit.placeDisplayName, uiState.query),
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
                            onClick = { onDayClick(day.dayKey) },
                            variant = CardVariant.GLASS,
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
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
                        color = VoyagerColors.OnSurface
                    )
                    Text(
                        "Try a different place, a date like 2026-06, or widen your search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Horizontally-scrollable filter row: a Clear chip (when any filter is active), rolling date
 * presets, place-category chips, and transport-mode chips. Each dispatches the matching
 * [SearchIntent] so the repository can narrow results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterBar(
    filters: SearchFilters,
    onIntent: (SearchIntent) -> Unit,
) {
    val today = LocalDate.now()
    val datePresets = remember(today) {
        listOf(
            "7 days" to DateRange(today.minusDays(6).toString(), today.toString()),
            "30 days" to DateRange(today.minusDays(29).toString(), today.toString()),
            "This year" to DateRange(LocalDate.of(today.year, 1, 1).toString(), today.toString()),
        )
    }
    val categories = remember { PlaceCategory.values().filter { it != PlaceCategory.UNKNOWN } }
    val transports = remember {
        listOf(SegmentType.WALK, SegmentType.RUN, SegmentType.CYCLE, SegmentType.DRIVE, SegmentType.TRANSIT, SegmentType.FLIGHT)
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!filters.isEmpty()) {
            item {
                AssistChip(
                    onClick = { onIntent(SearchIntent.ClearFilters) },
                    label = { Text("Clear") },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = VoyagerColors.Primary,
                        leadingIconContentColor = VoyagerColors.Primary,
                    ),
                )
            }
        }
        items(datePresets) { (label, range) ->
            SearchChip(
                label = label,
                selected = filters.dateRange == range,
                onClick = { onIntent(SearchIntent.SetDateRange(if (filters.dateRange == range) null else range)) },
            )
        }
        items(categories) { category ->
            SearchChip(
                label = category.displayName,
                selected = filters.placeCategories?.contains(category) == true,
                onClick = { onIntent(SearchIntent.ToggleCategoryFilter(category)) },
            )
        }
        items(transports) { mode ->
            SearchChip(
                label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = filters.transportModes?.contains(mode) == true,
                onClick = { onIntent(SearchIntent.ToggleTransportFilter(mode)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = VoyagerColors.OnSurfaceVariant,
            selectedContainerColor = VoyagerColors.Primary,
            selectedLabelColor = VoyagerColors.OnPrimary,
        ),
    )
}

/** Highlights the matched query substring in a result label (case-insensitive). */
private fun highlightMatch(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isBlank()) return AnnotatedString(text)
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(color = VoyagerColors.Primary, fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, idx + q.length))
        }
        append(text.substring(idx + q.length))
    }
}
