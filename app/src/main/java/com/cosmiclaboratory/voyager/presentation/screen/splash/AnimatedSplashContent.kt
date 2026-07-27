package com.cosmiclaboratory.voyager.presentation.screen.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated splash content shown on cold start.
 *
 * Renders the uppercase "VOYAGER" wordmark (the app-wide brand mark, shared with the
 * top bar via [VoyagerWordmark]) on the OLED near-black background, with a soft primary
 * glow behind it and a small route line that draws itself beneath the name — a calm nod
 * to the "your route is the app" thesis. The "— Voyager by Anshul" signature fades in
 * after. Honours the system "remove animations" setting. Auto-completes after ~2s.
 */
@Composable
fun AnimatedSplashContent(onComplete: () -> Unit) {
    val titleAlpha = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.92f) }
    val routeProgress = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }

    // Respect the system "Remove animations" accessibility setting (matches VoyagerApp).
    val context = LocalContext.current
    val reduceMotion = remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            titleAlpha.snapTo(1f); titleScale.snapTo(1f)
            routeProgress.snapTo(1f); subtitleAlpha.snapTo(1f)
            delay(1200)
            onComplete()
        } else {
            launch { titleAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
            launch { titleScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            delay(250)
            routeProgress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
            subtitleAlpha.animateTo(1f, tween(450))
            delay(450)
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Wordmark with a soft primary glow behind it.
            VoyagerWordmark(
                color = VoyagerColors.OnSurface,
                fontSize = 44.sp,
                letterSpacing = 10.sp,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .scale(titleScale.value)
                    .drawBehind {
                        drawRect(VoyagerGradients.primaryGlow(size.width, size.height))
                    }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // A journey line that draws itself beneath the name.
            SplashRoute(
                progress = routeProgress.value,
                modifier = Modifier
                    .width(220.dp)
                    .height(44.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "— Voyager by Anshul",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )
        }
    }
}

/** A gentle multi-point route that draws in from start to head, with a glowing head dot. */
@Composable
private fun SplashRoute(progress: Float, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pts = listOf(
            Offset(w * 0.04f, h * 0.78f),
            Offset(w * 0.28f, h * 0.34f),
            Offset(w * 0.50f, h * 0.62f),
            Offset(w * 0.72f, h * 0.24f),
            Offset(w * 0.96f, h * 0.56f)
        )
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val total = measure.length

        // Faint full-route guide, then the saturated drawn portion on top.
        drawPath(
            path = path,
            color = VoyagerColors.Primary.copy(alpha = 0.12f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        val drawn = Path()
        measure.getSegment(0f, total * progress, drawn, true)
        drawPath(
            path = drawn,
            color = VoyagerColors.Primary,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Glowing head that leads the draw.
        if (progress > 0f) {
            val head = measure.getPosition(total * progress)
            drawCircle(VoyagerColors.Primary.copy(alpha = 0.22f), radius = 8.dp.toPx(), center = head)
            drawCircle(VoyagerColors.Primary, radius = 4.dp.toPx(), center = head)
        }
    }
}
