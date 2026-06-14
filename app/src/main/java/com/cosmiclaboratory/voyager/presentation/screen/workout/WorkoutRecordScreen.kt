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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.LiveWorkoutStats
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients

/**
 * Strava-inspired live workout recorder: pick a mode → big live metrics while recording →
 * a saved-summary card. Self-contained and callback-driven so it doesn't depend on the
 * in-progress nav graph; wire it into the NavHost when ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutRecordScreen(
    onBack: () -> Unit,
    onViewActivities: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val live by viewModel.liveStats.collectAsState()
    val recording by viewModel.isRecording.collectAsState()
    val saved by viewModel.savedActivity.collectAsState()

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
                    onDone = viewModel::consumeSaved,
                    onViewActivities = onViewActivities,
                )
                recording -> RecordingView(live = live, onFinish = viewModel::stop)
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
        Text("What are you doing?", style = MaterialTheme.typography.headlineSmall)
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
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecordingView(live: LiveWorkoutStats?, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                (live?.type?.displayName ?: "Workout") + " · recording",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
        HugeMetric(value = WorkoutFormat.duration(live?.durationMs ?: 0L), label = "Time")
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Metric(value = WorkoutFormat.distanceKm(live?.distanceMeters ?: 0.0), label = "km")
            Metric(value = WorkoutFormat.pace(live?.avgPaceSecPerKm), label = "/km")
        }
        Spacer(Modifier.height(16.dp))
        Metric(value = WorkoutFormat.speedKmh(live?.currentSpeedMps ?: 0f), label = "km/h now")
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Stop, contentDescription = "Finish", modifier = Modifier.size(36.dp))
                Text("FINISH", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SavedView(activity: Activity, onDone: () -> Unit, onViewActivities: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Workout saved", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(activity.displayTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Metric(value = WorkoutFormat.distanceKm(activity.distanceMeters), label = "km")
            Metric(value = WorkoutFormat.duration(activity.durationMs), label = "time")
            Metric(value = WorkoutFormat.pace(activity.avgPaceSecPerKm), label = "/km")
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
        Text(value, fontSize = 72.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
