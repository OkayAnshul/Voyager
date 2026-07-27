package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.LocalReduceMotion
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerDurations
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerEasing
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.tweenOrSnap

/** One concentric ring of the activity display. */
data class ActivityRing(
    val progress: Float,
    val color: Color,
    val label: String,
    val valueText: String,
    val trackColor: Color = color.copy(alpha = 0.16f)
)

/**
 * Apple-Fitness-style concentric activity rings, drawn on a [Canvas]. The
 * outermost ring is index 0. Rings sweep in on first composition (snap under
 * reduce-motion) and expose a single merged semantics description for TalkBack.
 *
 * Named [VoyagerActivityRings] to coexist with the legacy fixed-arg
 * `ActivityRings(distanceProgress, stepsProgress, activeTimeProgress)` while
 * adding animation, labels, glow and a center slot.
 */
@Composable
fun VoyagerActivityRings(
    rings: List<ActivityRing>,
    modifier: Modifier = Modifier,
    ringSize: Dp = 168.dp,
    strokeWidth: Dp = 13.dp,
    gap: Dp = 6.dp,
    glow: Boolean = true,
    center: @Composable (BoxScope.() -> Unit)? = null
) {
    val reduce = LocalReduceMotion.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(rings) { started = true }

    val animated = ArrayList<Float>(rings.size)
    for (i in rings.indices) {
        val value by animateFloatAsState(
            targetValue = if (started) rings[i].progress.coerceIn(0f, 1f) else 0f,
            animationSpec = tweenOrSnap(reduce, VoyagerDurations.xlarge, i * 80, VoyagerEasing.emphasizedDecelerate),
            label = "ring-$i"
        )
        animated.add(value)
    }

    val description = rings.joinToString(separator = ", ") { "${it.label}: ${it.valueText}" }

    Box(
        modifier = modifier
            .size(ringSize)
            .then(if (glow) Modifier.drawBehind {
                drawRect(VoyagerGradients.primaryGlow(size.width, size.height))
            } else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val gapPx = gap.toPx()
            for (i in rings.indices) {
                val inset = stroke / 2f + i * (stroke + gapPx)
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val topLeft = Offset(inset, inset)
                // Track
                drawArc(
                    color = rings[i].trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Progress
                drawArc(
                    color = rings[i].color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated[i],
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        center?.invoke(this)
    }
}
