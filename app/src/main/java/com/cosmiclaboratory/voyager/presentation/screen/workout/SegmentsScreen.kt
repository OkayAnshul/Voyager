package com.cosmiclaboratory.voyager.presentation.screen.workout

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.SegmentWithEfforts
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients

/**
 * Saved "race-yourself" segments with your best time and effort count — the private analogue to a
 * Strava segment list. Create one from an activity's detail screen (the route icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentsScreen(
    onBack: () -> Unit,
    viewModel: SegmentsViewModel = hiltViewModel(),
) {
    val segments by viewModel.segments.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Segments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(VoyagerGradients.screenBackground(size.width, size.height)) }
                .padding(padding),
        ) {
            if (segments.isEmpty()) {
                Text(
                    "No segments yet.\nOpen an activity and tap the route icon to save one, then race yourself.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = VoyagerColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(segments, key = { it.segment.id }) { SegmentRow(it, onDelete = { viewModel.delete(it.segment.id) }) }
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(item: SegmentWithEfforts, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.segment.name, style = MaterialTheme.typography.titleMedium, color = VoyagerColors.OnSurface)
                Text(
                    "${WorkoutFormat.distanceKm(item.segment.distanceMeters)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val best = item.bestMs
                Text(
                    if (best != null) WorkoutFormat.duration(best) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = VoyagerColors.Premium,
                )
                Text(
                    if (item.count > 0) "best of ${item.count}" else "no efforts yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete segment", tint = VoyagerColors.OnSurfaceVariant)
            }
        }
    }
}
