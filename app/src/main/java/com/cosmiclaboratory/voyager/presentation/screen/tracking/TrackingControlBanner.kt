package com.cosmiclaboratory.voyager.presentation.screen.tracking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.LocalPermissionActions
import com.cosmiclaboratory.voyager.domain.model.enums.PermissionState
import com.cosmiclaboratory.voyager.presentation.theme.PulsingDot
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerOutlinedButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerPrimaryButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSpacing

/**
 * The dashboard's live-tracking control.
 *
 * While **off**, it's a prominent, inviting "Start tracking" hero (starting should be easy).
 * While **active/paused**, it collapses into a small, stylish status chip — a pulsing dot +
 * one line of status — with Pause/Resume/Stop **hidden behind a tap** (the chevron), so the
 * session can't be stopped by accident. Tap the chip to reveal the controls.
 */
@Composable
fun TrackingControlBanner(
    modifier: Modifier = Modifier,
    viewModel: TrackingControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionActions = LocalPermissionActions.current
    val isTracking = uiState.runtimeState?.isTracking == true
    val isPaused = uiState.runtimeState?.isPaused == true
    val permissionMissing = uiState.permissionState == PermissionState.NOTHING ||
        uiState.permissionState == PermissionState.NO_LOCATION_WITH_AR
    val degraded = uiState.permissionState != PermissionState.FULL && !permissionMissing

    var showStopConfirmation by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // First-run fix: "Grant & start" only launches the permission request; once the grant
    // lands (permissionState changes) auto-start so the user needn't tap the button twice.
    var pendingStart by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.permissionState) {
        if (pendingStart && !permissionMissing && !isTracking && !isPaused) {
            viewModel.onIntent(TrackingControlIntent.StartTracking)
            pendingStart = false
        }
    }

    if (isTracking || isPaused) {
        // ── Compact, collapsible status chip (controls tucked away) ──────────
        val accent: Color = if (isPaused) VoyagerColors.AccentAmber else VoyagerColors.AccentGreen
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            label = "chevron"
        )
        val subtitle = when {
            isPaused -> "Capture paused"
            else -> uiState.health?.lastSampleAt?.let { lastAt ->
                "Recording · last sample ${(System.currentTimeMillis() - lastAt) / 1000}s ago"
            } ?: "Recording your day"
        }

        VoyagerCard(
            modifier = modifier.fillMaxWidth(),
            onClick = { expanded = !expanded },
            padding = VoyagerSpacing.md,
            tintColor = accent.copy(alpha = if (isPaused) 0.12f else 0.10f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPaused) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    PulsingDot(size = 10.dp, color = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isPaused) "Tracking paused" else "Tracking active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPaused) accent else VoyagerColors.OnSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide controls" else "Show controls",
                    tint = VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(VoyagerSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isPaused) {
                            VoyagerPrimaryButton(
                                onClick = { viewModel.onIntent(TrackingControlIntent.ResumeTracking) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(4.dp)); Text("Resume")
                            }
                        } else {
                            VoyagerButton(
                                onClick = { viewModel.onIntent(TrackingControlIntent.PauseTracking) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Pause")
                            }
                        }
                        VoyagerOutlinedButton(
                            onClick = { showStopConfirmation = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Stop")
                        }
                    }
                    if (degraded) {
                        Spacer(Modifier.height(10.dp))
                        PermissionDegradedRow { permissionActions.requestLocationPermissions() }
                    }
                }
            }
        }
    } else {
        // ── Off — a prominent, inviting Start hero (starting should be easy) ──
        VoyagerCard(modifier = modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = VoyagerColors.OnSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Tracking off",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VoyagerColors.OnSurface
                    )
                    Text(
                        text = "Start to record where your day goes",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(VoyagerSpacing.md))
            VoyagerPrimaryButton(
                onClick = {
                    if (permissionMissing) {
                        pendingStart = true
                        permissionActions.requestLocationPermissions()
                    } else {
                        viewModel.onIntent(TrackingControlIntent.StartTracking)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (permissionMissing) "Grant & start tracking" else "Start tracking")
            }
            if (degraded) {
                Spacer(Modifier.height(10.dp))
                PermissionDegradedRow { permissionActions.requestLocationPermissions() }
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop tracking?") },
            text = { Text("This ends the current tracking session. You can start a new one any time.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(TrackingControlIntent.StopTracking)
                    showStopConfirmation = false
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PermissionDegradedRow(onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = VoyagerColors.Warning,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Limited permissions — some features may be unavailable",
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.Warning,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onFix) { Text("Fix") }
    }
}
