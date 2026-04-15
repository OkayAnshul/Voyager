package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * F-35: Reusable empty/error state composable for consistent messaging across screens.
 */

enum class EmptyStateType(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String?
) {
    NO_TRACKING(
        icon = Icons.Outlined.LocationOff,
        title = "Tracking Not Active",
        description = "Start tracking to see your location history and visited places.",
        actionLabel = "Start Tracking"
    ),
    NO_PERMISSION(
        icon = Icons.Default.Lock,
        title = "Location Permission Required",
        description = "Grant location permission to track your movements and discover places.",
        actionLabel = "Grant Permission"
    ),
    NO_PLACES(
        icon = Icons.Default.Place,
        title = "No Places Yet",
        description = "Keep tracking and Voyager will automatically detect the places you visit.",
        actionLabel = null
    ),
    NO_INSIGHTS(
        icon = Icons.Outlined.Lightbulb,
        title = "No Insights Available",
        description = "Visit more places over time to unlock personalized movement insights.",
        actionLabel = null
    )
}

@Composable
fun EmptyStateComposable(
    type: EmptyStateType,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = type.title,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = type.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = type.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (type.actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onAction) {
                Text(text = type.actionLabel)
            }
        }
    }
}
