package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism Components for Voyager App
 *
 * Modern glass-like UI components with:
 * - Semi-transparent backgrounds
 * - Subtle blur effects
 * - Light borders
 * - Elegant shadows
 *
 * Based on the glassmorphism design trend for a premium, modern look
 */

/**
 * Main glass card component with frosted glass effect
 *
 * @param modifier Modifier to apply to the card
 * @param shape Shape of the card (default: rounded corners)
 * @param backgroundColor Base background color with transparency
 * @param borderColor Color of the border
 * @param elevation Shadow elevation
 * @param onClick Optional click handler
 * @param content Card content
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    elevation: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Glass button with gradient overlay
 *
 * @param onClick Click handler
 * @param modifier Modifier to apply
 * @param enabled Whether button is enabled
 * @param colors Custom color scheme
 * @param content Button content
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.White.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        content = content
    )
}

/**
 * Glass chip for tags/categories
 *
 * @param label Chip text
 * @param modifier Modifier to apply
 * @param selected Whether chip is selected
 * @param onClick Optional click handler
 * @param leadingIcon Optional leading icon
 */
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val backgroundColor = if (selected) {
        Color.White.copy(alpha = 0.25f)
    } else {
        Color.White.copy(alpha = 0.1f)
    }

    val borderColor = if (selected) {
        Color.White.copy(alpha = 0.4f)
    } else {
        Color.White.copy(alpha = 0.2f)
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
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let {
                it()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Glass container with gradient background
 * Useful for section headers or highlighted content
 *
 * @param modifier Modifier to apply
 * @param gradient Custom gradient colors
 * @param content Container content
 */
@Composable
fun GlassGradientContainer(
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(
        Color(0xFFEC4899).copy(alpha = 0.3f), // Pink
        Color(0xFFF97316).copy(alpha = 0.3f)  // Orange
    ),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(gradient)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        content = content
    )
}

/**
 * Glass surface for larger sections (like screen backgrounds)
 *
 * @param modifier Modifier to apply
 * @param backgroundColor Background color with transparency
 * @param content Surface content
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.05f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        content = content
    )
}

/**
 * Glass divider with subtle transparency
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.15f),
    thickness: Dp = 1.dp
) {
    HorizontalDivider(
        modifier = modifier,
        color = color,
        thickness = thickness
    )
}

/**
 * Glass text field with frosted background
 *
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param modifier Modifier to apply
 * @param placeholder Placeholder text
 * @param leadingIcon Optional leading icon
 * @param trailingIcon Optional trailing icon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedBorderColor = Color.White.copy(alpha = 0.3f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

/**
 * Glass icon button with subtle background
 *
 * @param onClick Click handler
 * @param modifier Modifier to apply
 * @param enabled Whether button is enabled
 * @param content Icon content
 */
@Composable
fun GlassIconButton(
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
        color = Color.White.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
