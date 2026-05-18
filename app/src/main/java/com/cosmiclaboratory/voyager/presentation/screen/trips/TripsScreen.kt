package com.cosmiclaboratory.voyager.presentation.screen.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.Trip
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.billing.FeatureGate
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val RANGE_FMT = DateTimeFormatter.ofPattern("MMM d")

/**
 * Trips — auto-detected multi-day journeys away from home, each openable as a story.
 *
 * A Pro feature: wrapped in [FeatureGate], so free users see a locked card instead.
 */
@Composable
fun TripsScreen(
    onTripClick: (Long) -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel(),
    entitlementViewModel: EntitlementViewModel = hiltViewModel()
) {
    val isPro by entitlementViewModel.isPro.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        FeatureGate(
            isPro = isPro,
            featureName = "Trips",
            description = "Voyager automatically finds your multi-day trips away from " +
                "home and turns each one into a shareable, printable travel story.",
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            onUnlock = onNavigateToPaywall
        ) {
            TripsContent(state = state, onTripClick = onTripClick)
        }
    }
}

@Composable
private fun TripsContent(
    state: TripsUiState,
    onTripClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Trips",
                trailingAction = { ProBadge() }
            )
        }

        when {
            !state.hasHomeAnchor -> item {
                InfoCard(
                    title = "Set a Home place first",
                    body = "Trip detection measures journeys against your home. Once a " +
                        "place is confirmed as Home, your trips will appear here."
                )
            }
            state.trips.isEmpty() -> item {
                InfoCard(
                    title = if (state.isDetecting) "Looking for trips…" else "No trips yet",
                    body = if (state.isDetecting) {
                        "Scanning your timeline for multi-day journeys."
                    } else {
                        "No multi-day trips away from home have been detected yet."
                    }
                )
            }
            else -> items(state.trips, key = { it.id }) { trip ->
                TripCard(trip = trip, onClick = { onTripClick(trip.id) })
            }
        }
    }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    VoyagerCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        variant = CardVariant.HIGHLIGHTED
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = formatRange(trip),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            if (trip.isOngoing) OngoingChip()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${trip.durationDays} days · ${trip.placeCount} places · " +
                "%.0f km".format(trip.distanceKm),
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.OnSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun OngoingChip() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VoyagerColors.Primary.copy(alpha = 0.18f)
    ) {
        Text(
            text = "ONGOING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.Primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ProBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VoyagerColors.Premium.copy(alpha = 0.18f)
    ) {
        Text(
            text = "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.Premium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatRange(trip: Trip): String = runCatching {
    val start = LocalDate.parse(trip.startDayKey)
    val end = LocalDate.parse(trip.endDayKey)
    "${RANGE_FMT.format(start)} – ${RANGE_FMT.format(end)}, ${end.year}"
}.getOrDefault("${trip.startDayKey} – ${trip.endDayKey}")
