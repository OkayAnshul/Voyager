package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings section for timeline grouping preferences
 *
 * Allows users to configure how visits are grouped in the timeline view:
 * - 15 minutes: Show more detail, separate short visits
 * - 30 minutes: Balanced view (default)
 * - 60 minutes: Simplified view, group related activities
 */
@Composable
fun TimelineSettingsSection(
    timeWindowMinutes: Long,
    onTimeWindowChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Timeline Grouping",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Group nearby visits within:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = timeWindowMinutes == 15L,
                onClick = { onTimeWindowChanged(15L) },
                label = { Text("15 min") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = timeWindowMinutes == 30L,
                onClick = { onTimeWindowChanged(30L) },
                label = { Text("30 min") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = timeWindowMinutes == 60L,
                onClick = { onTimeWindowChanged(60L) },
                label = { Text("1 hour") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Explanation text for each option
        Text(
            text = when (timeWindowMinutes) {
                15L -> "Detailed view - Shows each visit separately"
                30L -> "Balanced view - Groups related activities (recommended)"
                60L -> "Simplified view - Groups longer time periods"
                else -> "Groups visits that are close in time"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
