package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.ui.theme.*
import kotlin.math.sin

/**
 * Voyager Components Library
 *
 * Professional map-inspired UI components for the Voyager Map theme.
 * Features:
 * - Rounded 12dp corners (modern, friendly)
 * - Teal accents and borders
 * - Dark blue backgrounds
 * - Minimal elevation (rely on borders)
 * - Clean, accessible aesthetic
 *
 * Replaces GlassmorphismComponents.kt
 */

// ============================================================================
// CARD COMPONENTS
// ============================================================================

/**
 * MatrixCard - Primary container for grouped content
 *
 * Sharp-cornered card with green border, black background.
 * Use for grouping related information or interactive sections.
 *
 * @param modifier Modifier to apply to the card
 * @param onClick Optional click handler (makes card interactive)
 * @param enabled Whether card is enabled (affects opacity)
 * @param content Card content in a Column scope
 */
@Composable
fun MatrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(12.dp), // Sharp corners for technical look
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp // Minimal shadow, rely on borders
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * MatrixCard with custom padding
 *
 * Use when you need different padding than the default 16.dp
 */
@Composable
fun MatrixCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}

// ============================================================================
// BUTTON COMPONENTS
// ============================================================================

/**
 * MatrixButton - Primary action button
 *
 * Transparent background with green border (outlined style).
 * Green text for terminal aesthetic.
 *
 * @param onClick Click handler
 * @param modifier Modifier to apply
 * @param enabled Whether button is enabled
 * @param content Button content (text, icons, etc.)
 */
@Composable
fun MatrixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        content = content
    )
}

/**
 * MatrixFilledButton - Filled action button
 *
 * Green background with black text (high emphasis).
 * Use sparingly for primary actions.
 */
@Composable
fun MatrixFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}

/**
 * MatrixTextButton - Text-only button
 *
 * No background, no border. Just green text.
 * Use for low-emphasis actions.
 */
@Composable
fun MatrixTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        content = content
    )
}

/**
 * MatrixIconButton - Icon-only button
 *
 * Square button with green border, transparent background.
 * Use for icon-based actions (refresh, settings, etc.)
 */
@Composable
fun MatrixIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.size(48.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

// ============================================================================
// INPUT COMPONENTS
// ============================================================================

/**
 * MatrixTextField - Terminal-style text input
 *
 * Green text on transparent background with green border.
 * Monospace font for consistency with theme.
 *
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param modifier Modifier to apply
 * @param placeholder Placeholder text
 * @param leadingIcon Optional leading icon
 * @param trailingIcon Optional trailing icon
 * @param singleLine Whether to limit to single line
 * @param enabled Whether field is enabled
 * @param isError Whether field shows error state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.primary,
            unfocusedTextColor = MaterialTheme.colorScheme.primary,
            disabledTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            cursorColor = MaterialTheme.colorScheme.primary,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorCursorColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

// ============================================================================
// CHIP COMPONENTS
// ============================================================================

/**
 * MatrixChip - Category/tag chip
 *
 * Small rectangular chip with green border.
 * Use for categories, tags, or filter options.
 *
 * @param label Chip text (will be uppercased)
 * @param selected Whether chip is selected
 * @param onClick Optional click handler
 * @param modifier Modifier to apply
 */
@Composable
fun MatrixChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ============================================================================
// DIVIDER COMPONENTS
// ============================================================================

/**
 * MatrixDivider - Subtle green horizontal line
 *
 * Use to separate sections or create visual hierarchy.
 */
@Composable
fun MatrixDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

/**
 * MatrixVerticalDivider - Subtle green vertical line
 */
@Composable
fun MatrixVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline
) {
    VerticalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

// ============================================================================
// ANIMATED COMPONENTS
// ============================================================================

/**
 * PulsingDot - Animated pulsing green dot
 *
 * Use for live status indicators.
 * Pulses between 50% and 100% opacity with sine wave animation.
 *
 * @param modifier Modifier to apply
 * @param size Size of the dot
 * @param color Color of the dot (default: Matrix green)
 */
@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    color: Color = Teal
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

/**
 * LoadingDots - Three pulsing dots (loading indicator)
 *
 * Use for loading states. Dots pulse in sequence.
 */
@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    color: Color = Teal
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "loading_$index")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(alpha)
                    .background(color, CircleShape)
            )
        }
    }
}

// ============================================================================
// LAYOUT COMPONENTS
// ============================================================================

/**
 * CollapsibleSection - Expandable/collapsible section
 *
 * Use for organizing content into collapsible sections.
 * Click header to toggle visibility of content.
 *
 * @param title Section title
 * @param isExpanded Whether section is currently expanded
 * @param onToggle Callback when section is toggled
 * @param content Content to show when expanded
 */
@Composable
fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        // Header (always visible)
        MatrixCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggle
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Content (collapsible)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                content()
            }
        }
    }
}

// ============================================================================
// SPECIAL COMPONENTS
// ============================================================================

/**
 * MatrixBadge - Small numeric or text badge
 *
 * Use for notification counts, status indicators.
 *
 * @param count Badge text (e.g., notification count)
 * @param modifier Modifier to apply
 */
@Composable
fun MatrixBadge(
    count: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text = count,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * EmptyStateMessage - Empty state with icon and message
 *
 * Use when lists or screens have no data to display.
 *
 * @param icon Icon composable
 * @param title Empty state title
 * @param message Optional description
 * @param actionButton Optional action button
 */
@Composable
fun EmptyStateMessage(
    icon: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        icon()

        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        actionButton?.invoke()
    }
}

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

/**
 * MatrixSectionHeader - Section header with divider
 *
 * Use to create clear sections in screens.
 *
 * @param title Section title
 * @param modifier Modifier to apply
 * @param action Optional trailing action (e.g., "See all" button)
 */
@Composable
fun MatrixSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            action?.invoke()
        }

        Spacer(modifier = Modifier.height(8.dp))
        MatrixDivider()
    }
}
