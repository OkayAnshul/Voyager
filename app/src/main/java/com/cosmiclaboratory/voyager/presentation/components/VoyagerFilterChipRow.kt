package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.LocalReduceMotion
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerDurations
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerShapes
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSpacing
import com.cosmiclaboratory.voyager.presentation.theme.tweenOrSnap

/**
 * Horizontally scrollable filter chips with an animated aurora-fill selection.
 * The premium alternative to a crowded [androidx.compose.material3.ScrollableTabRow]
 * — selecting a chip cross-fades its aurora background in. Pro-gated chips show a
 * gold lock.
 */
@Composable
fun VoyagerFilterChipRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    proGatedIndices: Set<Int> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(horizontal = VoyagerSpacing.screen)
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm),
        contentPadding = contentPadding
    ) {
        itemsIndexed(items) { index, label ->
            FilterChip(
                label = label,
                selected = index == selectedIndex,
                isPro = index in proGatedIndices,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(index)
                }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    isPro: Boolean,
    onClick: () -> Unit
) {
    val reduce = LocalReduceMotion.current
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tweenOrSnap(reduce, VoyagerDurations.standard),
        label = "chip-fill"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) VoyagerColors.OnPrimary else VoyagerColors.OnSurfaceVariant,
        animationSpec = tweenOrSnap(reduce, VoyagerDurations.standard),
        label = "chip-content"
    )

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(VoyagerShapes.pill)
            .background(VoyagerColors.Surface.copy(alpha = 0.5f), VoyagerShapes.pill)
            .border(
                width = 1.dp,
                color = if (selected) androidx.compose.ui.graphics.Color.Transparent
                else VoyagerColors.OnSurfaceVariant.copy(alpha = 0.30f),
                shape = VoyagerShapes.pill
            )
            .clickable(onClick = onClick)
    ) {
        // Animated solid brand fill behind the chip content when selected.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = fillAlpha }
                .background(VoyagerColors.Primary, VoyagerShapes.pill)
        )
        Row(
            modifier = Modifier.padding(horizontal = VoyagerSpacing.lg, vertical = VoyagerSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
        ) {
            if (isPro) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Pro",
                    tint = if (selected) VoyagerColors.OnPrimary else VoyagerColors.Premium,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
