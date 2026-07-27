package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.LiveWorkoutStats
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients

/**
 * Strava-inspired live workout recorder: pick a mode → a live route map + big metrics while
 * recording (with pause/resume and honest moving-time pace) → a saved-summary card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutRecordScreen(
    onBack: () -> Unit,
    onViewActivities: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val live by viewModel.liveStats.collectAsState()
    val route by viewModel.liveRoute.collectAsState()
    val recording by viewModel.isRecording.collectAsState()
    val saved by viewModel.savedActivity.collectAsState()
    val achievements by viewModel.achievements.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Record") },
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
            contentAlignment = Alignment.TopCenter,
        ) {
            val savedActivity = saved
            when {
                savedActivity != null -> SavedView(
                    activity = savedActivity,
                    achievements = achievements,
                    onDone = viewModel::consumeSaved,
                    onViewActivities = onViewActivities,
                )
                recording -> RecordingView(
                    live = live,
                    route = route,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onFinish = viewModel::stop,
                )
                else -> IdleView(onStart = viewModel::start)
            }
        }
    }
}

@Composable
private fun IdleView(onStart: (WorkoutType) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(WorkoutType.RUN) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("What are you doing?", style = MaterialTheme.typography.headlineSmall, color = VoyagerColors.OnSurface)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutType.entries.take(3).forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { selected = type },
                    label = { Text(type.displayName) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutType.entries.drop(3).forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { selected = type },
                    label = { Text(type.displayName) },
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { onStart(selected) },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = VoyagerColors.Primary),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Start", modifier = Modifier.size(36.dp))
                Text("START", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Uses high-accuracy GPS while recording — best on a charge for long sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecordingView(
    live: LiveWorkoutStats?,
    route: List<RoutePoint>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val paused = live?.isPaused == true
    val type = live?.type ?: WorkoutType.OTHER
    Column(modifier = Modifier.fillMaxSize()) {
        // Live route map — the hero, growing as you move.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            WorkoutMap(
                route = route,
                color = workoutRouteColorArgb(type),
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(VoyagerColors.Background.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(10.dp).background(if (paused) VoyagerColors.Warning else VoyagerColors.Error, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    type.displayName + (if (paused) " · paused" else " · recording"),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (paused) VoyagerColors.Warning else VoyagerColors.Error,
                )
            }
        }

        // Metrics + controls.
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HugeMetric(value = WorkoutFormat.duration(live?.movingTimeMs ?: 0L), label = "Moving time")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Metric(value = WorkoutFormat.distanceKm(live?.distanceMeters ?: 0.0), label = "km")
                Metric(value = WorkoutFormat.pace(live?.avgPaceSecPerKm), label = "/km")
                Metric(value = "${(live?.elevationGainM ?: 0.0).toInt()}", label = "m ↑")
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                // Pause / resume.
                Button(
                    onClick = { if (paused) onResume() else onPause() },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VoyagerColors.Surface),
                ) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                        modifier = Modifier.size(32.dp),
                        tint = VoyagerColors.OnSurface,
                    )
                }
                // Finish.
                Button(
                    onClick = onFinish,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VoyagerColors.Error),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Stop, contentDescription = "Finish", modifier = Modifier.size(32.dp))
                        Text("FINISH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedView(
    activity: Activity,
    achievements: List<com.cosmiclaboratory.voyager.domain.model.Achievement>,
    onDone: () -> Unit,
    onViewActivities: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Workout saved", style = MaterialTheme.typography.headlineSmall, color = VoyagerColors.OnSurface)
        Spacer(Modifier.height(8.dp))
        Text(activity.displayTitle, style = MaterialTheme.typography.titleMedium, color = VoyagerColors.OnSurfaceVariant)
        if (achievements.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            AchievementChips(achievements)
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Metric(value = WorkoutFormat.distanceKm(activity.distanceMeters), label = "km")
            Metric(value = WorkoutFormat.duration(activity.durationMs), label = "time")
            Metric(value = WorkoutFormat.pace(activity.avgPaceSecPerKm), label = "/km")
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Metric(value = "${activity.elevationGainM.toInt()}", label = "m ↑")
            Metric(value = "${activity.elevationLossM.toInt()}", label = "m ↓")
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = onViewActivities, modifier = Modifier.fillMaxWidth()) { Text("View activities") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun HugeMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = VoyagerColors.OnSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = VoyagerColors.OnSurfaceVariant)
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = VoyagerColors.OnSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = VoyagerColors.OnSurfaceVariant)
    }
}
