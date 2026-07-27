package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.LocalReduceMotion
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerDurations
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerEasing
import com.cosmiclaboratory.voyager.presentation.theme.tweenOrSnap
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge

/**
 * Animated numeric value. Rolls from its previous value to [target] over ~400ms
 * (snaps under reduce-motion). Returns the current interpolated value so callers
 * can format it however they like.
 */
@Composable
fun animatedCount(
    target: Float,
    durationMillis: Int = VoyagerDurations.large
): Float {
    val reduce = LocalReduceMotion.current
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = tweenOrSnap(reduce, durationMillis, easing = VoyagerEasing.standard),
        label = "animated-count"
    )
    return value
}

/**
 * A stat tile whose value counts up on change. JetBrains Mono value, optional
 * unit and label. Merges to a single TalkBack node ("Distance: 12.4 km").
 */
@Composable
fun AnimatedStat(
    value: Float,
    modifier: Modifier = Modifier,
    formatter: (Float) -> String = { it.toInt().toString() },
    style: TextStyle = MonoStatLarge,
    color: Color = VoyagerColors.OnSurface,
    unit: String? = null,
    label: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val animated = animatedCount(value)
    val display = formatter(animated)
    val spoken = buildString {
        if (label != null) append("$label: ")
        append(formatter(value))
        if (unit != null) append(" $unit")
    }
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = horizontalAlignment
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.material3.Text(text = display, style = style, color = color)
            if (unit != null) {
                androidx.compose.material3.Text(
                    text = unit,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        }
        if (label != null) {
            androidx.compose.material3.Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}
