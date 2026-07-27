package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Voyager Design System — Motion
 *
 * Expressive but disciplined. Data animates (counters roll, rings sweep, charts
 * grow); chrome stays calm. Every motion routes through [LocalReduceMotion] so a
 * single switch collapses the app to opacity-only / snap-to-value.
 */

/** Animation durations, in milliseconds, aligned to Material 3 motion. */
object VoyagerDurations {
    const val fast = 120        // taps, chip flips
    const val standard = 240    // default enters/exits, cross-fades
    const val large = 400       // counters, sheets, sliding indicators
    const val xlarge = 560      // ring sweeps, camera fly-to
    const val pulse = 1500      // live-status loop
}

/** Shared easing curves. */
object VoyagerEasing {
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** Reusable spring specs for gestures and playful overshoot. */
object VoyagerSprings {
    val snappy: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    val gentle: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
}

/**
 * True when the user (or system) prefers reduced motion. Provided at the app
 * root from [android.provider.Settings.Global.ANIMATOR_DURATION_SCALE]; defaults
 * to false so components animate unless told otherwise.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * A [tween] that becomes an instant [snap] when reduce-motion is active.
 * Pass the current [LocalReduceMotion] value as [reduceMotion].
 */
fun <T> tweenOrSnap(
    reduceMotion: Boolean,
    durationMillis: Int = VoyagerDurations.standard,
    delayMillis: Int = 0,
    easing: Easing = VoyagerEasing.emphasized
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(durationMillis, delayMillis, easing)

/**
 * Fades + lifts a list item into place with a per-index stagger. Collapses to an
 * instant appearance under reduce-motion. Apply to each row in a list/column.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    itemDelayMillis: Int = 30,
    riseDp: Float = 14f
): Modifier = composed {
    val reduce = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val delay = (index * itemDelayMillis).coerceAtMost(300)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tweenOrSnap(reduce, VoyagerDurations.standard, delay, VoyagerEasing.emphasizedDecelerate),
        label = "stagger-alpha"
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else riseDp,
        animationSpec = tweenOrSnap(reduce, VoyagerDurations.large, delay, VoyagerEasing.emphasizedDecelerate),
        label = "stagger-translate"
    )
    graphicsLayer {
        this.alpha = alpha
        translationY = translateY * density
    }
}

/**
 * Press-feedback scale for tappable surfaces. Honors reduce-motion (no scale).
 * [pressed] is typically driven by an [androidx.compose.foundation.interaction.InteractionSource].
 */
fun Modifier.pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = VoyagerSprings.snappy,
        label = "press-scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** No-op draw helper kept for symmetry with future shimmer overlays. */
internal fun Modifier.passthrough(): Modifier = drawWithContent { drawContent() }
