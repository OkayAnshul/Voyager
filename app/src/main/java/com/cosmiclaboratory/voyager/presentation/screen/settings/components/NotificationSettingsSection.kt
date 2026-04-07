package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.UserSettings

@Composable
fun NotificationSettingsSection(
    settings: UserSettings,
    onUpdateSetting: (String, Any) -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    Column(modifier = modifier) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Permission Status Banner
        if (!hasNotificationPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Grant notification permission to receive alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Notification Permission")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Notification Types
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Notification Types",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Tracking Status
                NotificationToggleRow(
                    icon = Icons.Default.MyLocation,
                    title = "Tracking Status",
                    description = "Show tracking status in notification bar",
                    checked = settings.trackingStatusNotificationEnabled && hasNotificationPermission,
                    enabled = hasNotificationPermission,
                    onCheckedChange = { onUpdateSetting("trackingStatusNotificationEnabled", it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Daily Insights
                NotificationToggleRow(
                    icon = Icons.Default.Insights,
                    title = "Daily Insights",
                    description = "Get daily movement insights",
                    checked = settings.dailyInsightsEnabled && hasNotificationPermission,
                    enabled = hasNotificationPermission,
                    onCheckedChange = { onUpdateSetting("dailyInsightsEnabled", it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Weekly Insights
                NotificationToggleRow(
                    icon = Icons.Default.DateRange,
                    title = "Weekly Summary",
                    description = "Get weekly movement and place insights",
                    checked = settings.weeklyInsightsEnabled && hasNotificationPermission,
                    enabled = hasNotificationPermission,
                    onCheckedChange = { onUpdateSetting("weeklyInsightsEnabled", it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Anomaly Alerts
                NotificationToggleRow(
                    icon = Icons.Default.Warning,
                    title = "Anomaly Alerts",
                    description = "Get notified about unusual patterns",
                    checked = settings.anomalyAlertsEnabled && hasNotificationPermission,
                    enabled = hasNotificationPermission,
                    onCheckedChange = { onUpdateSetting("anomalyAlertsEnabled", it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Place Confirmation
                NotificationToggleRow(
                    icon = Icons.Default.Place,
                    title = "Place Confirmation",
                    description = "Prompt to confirm newly detected places",
                    checked = settings.placeConfirmationPromptsEnabled && hasNotificationPermission,
                    enabled = hasNotificationPermission,
                    onCheckedChange = { onUpdateSetting("placeConfirmationPromptsEnabled", it) }
                )
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
