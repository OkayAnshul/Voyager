package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.LocalPermissionActions
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionSnapshot
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * Persistent in-app banner shown when any permission is missing.
 *
 * Displays a compact warning with a "Fix" button that expands to show
 * exactly which permissions are missing and individual action buttons.
 * Disappears automatically once all permissions are granted.
 */
@Composable
fun PermissionReminderBanner(
    snapshot: PermissionSnapshot,
    modifier: Modifier = Modifier
) {
    // Nothing missing → don't render
    if (snapshot.isComplete) return

    val permissionActions = LocalPermissionActions.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                VoyagerColors.SurfaceVariant,
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // ── Compact row: icon + message + Fix button ────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = VoyagerColors.Warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${snapshot.missingCount} permission${if (snapshot.missingCount > 1) "s" else ""} missing",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "Tap to see details",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = VoyagerColors.OnSurfaceVariant
            )
        }

        // ── Expanded detail rows ────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!snapshot.hasFineLocation) {
                    MissingPermissionRow(
                        label = "Location",
                        detail = "Required for tracking",
                        onFix = { permissionActions.requestLocationPermissions() }
                    )
                }

                if (!snapshot.hasBackgroundLocation && snapshot.hasAnyLocation) {
                    MissingPermissionRow(
                        label = "Background Location",
                        detail = "\"Allow all the time\" for continuous tracking",
                        onFix = { permissionActions.requestBackgroundLocation() }
                    )
                }

                if (!snapshot.hasActivityRecognition) {
                    MissingPermissionRow(
                        label = "Activity Recognition",
                        detail = "Detect walk, drive, cycle",
                        onFix = { permissionActions.requestActivityRecognition() }
                    )
                }

                if (!snapshot.hasNotifications) {
                    MissingPermissionRow(
                        label = "Notifications",
                        detail = "Tracking status updates",
                        onFix = { permissionActions.requestNotifications() }
                    )
                }

                if (!snapshot.isBatteryOptimizationExempt) {
                    MissingPermissionRow(
                        label = "Battery Optimization",
                        detail = "Prevents tracking interruptions",
                        onFix = { permissionActions.requestBatteryOptimization() }
                    )
                }
            }
        }
    }
}

/**
 * Rough-timeline-mode banner — shown on the Timeline when only "Approximate" location
 * is granted. Explains that the timeline is grouped at a city scale (rather than
 * presenting misleadingly precise place pins) and offers a one-tap path to upgrade to
 * "Precise" location.
 */
@Composable
fun RoughLocationBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val permissionActions = LocalPermissionActions.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                VoyagerColors.Warning.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationSearching,
            contentDescription = null,
            tint = VoyagerColors.Warning,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "City-level timeline",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Voyager only has approximate location, so visits are grouped " +
                    "into broad areas. Switch to Precise location for exact places.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
        TextButton(
            onClick = { permissionActions.requestLocationPermissions() },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Fix",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.Warning
            )
        }
    }
}

@Composable
private fun MissingPermissionRow(
    label: String,
    detail: String,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoyagerColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = VoyagerColors.Error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
        TextButton(
            onClick = onFix,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Grant",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.Primary
            )
        }
    }
}
