package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.LocalReduceMotion
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerShapes

/**
 * Standardized loading shimmer. Replaces the ad-hoc per-screen skeletons so every
 * loading state shares one sweep. Honors reduce-motion (static muted fill).
 *
 * Build skeletons that mirror the real layout with [ShimmerBox] / [ShimmerLine].
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val reduce = LocalReduceMotion.current
    val base = VoyagerColors.SurfaceVariant
    val highlight = VoyagerColors.SurfaceBright
    if (reduce) return SolidColor(base)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + 400f, 0f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = VoyagerShapes.card
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun ShimmerLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = VoyagerShapes.pill
    )
}

@Composable
fun ShimmerDot(size: Dp = 40.dp) {
    ShimmerBox(
        modifier = Modifier.size(size),
        shape = VoyagerShapes.pill
    )
}
