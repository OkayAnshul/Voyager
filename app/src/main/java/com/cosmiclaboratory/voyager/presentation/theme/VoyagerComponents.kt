package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall

private val CardShape = RoundedCornerShape(12.dp)

// ── Card Elevation Variants ──────────────────────────────────────────────────

enum class CardVariant {
    /** Default — flat card with subtle border */
    FLAT,
    /** Raised — SurfaceVariant background for emphasis */
    RAISED,
    /** Highlighted — primary border glow for selected/active items */
    HIGHLIGHTED
}

@Composable
fun VoyagerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    padding: Dp = 16.dp,
    variant: CardVariant = CardVariant.FLAT,
    tintColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .alpha(if (enabled) 1f else 0.5f)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)

    val baseColor = when (variant) {
        CardVariant.FLAT -> VoyagerColors.Surface
        CardVariant.RAISED -> VoyagerColors.SurfaceVariant
        CardVariant.HIGHLIGHTED -> VoyagerColors.Surface
    }
    val containerColor = if (tintColor != null) tintColor.compositeOver(baseColor) else baseColor

    val border = when (variant) {
        CardVariant.FLAT -> BorderStroke(1.dp, VoyagerColors.PrimaryDim.copy(alpha = 0.3f))
        CardVariant.RAISED -> BorderStroke(1.dp, VoyagerColors.SurfaceVariant)
        CardVariant.HIGHLIGHTED -> BorderStroke(1.5.dp, VoyagerColors.Primary.copy(alpha = 0.6f))
    }

    OutlinedCard(
        modifier = cardModifier,
        shape = CardShape,
        border = border,
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}

@Composable
fun VoyagerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = VoyagerColors.PrimaryContainer,
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

@Composable
fun VoyagerOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, VoyagerColors.Primary.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

@Composable
fun VoyagerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

@Composable
fun VoyagerTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

/** Pulsing dot indicator for live status */
@Composable
fun PulsingDot(
    size: Dp = 12.dp,
    color: Color = VoyagerColors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

/** Collapsible section with chevron indicator */
@Composable
fun VoyagerCollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.Primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trailingAction?.invoke()
                Text(
                    text = if (isExpanded) "▾" else "▸",
                    color = VoyagerColors.PrimaryDim
                )
            }
        }
        if (isExpanded) {
            content()
        }
    }
}

/** Small badge for counts or labels — now with color variants */
@Composable
fun VoyagerBadge(
    text: String,
    color: Color = VoyagerColors.PrimaryContainer,
    contentColor: Color = VoyagerColors.Primary
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Section header — Title Case, Inter font, optional trailing action */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoyagerColors.Primary
        )
        trailingAction?.invoke()
    }
}

/** Loading animation — kept for backward compatibility, prefer ShimmerPlaceholder */
@Composable
fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )
    Text(
        text = "Loading" + ".".repeat(dotCount.toInt().coerceIn(0, 3)),
        style = MaterialTheme.typography.bodyMedium,
        color = VoyagerColors.OnSurfaceVariant
    )
}

/** Stat item — JetBrains Mono for value, Inter for label */
@Composable
fun StatItem(
    value: String,
    label: String,
    valueColor: Color = VoyagerColors.Primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MonoStatMedium,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}
