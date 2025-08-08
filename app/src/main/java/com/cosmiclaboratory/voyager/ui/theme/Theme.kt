package com.cosmiclaboratory.voyager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

// ============================================================================
// VOYAGER DARK COLOR SCHEME - Primary Theme (VoyagerColors)
// ============================================================================

/**
 * Voyager Dark Color Scheme
 *
 * Polished dark palette using VoyagerColors:
 * - Near-black background with blue tint for modern aesthetic
 * - Vivid blue (#3B82F6) for interactive elements and highlights
 * - Dark blue-grey surfaces for cards and elevation
 * - Deep blue borders and accents
 */
private val VoyagerDarkColorScheme = darkColorScheme(
    primary = VoyagerColors.Primary,
    onPrimary = VoyagerColors.OnPrimary,
    primaryContainer = VoyagerColors.PrimaryContainer,
    onPrimaryContainer = VoyagerColors.Primary,
    secondary = VoyagerColors.PrimaryDim,
    onSecondary = VoyagerColors.OnPrimary,
    secondaryContainer = VoyagerColors.SurfaceVariant,
    onSecondaryContainer = VoyagerColors.Primary,
    tertiary = VoyagerColors.PrimaryDim,
    onTertiary = VoyagerColors.OnPrimary,
    tertiaryContainer = VoyagerColors.SurfaceVariant,
    onTertiaryContainer = VoyagerColors.Primary,
    background = VoyagerColors.Background,
    onBackground = VoyagerColors.OnSurface,
    surface = VoyagerColors.Surface,
    onSurface = VoyagerColors.OnSurface,
    surfaceVariant = VoyagerColors.SurfaceVariant,
    onSurfaceVariant = VoyagerColors.OnSurfaceVariant,
    surfaceTint = VoyagerColors.Primary,
    inverseSurface = VoyagerColors.Primary,
    inverseOnSurface = VoyagerColors.Background,
    inversePrimary = VoyagerColors.PrimaryDim,
    outline = VoyagerColors.PrimaryDim,
    outlineVariant = VoyagerColors.PrimaryDim.copy(alpha = 0.2f),
    scrim = VoyagerColors.Background.copy(alpha = 0.8f),
    error = VoyagerColors.Error,
    onError = VoyagerColors.OnPrimary,
    errorContainer = VoyagerColors.ErrorContainer,
    onErrorContainer = VoyagerColors.Error
)

// ============================================================================
// LEGACY MATRIX COLOR SCHEME - Kept for reference
// ============================================================================

/**
 * Matrix Dark Color Scheme (LEGACY)
 *
 * Black/Green cyberpunk aesthetic with:
 * - Pure black background (#000000) for OLED optimization
 * - Matrix green (#00FF41) for all text and interactive elements
 * - Dark gray surfaces for cards and elevation
 * - Green borders and highlights
 */
private val MatrixDarkColorScheme = darkColorScheme(
    // Primary colors - Matrix green
    primary = MatrixGreen,
    onPrimary = MatrixBlack,
    primaryContainer = MatrixGreenDark,
    onPrimaryContainer = MatrixGreen,

    // Secondary colors - Darker green accents
    secondary = MatrixGreenDark,
    onSecondary = MatrixBlack,
    secondaryContainer = MatrixGray,
    onSecondaryContainer = MatrixGreen,

    // Tertiary colors - Dimmed green
    tertiary = MatrixGreenDim,
    onTertiary = MatrixBlack,
    tertiaryContainer = MatrixDarkGray,
    onTertiaryContainer = MatrixGreen,

    // Background colors - Pure black
    background = MatrixBlack,
    onBackground = MatrixGreen,

    // Surface colors - Dark gray for cards
    surface = MatrixGray,
    onSurface = MatrixGreen,
    surfaceVariant = MatrixDarkGray,
    onSurfaceVariant = MatrixGreenDim,
    surfaceTint = MatrixGreen,

    // Inverse colors
    inverseSurface = MatrixGreen,
    inverseOnSurface = MatrixBlack,
    inversePrimary = MatrixGreenDark,

    // Outline colors - Borders and dividers
    outline = MatrixGreenDim,
    outlineVariant = MatrixGreenDim.copy(alpha = 0.2f),

    // Scrim for modals
    scrim = MatrixBlack.copy(alpha = 0.8f),

    // Error colors
    error = MatrixError,
    onError = MatrixBlack,
    errorContainer = MatrixError.copy(alpha = 0.2f),
    onErrorContainer = MatrixError
)

// ============================================================================
// LEGACY COLOR SCHEMES - Kept for rollback
// ============================================================================

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// ============================================================================
// VOYAGER THEME - Map Dark Mode (Always Dark)
// ============================================================================

/**
 * Voyager Theme - Professional Map Aesthetic
 *
 * Always uses dark mode with blue on dark navy.
 * No light mode or dynamic color support (by design).
 *
 * @param darkTheme Ignored - always dark (kept for API compatibility)
 * @param dynamicColor Ignored - always Map theme (kept for API compatibility)
 * @param useMapTheme Set to false for legacy Matrix theme (rollback)
 * @param content Composable content
 */
@Composable
fun VoyagerTheme(
    darkTheme: Boolean = true, // Ignored - always dark
    dynamicColor: Boolean = false, // Ignored - always Map theme
    useMapTheme: Boolean = true, // Set to false for rollback to Matrix
    content: @Composable () -> Unit
) {
    val colorScheme = if (useMapTheme) {
        VoyagerDarkColorScheme
    } else {
        // Rollback to legacy Matrix theme
        MatrixDarkColorScheme
    }

    val typography = if (useMapTheme) {
        VoyagerTypography // Dual-font: Inter (body) + JetBrains Mono (data)
    } else {
        Typography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}