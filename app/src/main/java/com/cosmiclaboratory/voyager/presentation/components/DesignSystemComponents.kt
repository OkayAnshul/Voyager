package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.ui.theme.MonoStatLarge
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall
import com.cosmiclaboratory.voyager.ui.theme.MonoTimestamp

// ============================================================================
// UI-0.04: ConfidenceBar — color-gradient bar with percentage + source
// ============================================================================

@Composable
fun ConfidenceBar(
    confidence: Float,
    source: String? = null,
    modifier: Modifier = Modifier
) {
    val color = when {
        confidence >= 0.7f -> VoyagerColors.ConfidenceHigh
        confidence >= 0.4f -> VoyagerColors.ConfidenceMedium
        else -> VoyagerColors.ConfidenceLow
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(confidence * 100).toInt()}%",
                style = MonoStatSmall,
                color = color
            )
            source?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(VoyagerColors.SurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(confidence.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

// ============================================================================
// UI-0.05: NameSourceIndicator — shows name resolution source per §6.4
// ============================================================================

@Composable
fun NameSourceIndicator(
    nameSource: String,
    modifier: Modifier = Modifier
) {
    val (displayText, displayColor) = when {
        nameSource.equals("custom", ignoreCase = true) ||
            nameSource.contains("user", ignoreCase = true) ->
            "Custom name" to VoyagerColors.AccentPurple
        nameSource.contains("photon", ignoreCase = true) ->
            "via Photon" to VoyagerColors.AccentBlue
        nameSource.contains("nominatim", ignoreCase = true) ->
            "via Nominatim" to VoyagerColors.AccentBlue
        nameSource.contains("geocode", ignoreCase = true) ->
            "via Geocoding" to VoyagerColors.AccentBlue
        nameSource.contains("infer", ignoreCase = true) ->
            "Inferred" to VoyagerColors.Warning
        nameSource.contains("coord", ignoreCase = true) ->
            "Coordinates" to VoyagerColors.OnSurfaceVariant
        nameSource.contains("provider", ignoreCase = true) ->
            "via Provider" to VoyagerColors.AccentBlue
        else -> nameSource to VoyagerColors.OnSurfaceVariant
    }

    Text(
        text = displayText,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = displayColor,
        fontWeight = FontWeight.Medium
    )
}

// ============================================================================
// UI-0.06: DataCard — large stat (mono) + label + trend arrow + sparkline
// ============================================================================

enum class TrendDirection { UP, DOWN, STABLE }

@Composable
fun DataCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trend: TrendDirection? = null,
    trendPercent: String? = null,
    sparklineData: List<Float>? = null,
    valueColor: Color = VoyagerColors.Primary,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else modifier

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.Surface,
        border = BorderStroke(1.dp, VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MonoStatMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.OnSurfaceVariant
            )
            if (trend != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TrendIndicator(trend = trend, percent = trendPercent)
            }
            if (sparklineData != null && sparklineData.size >= 2) {
                Spacer(modifier = Modifier.height(4.dp))
                SparklineChart(
                    data = sparklineData,
                    modifier = Modifier
                        .width(48.dp)
                        .height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendIndicator(
    trend: TrendDirection,
    percent: String? = null
) {
    val (icon, color) = when (trend) {
        TrendDirection.UP -> Icons.AutoMirrored.Filled.TrendingUp to VoyagerColors.AccentGreen
        TrendDirection.DOWN -> Icons.AutoMirrored.Filled.TrendingDown to VoyagerColors.AccentRed
        TrendDirection.STABLE -> Icons.Default.Remove to VoyagerColors.OnSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = trend.name,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        percent?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================================
// UI-0.10: CategoryChip — category icon + name in colored chip
// ============================================================================

@Composable
fun CategoryChip(
    categoryName: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val icon = categoryIcon(categoryName)
    val chipColor = VoyagerColors.PrimaryContainer

    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        color = chipColor,
        contentColor = VoyagerColors.Primary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun categoryIcon(category: String): ImageVector = when (category.uppercase()) {
    "HOME" -> Icons.Default.Home
    "WORK" -> Icons.Default.Work
    "GYM" -> Icons.Default.FitnessCenter
    "RESTAURANT" -> Icons.Default.Restaurant
    "SHOPPING" -> Icons.Default.ShoppingCart
    "ENTERTAINMENT" -> Icons.Default.TheaterComedy
    "HEALTHCARE" -> Icons.Default.LocalHospital
    "EDUCATION" -> Icons.Default.School
    "TRANSPORT" -> Icons.Default.DirectionsBus
    "TRAVEL" -> Icons.Default.Flight
    "OUTDOOR" -> Icons.Default.Park
    "SOCIAL" -> Icons.Default.People
    "SERVICES" -> Icons.Default.Build
    "TRANSIT_HUB" -> Icons.Default.Train
    else -> Icons.Default.Place
}

// ============================================================================
// UI-0.11: TransportModeIcon — icon per SegmentType with color
// ============================================================================

@Composable
fun TransportModeIcon(
    segmentType: SegmentType,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val (icon, color) = transportIconAndColor(segmentType)
    Icon(
        imageVector = icon,
        contentDescription = segmentType.name,
        tint = color,
        modifier = modifier.size(size)
    )
}

fun transportIconAndColor(segmentType: SegmentType): Pair<ImageVector, Color> = when (segmentType) {
    SegmentType.VISIT, SegmentType.DWELL -> Icons.Default.LocationOn to VoyagerColors.Primary
    SegmentType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk to VoyagerColors.TransportWalk
    SegmentType.RUN -> Icons.Default.DirectionsRun to VoyagerColors.TransportWalk
    SegmentType.CYCLE -> Icons.Default.DirectionsBike to VoyagerColors.TransportCycle
    SegmentType.DRIVE -> Icons.Default.DirectionsCar to VoyagerColors.TransportDrive
    SegmentType.TRANSIT -> Icons.Default.Train to VoyagerColors.TransportTransit
    SegmentType.GAP -> Icons.Default.SignalWifiOff to VoyagerColors.TransportGap
    SegmentType.UNKNOWN_MOTION -> Icons.Default.HelpOutline to VoyagerColors.OnSurfaceVariant
}

fun transportColor(segmentType: SegmentType): Color = transportIconAndColor(segmentType).second

// ============================================================================
// UI-0.12: GapSegmentCard — dashed card with gapReason display
// ============================================================================

@Composable
fun GapSegmentCard(
    gapReason: String?,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val (reasonText, reasonIcon) = gapReasonDisplay(gapReason)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = VoyagerColors.TransportGap.copy(alpha = 0.4f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 4.dp.toPx())
                        )
                    )
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.Surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = reasonIcon,
                contentDescription = null,
                tint = VoyagerColors.TransportGap,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Text(
                text = formatDurationCompact(durationMs),
                style = MonoTimestamp,
                color = VoyagerColors.TransportGap
            )
        }
    }
}

private fun gapReasonDisplay(reason: String?): Pair<String, ImageVector> = when (reason?.uppercase()) {
    "PERMISSION" -> "Location permission lost" to Icons.Default.GppBad
    "DOZE" -> "Device in deep sleep" to Icons.Default.BedtimeOff
    "DORMANT" -> "Phone stationary (GPS off)" to Icons.Default.NightsStay
    "GPS_LOSS" -> "No GPS signal received" to Icons.Default.SignalWifiOff
    "PROCESS_DEAD" -> "Voyager was stopped by system" to Icons.Default.RestartAlt
    "MANUAL_PAUSE" -> "Tracking paused" to Icons.Default.PauseCircle
    "UNKNOWN", null -> "Unknown gap" to Icons.Default.HelpOutline
    else -> (reason ?: "Gap") to Icons.Default.HelpOutline
}

// ============================================================================
// UI-0.13: TrackingStatusPill — compact status indicator
// ============================================================================

enum class TrackingStatus {
    ACTIVE, PAUSED, DEGRADED
}

@Composable
fun TrackingStatusPill(
    status: TrackingStatus,
    transportMode: String? = null,
    degradedReason: String? = null,
    modifier: Modifier = Modifier
) {
    val (text, bgColor, textColor) = when (status) {
        TrackingStatus.ACTIVE -> Triple(
            "Tracking: ${transportMode ?: "Active"}",
            VoyagerColors.AccentGreen.copy(alpha = 0.15f),
            VoyagerColors.AccentGreen
        )
        TrackingStatus.PAUSED -> Triple(
            "Paused",
            VoyagerColors.OnSurfaceVariant.copy(alpha = 0.15f),
            VoyagerColors.OnSurfaceVariant
        )
        TrackingStatus.DEGRADED -> Triple(
            "Degraded: ${degradedReason ?: "Unknown"}",
            VoyagerColors.Warning.copy(alpha = 0.15f),
            VoyagerColors.Warning
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (status == TrackingStatus.ACTIVE) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(textColor, CircleShape)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

// ============================================================================
// UI-0.14: PermissionBanner — contextual degradation banner per §5.4
// ============================================================================

enum class PermissionDegradation {
    COARSE_LOCATION,
    NO_LOCATION,
    BACKGROUND_RESTRICTED,
    NO_LOCATION_NO_AR
}

@Composable
fun PermissionBanner(
    degradation: PermissionDegradation,
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (message, severity) = when (degradation) {
        PermissionDegradation.COARSE_LOCATION ->
            "Location accuracy reduced" to VoyagerColors.Warning
        PermissionDegradation.NO_LOCATION ->
            "Location paused — activity logging continues" to VoyagerColors.AccentRed
        PermissionDegradation.BACKGROUND_RESTRICTED ->
            "Continuous tracking unavailable" to VoyagerColors.Warning
        PermissionDegradation.NO_LOCATION_NO_AR ->
            "Tracking paused — restore permissions to continue" to VoyagerColors.AccentRed
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = severity.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = severity,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = severity,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onFix,
                colors = ButtonDefaults.textButtonColors(contentColor = severity)
            ) {
                Text("Fix", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// UI-0.15: SegmentedProgressBar — multi-color time distribution bar
// ============================================================================

data class ProgressSegment(
    val fraction: Float,
    val color: Color,
    val label: String? = null
)

@Composable
fun SegmentedProgressBar(
    segments: List<ProgressSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    if (segments.isEmpty()) return

    val totalFraction = segments.sumOf { it.fraction.toDouble() }.toFloat()
    if (totalFraction <= 0f) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(VoyagerColors.SurfaceVariant)
    ) {
        segments.forEach { segment ->
            val normalizedFraction = segment.fraction / totalFraction
            if (normalizedFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(normalizedFraction)
                        .fillMaxHeight()
                        .background(segment.color)
                )
            }
        }
    }
}

// ============================================================================
// UI-0.16: SparklineChart — tiny inline trend chart (48x24dp)
// ============================================================================

@Composable
fun SparklineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = VoyagerColors.Primary
) {
    if (data.size < 2) return
    val minVal = data.min()
    val maxVal = data.max()
    val range = (maxVal - minVal).coerceAtLeast(0.001f)

    Canvas(modifier = modifier) {
        val stepX = size.width / (data.size - 1)
        val path = Path().apply {
            data.forEachIndexed { i, value ->
                val x = i * stepX
                val y = size.height - ((value - minVal) / range) * size.height
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ============================================================================
// UI-0.17: ActivityRings — three concentric progress rings
// ============================================================================

@Composable
fun ActivityRings(
    distanceProgress: Float,
    stepsProgress: Float,
    activeTimeProgress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val strokeWidth = this.size.minDimension * 0.1f
        val gap = strokeWidth * 0.4f

        val rings = listOf(
            Triple(distanceProgress, VoyagerColors.Primary, this.size.minDimension / 2 - strokeWidth / 2),
            Triple(stepsProgress, VoyagerColors.AccentBlue, this.size.minDimension / 2 - strokeWidth * 1.5f - gap),
            Triple(activeTimeProgress, VoyagerColors.AccentGreen, this.size.minDimension / 2 - strokeWidth * 2.5f - gap * 2)
        )

        rings.forEach { (progress, color, radius) ->
            // Background ring
            drawCircle(
                color = color.copy(alpha = 0.15f),
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Progress arc
            val sweepAngle = (progress.coerceIn(0f, 1f) * 360f)
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

// ============================================================================
// UI-0.18: PresetCard — preset name + behavioral summary
// ============================================================================

@Composable
fun PresetCard(
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTraveler: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) VoyagerColors.PrimaryContainer else VoyagerColors.Surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) VoyagerColors.Primary else VoyagerColors.PrimaryDim.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isTraveler) Icons.Default.Flight else Icons.Default.Settings,
                contentDescription = null,
                tint = if (isSelected) VoyagerColors.Primary else VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) VoyagerColors.Primary else VoyagerColors.OnSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================================
// UI-0.19: AnomalyCard — severity color + sigma + explanation
// ============================================================================

@Composable
fun AnomalyAlertCard(
    metricKey: String,
    humanExplanation: String,
    severity: String,
    deviationSigma: Double,
    impactedDay: String? = null,
    modifier: Modifier = Modifier
) {
    val severityColor = when (severity.uppercase()) {
        "HIGH", "SIGNIFICANT" -> VoyagerColors.SeverityHigh
        "MEDIUM", "NOTABLE" -> VoyagerColors.SeverityMedium
        "LOW", "MILD" -> VoyagerColors.SeverityLow
        "INFO" -> VoyagerColors.SeverityInfo
        else -> VoyagerColors.OnSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.Surface,
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(severityColor, CircleShape)
                )
                Text(
                    text = metricKey,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${String.format("%.1f", deviationSigma)}\u03C3",
                    style = MonoStatSmall,
                    color = severityColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = humanExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurface
            )
            impactedDay?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// UI-0.20: PlaceConfirmationPrompt — "Is this right?" for low-confidence places
// ============================================================================

@Composable
fun PlaceConfirmationPrompt(
    placeName: String,
    confidence: Float,
    suggestions: List<String> = emptyList(),
    onConfirm: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.Warning.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, VoyagerColors.Warning.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = VoyagerColors.Warning,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Is this right?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.Warning
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"$placeName\" (${(confidence * 100).toInt()}% confidence)",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurface
            )
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Suggestions: ${suggestions.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VoyagerColors.AccentGreen.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VoyagerColors.AccentGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Confirm", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onRename,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VoyagerColors.Primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VoyagerColors.Primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Rename", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ============================================================================
// UI-0.21: ShimmerPlaceholder — skeleton loading
// ============================================================================

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmerColors = listOf(
        VoyagerColors.SurfaceVariant.copy(alpha = 0.6f),
        VoyagerColors.SurfaceBright.copy(alpha = 0.4f),
        VoyagerColors.SurfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(VoyagerColors.SurfaceVariant)
            .drawBehind {
                val shimmerX = translateAnim - size.width
                drawRect(
                    color = VoyagerColors.SurfaceBright.copy(alpha = 0.3f),
                    topLeft = Offset(shimmerX, 0f),
                    size = Size(size.width * 0.4f, size.height)
                )
            }
    )
}

/** Shimmer card placeholder matching VoyagerCard dimensions */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp
) {
    ShimmerPlaceholder(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(12.dp)
    )
}

// ============================================================================
// UI-0.03: EvidenceCard — displays InferenceExplanation
// ============================================================================

@Composable
fun EvidenceCard(
    label: String,
    confidence: Float,
    humanExplanation: String,
    supportingMetrics: Map<String, Any> = emptyMap(),
    counterEvidence: List<String> = emptyList(),
    sourceSet: Set<String> = emptySet(),
    ruleVersion: String? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.SurfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: label + confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(confidence * 100).toInt()}%",
                    style = MonoStatSmall,
                    color = when {
                        confidence >= 0.7f -> VoyagerColors.ConfidenceHigh
                        confidence >= 0.4f -> VoyagerColors.ConfidenceMedium
                        else -> VoyagerColors.ConfidenceLow
                    }
                )
            }

            // Confidence bar
            Spacer(modifier = Modifier.height(6.dp))
            ConfidenceBar(confidence = confidence)

            // Human explanation
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = humanExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurface
            )

            // Source chips
            if (sourceSet.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sourceSet.forEach { source ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VoyagerColors.PrimaryContainer
                        ) {
                            Text(
                                text = source,
                                style = MaterialTheme.typography.labelSmall,
                                color = VoyagerColors.Primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Expandable details
            if (supportingMetrics.isNotEmpty() || counterEvidence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = VoyagerColors.PrimaryDim
                    )
                ) {
                    Text(
                        text = if (isExpanded) "Hide details" else "Show details",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (isExpanded) {
                    // Supporting metrics
                    if (supportingMetrics.isNotEmpty()) {
                        Text(
                            text = "Supporting Metrics",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VoyagerColors.Primary
                        )
                        supportingMetrics.forEach { (key, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VoyagerColors.OnSurfaceVariant
                                )
                                Text(
                                    text = value.toString(),
                                    style = MonoTimestamp,
                                    color = VoyagerColors.OnSurface
                                )
                            }
                        }
                    }

                    // Counter evidence
                    if (counterEvidence.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Counter Evidence",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VoyagerColors.Warning
                        )
                        counterEvidence.forEach { evidence ->
                            Text(
                                text = "- $evidence",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }

                    // Rule version
                    ruleVersion?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rule: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoyagerColors.OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// UI-0.08: DayNavigator — horizontal date strip
// ============================================================================

@Composable
fun DayNavigator(
    dayLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    summaryText: String? = null,
    onTodayClick: (() -> Unit)? = null,
    isToday: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoyagerColors.Surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous day",
                        tint = VoyagerColors.Primary
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VoyagerColors.Primary
                    )
                    if (!isToday && onTodayClick != null) {
                        TextButton(
                            onClick = onTodayClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoyagerColors.PrimaryDim
                            )
                        }
                    }
                }

                IconButton(onClick = onNext, enabled = !isToday) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next day",
                        tint = if (isToday) VoyagerColors.OnSurfaceVariant.copy(alpha = 0.3f)
                        else VoyagerColors.Primary
                    )
                }
            }
            if (summaryText != null) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .padding(bottom = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ============================================================================
// Utility
// ============================================================================

fun formatDurationCompact(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
