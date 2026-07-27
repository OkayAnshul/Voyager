package com.cosmiclaboratory.voyager.presentation.screen.workout

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.Split
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Strava-style post-activity detail: route map + summary stats + per-km splits + elevation
 * profile, with edit-title/notes, delete, and GPX share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSegment by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(state.activity?.displayTitle ?: "Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val a = state.activity
                    if (a != null) {
                        IconButton(onClick = { showSegment = true }) { Icon(Icons.Filled.Route, contentDescription = "Save as segment") }
                        IconButton(onClick = { showEdit = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { shareGpx(context, a.displayTitle, viewModel.gpx()) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share GPX")
                        }
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
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
            val a = state.activity
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = VoyagerColors.Primary)
                a == null -> Text("Activity not found", Modifier.align(Alignment.Center), color = VoyagerColors.OnSurfaceVariant)
                else -> DetailContent(a, state.route, state.splits, state.elevationProfile, state.achievements)
            }
        }
    }

    val editing = state.activity
    if (showEdit && editing != null) {
        EditDialog(
            initialTitle = editing.title.orEmpty(),
            initialNotes = editing.notes.orEmpty(),
            onDismiss = { showEdit = false },
            onSave = { t, n -> viewModel.updateUserFields(t, n); showEdit = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete activity?") },
            text = { Text("This removes the recorded workout. It can't be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(); showDelete = false }) { Text("Delete", color = VoyagerColors.Error) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
    if (showSegment && editing != null) {
        SegmentNameDialog(
            default = editing.displayTitle,
            onDismiss = { showSegment = false },
            onSave = { name -> viewModel.saveAsSegment(name); showSegment = false },
        )
    }
}

@Composable
private fun SegmentNameDialog(default: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(default) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as segment") },
        text = {
            Column {
                Text("Race yourself on this route — future activities that cover it get timed against your best.", style = MaterialTheme.typography.bodyMedium, color = VoyagerColors.OnSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Segment name") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DetailContent(
    activity: Activity,
    route: List<RoutePoint>,
    splits: List<Split>,
    elevationProfile: List<Pair<Double, Double>>,
    achievements: List<com.cosmiclaboratory.voyager.domain.model.Achievement>,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Route map.
        WorkoutMap(
            route = route,
            color = workoutRouteColorArgb(activity.type),
            follow = false,
            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)),
        )

        Text(
            "${activity.type.displayName} · ${formatDateTime(activity.startedAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant,
        )

        if (achievements.isNotEmpty()) {
            AchievementChips(achievements, modifier = Modifier.fillMaxWidth())
        }

        // Summary stats (GlassCard already applies padding + is a Column).
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile(WorkoutFormat.distanceKm(activity.distanceMeters), "km")
                StatTile(WorkoutFormat.duration(activity.durationMs), "time")
                StatTile(WorkoutFormat.pace(activity.avgPaceSecPerKm), "/km")
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("${activity.elevationGainM.toInt()}", "m ↑")
                StatTile("${activity.elevationLossM.toInt()}", "m ↓")
                StatTile(WorkoutFormat.speedKmh(activity.maxSpeedMps), "max km/h")
            }
        }

        // Elevation profile.
        if (elevationProfile.size >= 2) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Elevation", style = MaterialTheme.typography.titleSmall, color = VoyagerColors.OnSurface)
                Spacer(Modifier.height(12.dp))
                ElevationProfile(profile = elevationProfile, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        }

        // Splits.
        if (splits.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Splits (per km)", style = MaterialTheme.typography.titleSmall, color = VoyagerColors.OnSurface)
                Spacer(Modifier.height(8.dp))
                splits.forEach {
                    SplitRow(it)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Notes.
        activity.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Notes", style = MaterialTheme.typography.titleSmall, color = VoyagerColors.OnSurface)
                Spacer(Modifier.height(6.dp))
                Text(notes, style = MaterialTheme.typography.bodyMedium, color = VoyagerColors.OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = VoyagerColors.OnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurfaceVariant)
    }
}

@Composable
private fun SplitRow(split: Split) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${split.index} km", color = VoyagerColors.OnSurfaceVariant, fontFamily = FontFamily.Monospace)
        Text(WorkoutFormat.pace(split.paceSecPerKm) + " /km", color = VoyagerColors.OnSurface, fontFamily = FontFamily.Monospace)
        Text("+${split.elevationGainM.toInt()} m", color = VoyagerColors.OnSurfaceVariant, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun EditDialog(
    initialTitle: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, notes) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Writes the GPX to the shareable cache dir and fires a chooser. */
private fun shareGpx(context: Context, title: String, gpx: String?) {
    if (gpx.isNullOrEmpty()) return
    val safe = title.replace(Regex("[^A-Za-z0-9-_]"), "_").ifBlank { "activity" }
    val file = File(context.cacheDir, "$safe.gpx")
    file.writeText(gpx)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share GPX"))
}

private val DATE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

private fun formatDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DATE_TIME_FMT)
