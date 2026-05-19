package com.cosmiclaboratory.voyager.presentation.screen.trips

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.model.TripDay
import com.cosmiclaboratory.voyager.domain.model.TripDetail
import com.cosmiclaboratory.voyager.domain.model.TripPlaceVisit
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FMT = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Trip detail — the day-by-day journal of one trip, with a printable trip-book export.
 * Reached only from the Pro-gated [TripsScreen].
 */
@Composable
fun TripDetailScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TripDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.exportUri) {
        val uri = state.exportUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share trip book"))
        viewModel.onAction(TripDetailAction.ConsumeExportResult)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        val detail = state.detail
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = VoyagerColors.Primary
            )
            detail == null -> Text(
                text = "This trip is no longer available.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            else -> TripDetailContent(
                detail = detail,
                isExporting = state.isExporting,
                error = state.error,
                onExport = { viewModel.onAction(TripDetailAction.ExportBook) }
            )
        }
    }
}

@Composable
private fun TripDetailContent(
    detail: TripDetail,
    isExporting: Boolean,
    error: String?,
    onExport: () -> Unit
) {
    val trip = detail.trip
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.HIGHLIGHTED) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.OnSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${trip.durationDays} days · ${trip.placeCount} places · " +
                        "${trip.visitCount} stops · %.0f km".format(trip.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }

        item {
            VoyagerButton(
                onClick = onExport,
                enabled = !isExporting && detail.days.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp,
                        color = VoyagerColors.Primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isExporting) "Building trip book…" else "Export trip book (PDF)")
            }
        }

        error?.let {
            item {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.Error
                )
            }
        }

        items(detail.days, key = { it.dayKey }) { day ->
            TripDayCard(day)
        }
    }
}

@Composable
private fun TripDayCard(day: TripDay) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatDay(day.dayKey),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.OnSurface
        )
        Text(
            text = "%.1f km · %d place(s)".format(day.distanceMeters / 1000.0, day.places.size),
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        day.places.forEach { place -> TripPlaceRow(place) }
    }
}

@Composable
private fun TripPlaceRow(place: TripPlaceVisit) {
    val zone = remember { ZoneId.systemDefault() }
    val arrival = remember(place.arrivalAt) {
        TIME_FMT.format(Instant.ofEpochMilli(place.arrivalAt).atZone(zone))
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = arrival,
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = (place.emoji?.let { "$it " } ?: "") + place.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurface
        )
    }
}

private fun formatDay(dayKey: String): String = runCatching {
    DAY_FMT.format(LocalDate.parse(dayKey))
}.getOrDefault(dayKey)
