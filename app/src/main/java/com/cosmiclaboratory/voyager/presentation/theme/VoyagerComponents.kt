package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmiclaboratory.voyager.ui.theme.InterFontFamily
import com.cosmiclaboratory.voyager.ui.theme.JetBrainsMonoFontFamily
import com.cosmiclaboratory.voyager.ui.theme.MonoStatMedium

// ── Card Elevation Variants ──────────────────────────────────────────────────

enum class CardVariant {
    /** Default — flat solid card with a subtle hairline border */
    FLAT,
    /** Highlighted — faint teal hairline for selected/active items (otherwise identical to FLAT) */
    HIGHLIGHTED,
    /** Glass — retained as a flat alias of [FLAT]; no frosted treatment (see VoyagerCard) */
    GLASS
}

/**
 * Voyager's foundational surface. Every card is a uniform flat solid dark surface
 * with a 1dp hairline and no drop shadow — the variants differ only by border tint.
 * Press feedback and the [tint] wash are the only accents. Every screen that uses
 * [VoyagerCard] inherits this look automatically.
 *
 * @param tintColor legacy solid tint composited over the base (kept for compat).
 * @param tint optional [Brush] wash painted over the surface (hero/active/premium).
 */
@Composable
fun VoyagerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    padding: Dp = VoyagerSpacing.lg,
    variant: CardVariant = CardVariant.FLAT,
    tintColor: Color? = null,
    tint: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // All card variants share one solid surface + radius — uniform "look & feel".
    // (GLASS kept as an alias of the flat look; no frosted treatment.)
    val shape: Shape = VoyagerShapes.card

    val baseColor = VoyagerColors.Surface
    val containerColor = if (tintColor != null) tintColor.compositeOver(baseColor) else baseColor

    // Uniform 1dp hairline on every card. HIGHLIGHTED keeps a faint solid teal
    // border as a subtle "active/selected" cue — NOT the multicolor aurora.
    val borderWidth = 1.dp
    val borderBrush: Brush = when (variant) {
        CardVariant.HIGHLIGHTED -> SolidColor(VoyagerColors.Primary.copy(alpha = 0.45f))
        CardVariant.FLAT, CardVariant.GLASS -> SolidColor(VoyagerColors.PrimaryDim.copy(alpha = 0.30f))
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // No card carries a drop shadow — keeps the flat, uniform surface across the app.
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(pressed)
            .clip(shape)
            .background(containerColor, shape)
            .then(if (tint != null) Modifier.background(tint, shape) else Modifier)
            .border(BorderStroke(borderWidth, borderBrush), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}

/** Convenience for the frosted-glass surface. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    padding: Dp = VoyagerSpacing.lg,
    tint: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) = VoyagerCard(
    modifier = modifier,
    onClick = onClick,
    enabled = enabled,
    padding = padding,
    variant = CardVariant.GLASS,
    tint = tint,
    content = content
)

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
        shape = VoyagerShapes.button,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = VoyagerColors.PrimaryContainer,
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

/** Primary call-to-action — a clean, solid brand fill (no gradient). */
@Composable
fun VoyagerPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = SolidColor(VoyagerColors.Primary),
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(pressed)
            .clip(VoyagerShapes.button)
            .background(brush, VoyagerShapes.button)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = VoyagerSpacing.xl, vertical = VoyagerSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides VoyagerColors.OnPrimary) {
            content()
        }
    }
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
        shape = VoyagerShapes.button,
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

/** Pulsing dot indicator for live status. Static under reduce-motion. */
@Composable
fun PulsingDot(
    size: Dp = 12.dp,
    color: Color = VoyagerColors.Primary
) {
    val reduce = LocalReduceMotion.current
    val alpha = if (reduce) {
        1f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(VoyagerDurations.pulse / 2),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        ).value
    }
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape)
            .semantics { /* decorative live indicator */ }
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
                .padding(vertical = VoyagerSpacing.sm),
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
                horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)
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

/** Visual styles for [VoyagerBadge]. */
enum class BadgeStyle { FILLED, OUTLINE }

/**
 * Small pill badge for counts, labels and status. Now stadium-shaped with
 * filled/outline styles, an optional leading icon, and a gold Pro affordance.
 */
@Composable
fun VoyagerBadge(
    text: String,
    color: Color = VoyagerColors.PrimaryContainer,
    contentColor: Color = VoyagerColors.Primary,
    style: BadgeStyle = BadgeStyle.FILLED,
    icon: ImageVector? = null
) {
    val shape = VoyagerShapes.pill
    val container = if (style == BadgeStyle.FILLED) color else color.copy(alpha = 0.14f)
    val border = if (style == BadgeStyle.OUTLINE) BorderStroke(1.dp, contentColor.copy(alpha = 0.6f)) else null
    Surface(
        shape = shape,
        color = container,
        contentColor = contentColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VoyagerSpacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Pro / Premium gold badge. */
@Composable
fun ProBadge(text: String = "PRO", icon: ImageVector? = null) =
    VoyagerBadge(
        text = text,
        color = VoyagerColors.Premium,
        contentColor = VoyagerColors.Premium,
        style = BadgeStyle.OUTLINE,
        icon = icon
    )

/** Curated accent palette for section kickers — rotated per section so the app reads
 *  as a lively, multi-colour type system rather than one flat blue. */
val SectionAccents: List<Color> = listOf(
    VoyagerColors.Primary,
    VoyagerColors.AccentPurple,
    VoyagerColors.AccentAmber,
    VoyagerColors.AccentGreen,
    VoyagerColors.AccentBlue
)

/** Deterministic accent for a section title, so a given section keeps one stable colour. */
fun sectionAccentFor(title: String): Color =
    SectionAccents[(title.hashCode() and 0x7fffffff) % SectionAccents.size]

/**
 * Section header — a JetBrains-Mono uppercase "kicker" in an accent colour. When [accent]
 * is null the colour is picked deterministically from [SectionAccents] by title, so every
 * screen gets varied colours automatically; pass an explicit [accent] to override.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VoyagerSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            color = accent ?: sectionAccentFor(title),
            maxLines = 1
        )
        trailingAction?.invoke()
    }
}

/** Aurora-tinted hairline section divider. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(VoyagerGradients.sectionDivider)
    )
}

/** Loading animation — kept for backward compatibility, prefer VoyagerShimmer */
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

/**
 * The Voyager wordmark — uppercase, letter-spaced Inter. Used in the top bar (default
 * size) and, larger, on the splash. Size/tracking are parameterised so every surface
 * renders the exact same wordmark identity, never a bespoke re-styling.
 */
@Composable
fun VoyagerWordmark(
    modifier: Modifier = Modifier,
    color: Color = VoyagerColors.OnSurface,
    fontSize: TextUnit = 20.sp,
    letterSpacing: TextUnit = 3.sp
) {
    Text(
        text = "VOYAGER",
        modifier = modifier,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        color = color
    )
}

/** UPPERCASE, letter-spaced micro section-label (the reference's "eyebrow"). */
@Composable
fun VoyagerEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VoyagerColors.Primary
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        color = color
    )
}
