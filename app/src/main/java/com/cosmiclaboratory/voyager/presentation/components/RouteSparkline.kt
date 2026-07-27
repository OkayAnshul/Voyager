package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * A tiny custom-drawn preview of a trip's path — decodes the (already simplified)
 * route polyline and draws it normalized into a small box. Pure Canvas: no Map SDK,
 * no tiles, no network, no battery cost. Renders nothing when there are too few
 * points to form a line.
 *
 * @param encodedPolyline a Google-encoded polyline (prefer the simplified one).
 * @param color stroke colour, typically the transport-mode colour.
 */
@Composable
fun RouteSparkline(
    encodedPolyline: String?,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 36.dp
) {
    val points = remember(encodedPolyline) {
        encodedPolyline?.takeIf { it.isNotBlank() }?.let { PolylineEncoder.decode(it) } ?: emptyList()
    }
    if (points.size < 2) return

    val lats = points.map { it.first }
    val lngs = points.map { it.second }
    val minLat = lats.min(); val maxLat = lats.max()
    val minLng = lngs.min(); val maxLng = lngs.max()
    val latSpan = (maxLat - minLat).coerceAtLeast(1e-7)
    val lngSpan = (maxLng - minLng).coerceAtLeast(1e-7)

    Canvas(modifier = modifier.size(width, height)) {
        val inset = 3.dp.toPx()
        val w = (size.width - inset * 2).coerceAtLeast(1f)
        val h = (size.height - inset * 2).coerceAtLeast(1f)
        // Preserve aspect ratio: fit the larger span and centre the path.
        val span = maxOf(latSpan, lngSpan)
        val scale = minOf(w / span, h / span)
        val drawnW = (lngSpan * scale).toFloat()
        val drawnH = (latSpan * scale).toFloat()
        val offsetX = inset + (w - drawnW) / 2f
        val offsetY = inset + (h - drawnH) / 2f

        val path = Path()
        points.forEachIndexed { index, (lat, lng) ->
            val x = offsetX + ((lng - minLng) * scale).toFloat()
            // Invert Y: higher latitude should sit toward the top of the box.
            val y = offsetY + (drawnH - ((lat - minLat) * scale).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Endpoint dots anchor the path so a short trip still reads as a route.
        val first = points.first(); val last = points.last()
        drawCircle(
            color = color,
            radius = 2.dp.toPx(),
            center = Offset(
                offsetX + ((first.second - minLng) * scale).toFloat(),
                offsetY + (drawnH - ((first.first - minLat) * scale).toFloat())
            )
        )
        drawCircle(
            color = VoyagerColors.OnSurface,
            radius = 2.dp.toPx(),
            center = Offset(
                offsetX + ((last.second - minLng) * scale).toFloat(),
                offsetY + (drawnH - ((last.first - minLat) * scale).toFloat())
            )
        )
    }
}
