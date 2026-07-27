package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.LocalReduceMotion
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerDurations
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerShapes
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSpacing
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSprings
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSurfaces
import com.cosmiclaboratory.voyager.presentation.theme.tweenOrSnap

/**
 * Voyager's custom bottom navigation. Replaces the stock Material [NavigationBar]
 * with a glass row and an animated aurora indicator pill behind the selected tab
 * — the icon scales up and the label brightens, with light haptics on tap.
 */
@Composable
fun VoyagerNavBar(
    items: List<VoyagerDestination>,
    selectedRoute: String?,
    onItemSelected: (VoyagerDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = VoyagerSpacing.sm, vertical = VoyagerSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { destination ->
            VoyagerNavItem(
                destination = destination,
                selected = destination.route == selectedRoute,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onItemSelected(destination)
                }
            )
        }
    }
}

@Composable
private fun RowScope.VoyagerNavItem(
    destination: VoyagerDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val reduce = LocalReduceMotion.current
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tweenOrSnap(reduce, VoyagerDurations.standard),
        label = "nav-fill"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 0.9f,
        animationSpec = if (reduce) tweenOrSnap(reduce) else VoyagerSprings.snappy,
        label = "nav-scale"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) VoyagerColors.OnPrimary else VoyagerColors.OnSurfaceVariant,
        animationSpec = tweenOrSnap(reduce, VoyagerDurations.standard),
        label = "nav-content"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .clip(VoyagerShapes.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { role = Role.Tab }
            .padding(vertical = VoyagerSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            // Filled circle indicator (fades in when selected)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = fillAlpha }
                    .background(VoyagerColors.Primary, VoyagerShapes.pill)
            )
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.title,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            )
        }
    }
}
