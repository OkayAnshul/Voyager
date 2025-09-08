package com.cosmiclaboratory.voyager.presentation.screen.tracking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.LocalPermissionActions
import com.cosmiclaboratory.voyager.domain.model.enums.PermissionState
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackingControlBanner(
    modifier: Modifier = Modifier,
    viewModel: TrackingControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionActions = LocalPermissionActions.current
    val isTracking = uiState.runtimeState?.isTracking == true
    val isPaused = uiState.runtimeState?.activeSessionId != null && !isTracking
    var showStopConfirmation by remember { mutableStateOf(false) }

    var toggleBurst by remember { mutableStateOf(false) }
    LaunchedEffect(isTracking) {
        toggleBurst = true
        delay(500)
        toggleBurst = false
    }

    val bannerScale by animateFloatAsState(
        targetValue = if (toggleBurst) 1.02f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bannerScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (toggleBurst) VoyagerColors.AccentGreen
                      else VoyagerColors.PrimaryDim.copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "borderColor"
    )

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { scaleX = bannerScale; scaleY = bannerScale },
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = when {
                isTracking -> MaterialTheme.colorScheme.primaryContainer
                isPaused -> VoyagerColors.AccentAmber.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                imageVector = when {
                    isTracking -> Icons.Default.MyLocation
                    isPaused -> Icons.Default.Pause
                    else -> Icons.Default.LocationOff
                },
                contentDescription = null,
                tint = when {
                    isTracking -> MaterialTheme.colorScheme.primary
                    isPaused -> VoyagerColors.AccentAmber
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(Modifier.width(12.dp))

            // Status text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isTracking -> "Tracking Active"
                        isPaused -> "Tracking Paused"
                        else -> "Tracking Stopped"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        isPaused -> VoyagerColors.AccentAmber
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                uiState.health?.lastSampleAt?.let { lastAt ->
                    val ago = (System.currentTimeMillis() - lastAt) / 1000
                    Text(
                        text = "Last sample ${ago}s ago",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons — simplified: Pause (tap) / Stop (long-press)
            if (isTracking) {
                FilledTonalButton(
                    modifier = Modifier.combinedClickable(
                        onClick = { viewModel.onIntent(TrackingControlIntent.PauseTracking) },
                        onLongClick = { showStopConfirmation = true }
                    ),
                    onClick = { viewModel.onIntent(TrackingControlIntent.PauseTracking) }
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pause")
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        if (uiState.permissionState == PermissionState.NOTHING ||
                            uiState.permissionState == PermissionState.NO_LOCATION_WITH_AR) {
                            permissionActions.requestLocationPermissions()
                        } else if (isPaused) {
                            viewModel.onIntent(TrackingControlIntent.ResumeTracking)
                        } else {
                            viewModel.onIntent(TrackingControlIntent.StartTracking)
                        }
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            uiState.permissionState == PermissionState.NOTHING ||
                            uiState.permissionState == PermissionState.NO_LOCATION_WITH_AR -> "Grant & Start"
                            isPaused -> "Resume"
                            else -> "Start"
                        }
                    )
                }
            }
        }

        // Permission degradation warning
        if (uiState.permissionState != PermissionState.FULL) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Limited permissions - some features unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { permissionActions.requestLocationPermissions() }) {
                    Text("Fix")
                }
            }
        }
    }

    // Stop confirmation dialog
    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop Tracking?") },
            text = { Text("This will end the current tracking session. You can start a new one later.") },
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
