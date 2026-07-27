package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.PersonalRecords
import com.cosmiclaboratory.voyager.domain.model.WorkoutSuggestion
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.presentation.components.ActivityRing
import com.cosmiclaboratory.voyager.presentation.components.RouteSparkline
import com.cosmiclaboratory.voyager.presentation.components.VoyagerActivityRings
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerPrimaryButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerShapes
import com.cosmiclaboratory.voyager.presentation.theme.staggeredEntrance
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Activities tab — the Athlete persona's home. Opens on a live "this week" Activity-Rings hero
 * with a prominent Record CTA, then a newest-first feed of recorded workouts rendered as glass
 * cards with a route thumbnail, per-type colour, monospace stats, and a personal-record chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    onRecord: () -> Unit,
    onActivityClick: (Long) -> Unit = {},
    onSegments: () -> Unit = {},
    viewModel: ActivitiesViewModel = hiltViewModel(),
) {
    val activities by viewModel.activities.collectAsState()
    val records by viewModel.records.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!text.isNullOrBlank()) viewModel.importGpx(text)
        }
    }

    // The single activity holding the all-time longest distance gets a PR chip in the feed.
    val longestActivityId = activities.filter { it.distanceMeters > 0 }
        .maxByOrNull { it.distanceMeters }?.id

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Activities") },
                actions = {
                    IconButton(onClick = onSegments) {
                        Icon(Icons.Filled.Route, contentDescription = "Segments")
                    }
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import GPX")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(VoyagerGradients.screenBackground(size.width, size.height)) }
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hero") { WeekHero(activities, onRecord) }
                items(suggestions, key = { "suggestion-${it.segmentId}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onSave = { viewModel.saveSuggestion(suggestion) },
                        onDismiss = { viewModel.dismissSuggestion(suggestion.segmentId) },
                    )
                }
                if (records.hasAny) item(key = "records") { RecordsCard(records) }
                if (activities.isEmpty()) {
                    item(key = "empty") { EmptyActivities() }
                } else {
                    itemsIndexed(activities, key = { _, a -> a.id }) { index, activity ->
                        ActivityRow(
                            activity = activity,
                            index = index,
                            isPersonalRecord = activity.id == longestActivityId,
                            onClick = { onActivityClick(activity.id) },
                        )
                    }
                }
            }
        }
    }
}

/** "This week" Activity-Rings hero with a legend and the primary Record CTA. */
@Composable
private fun WeekHero(activities: List<Activity>, onRecord: () -> Unit) {
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val week = activities.filter { it.startedAt >= weekAgo }
    val distanceM = week.sumOf { it.distanceMeters }
    val activeMs = week.sumOf { it.durationMs }
    val count = week.size

    val rings = listOf(
        ActivityRing(
            progress = (distanceM / 1000.0 / WEEK_DISTANCE_GOAL_KM).toFloat(),
            color = VoyagerColors.AccentOrange, label = "Distance",
            valueText = "${WorkoutFormat.distanceKm(distanceM)} km",
        ),
        ActivityRing(
            progress = activeMs.toFloat() / (WEEK_ACTIVE_GOAL_MIN * 60_000f),
            color = VoyagerColors.AccentGreen, label = "Active",
            valueText = WorkoutFormat.duration(activeMs),
        ),
        ActivityRing(
            progress = count / WEEK_ACTIVITY_GOAL.toFloat(),
            color = VoyagerColors.AccentBlue, label = "Activities",
            valueText = "$count",
        ),
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), tint = VoyagerGradients.heroCard) {
        Text("This week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = VoyagerColors.OnSurface)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            VoyagerActivityRings(rings = rings, ringSize = 120.dp, strokeWidth = 10.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow(VoyagerColors.AccentOrange, "${WorkoutFormat.distanceKm(distanceM)} km", "distance")
                LegendRow(VoyagerColors.AccentGreen, WorkoutFormat.duration(activeMs), "active time")
                LegendRow(VoyagerColors.AccentBlue, "$count", "activities")
            }
        }
        Spacer(Modifier.height(16.dp))
        VoyagerPrimaryButton(onClick = onRecord, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Record activity")
        }
    }
}

