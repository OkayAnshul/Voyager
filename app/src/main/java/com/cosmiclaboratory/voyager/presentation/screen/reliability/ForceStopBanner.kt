package com.cosmiclaboratory.voyager.presentation.screen.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Constant: a sample gap beyond this on cold start almost always means the app was
 * Force-Stopped from system Settings (which blocks the foreground service, WorkManager,
 * and boot receivers until the user manually relaunches). Nothing the app can do at OS
 * level prevents it — but it can explain it instead of looking broken.
 */
const val FORCE_STOP_GAP_THRESHOLD_MS: Long = 24L * 60L * 60L * 1000L

/**
 * Returns true when the last accepted sample is older than [FORCE_STOP_GAP_THRESHOLD_MS].
 * [nowMs] is injected for testability.
 */
fun shouldShowForceStopBanner(lastSampleAt: Long?, nowMs: Long): Boolean {
    if (lastSampleAt == null || lastSampleAt <= 0L) return false
    return nowMs - lastSampleAt > FORCE_STOP_GAP_THRESHOLD_MS
}

@Composable
fun ForceStopBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tracking was paused",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "No location data for over 24 hours — Voyager was likely " +
                        "Force-Stopped from system Settings. Tracking has resumed now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
