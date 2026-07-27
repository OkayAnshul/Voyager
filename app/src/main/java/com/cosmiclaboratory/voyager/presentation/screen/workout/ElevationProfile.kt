package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * A minimal elevation profile: altitude (y) over cumulative distance (x), drawn as a filled area
 * with an accent stroke. One series, one accent, muted baseline — consistent with the app's other
 * native charts ([com.cosmiclaboratory.voyager.presentation.components.RouteSparkline]).
 *
 * [profile] is (cumulative-distance-metres, altitude-metres); fewer than two points draws nothing.
 */
@Composable
fun ElevationProfile(
    profile: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    if (profile.size < 2) return
    val minAlt = profile.minOf { it.second }
    val maxAlt = profile.maxOf { it.second }
    val maxDist = profile.last().first.coerceAtLeast(1.0)
    val altSpan = (maxAlt - minAlt).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun x(d: Double) = (d / maxDist * w).toFloat()
        // Leave a little headroom top/bottom so peaks/valleys aren't clipped to the edge.
        fun y(a: Double) = (h * (0.9f - 0.8f * ((a - minAlt) / altSpan))).toFloat()

        val line = Path().apply {
            moveTo(x(profile.first().first), y(profile.first().second))
            for (i in 1 until profile.size) lineTo(x(profile[i].first), y(profile[i].second))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(VoyagerColors.AccentGreen.copy(alpha = 0.35f), VoyagerColors.AccentGreen.copy(alpha = 0f)),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(path = line, color = VoyagerColors.AccentGreen, style = Stroke(width = 3f))
        // Baseline.
        drawLine(
            color = VoyagerColors.OnSurfaceVariant.copy(alpha = 0.25f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 1f,
        )
    }
}