@Composable
private fun LegendRow(color: Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(VoyagerShapes.pill).background(color))
        Column {
            Text(value, style = MonoStatMedium, color = VoyagerColors.OnSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: WorkoutSuggestion, onSave: () -> Unit, onDismiss: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), tint = VoyagerGradients.heroCard) {
        Text("Looks like a ${suggestion.type.displayName.lowercase()}", style = MaterialTheme.typography.titleMedium, color = VoyagerColors.OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "${WorkoutFormat.distanceKm(suggestion.distanceMeters)} km · ${WorkoutFormat.duration(suggestion.durationMs)} · ${formatDate(suggestion.startedAt)} — save it as an activity?",
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Save") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun RecordsCard(records: PersonalRecords) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("Personal records", style = MaterialTheme.typography.titleMedium, color = VoyagerColors.OnSurface)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RecordStat(WorkoutFormat.distanceKm(records.longestDistanceM), "km longest")
            RecordStat("${records.biggestClimbM.toInt()}", "m climb")
            RecordStat("${records.longestStreakDays}", "day streak")
        }
        if (records.bestEfforts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            records.bestEfforts.entries
                .sortedBy { it.key.meters }
                .forEach { (dist, timeMs) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fastest ${dist.label}", style = MaterialTheme.typography.bodyMedium, color = VoyagerColors.OnSurfaceVariant)
                        Text(WorkoutFormat.duration(timeMs), style = MonoStatMedium, color = VoyagerColors.OnSurface)
                    }
                    Spacer(Modifier.height(4.dp))
                }
        }
    }
}

@Composable
private fun RecordStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MonoStatMedium, fontWeight = FontWeight.Bold, color = VoyagerColors.Premium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
    }
}

@Composable
private fun ActivityRow(activity: Activity, index: Int, isPersonalRecord: Boolean, onClick: () -> Unit) {
    val accent = workoutTypeColor(activity.type)
    GlassCard(
        modifier = Modifier.fillMaxWidth().staggeredEntrance(index),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(VoyagerShapes.pill).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(workoutTypeIcon(activity.type), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(activity.displayTitle, style = MaterialTheme.typography.titleMedium, color = VoyagerColors.OnSurface)
                Text(
                    "${activity.type.displayName} · ${formatDate(activity.startedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Stat(WorkoutFormat.distanceKm(activity.distanceMeters), "km")
                    Stat(WorkoutFormat.duration(activity.durationMs), "time")
                    Stat(WorkoutFormat.pace(activity.avgPaceSecPerKm), "/km")
                }
                if (isPersonalRecord) {
                    Spacer(Modifier.height(8.dp))
                    PrChip("🏆 Longest")
                }
            }
            RouteSparkline(
                encodedPolyline = activity.encodedPolyline,
                color = accent,
                width = 64.dp,
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MonoStatMedium, color = VoyagerColors.OnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
    }
}

@Composable
private fun PrChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = VoyagerColors.Premium,
        modifier = Modifier
            .background(VoyagerColors.Premium.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyActivities() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = VoyagerColors.AccentOrange,
            )
            Spacer(Modifier.height(12.dp))
            Text("No recorded workouts yet", style = MaterialTheme.typography.titleMedium, color = VoyagerColors.OnSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap Record above to start your first run, ride, or walk.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The vivid per-type accent used for the icon badge and route thumbnail. */
private fun workoutTypeColor(type: WorkoutType): Color = when (type) {
    WorkoutType.RUN -> VoyagerColors.AccentOrange
    WorkoutType.WALK -> VoyagerColors.TransportWalk
    WorkoutType.CYCLE -> VoyagerColors.TransportCycle
    WorkoutType.HIKE -> VoyagerColors.Success
    WorkoutType.OTHER -> VoyagerColors.Primary
}

private fun workoutTypeIcon(type: WorkoutType): ImageVector = when (type) {
    WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    WorkoutType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    WorkoutType.CYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
    WorkoutType.HIKE -> Icons.Filled.Terrain
    WorkoutType.OTHER -> Icons.Filled.FitnessCenter
}

private const val WEEK_DISTANCE_GOAL_KM = 25.0
private const val WEEK_ACTIVE_GOAL_MIN = 150
private const val WEEK_ACTIVITY_GOAL = 5

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DATE_FMT)
