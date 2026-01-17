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

// ============================================================================
// MAP DARK COLOR SCHEME - Primary Theme
// ============================================================================

/**
 * Map Dark Color Scheme
 *
 * Professional dark blue/teal aesthetic for map applications:
 * - Dark blue background (#0A1929) for modern map app feel
 * - Teal (#00BCD4) for all interactive elements and highlights
 * - Darker blue surfaces for cards and elevation
 * - Teal borders and accents
 */
private val MapDarkColorScheme = darkColorScheme(
    // Primary colors - Teal
    primary = Teal,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TealDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,

    // Secondary colors - Light blue accents
    secondary = LightBlue,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = MapBlueDark,
    onSecondaryContainer = Teal,

    // Tertiary colors - Dimmed teal
    tertiary = TealDim,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = MapBlue,
    onTertiaryContainer = Teal,

    // Background colors - Dark blue
    background = MapBlue,
    onBackground = androidx.compose.ui.graphics.Color.White,

    // Surface colors - Darker blue for cards
    surface = MapBlueDark,
    onSurface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = MapBlueDark,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0BEC5),
    surfaceTint = Teal,

    // Inverse colors
    inverseSurface = Teal,
    inverseOnSurface = MapBlue,
    inversePrimary = TealDark,

    // Outline colors - Borders and dividers
    outline = TealDim,
    outlineVariant = TealDim.copy(alpha = 0.2f),

    // Scrim for modals
    scrim = MapBlue.copy(alpha = 0.8f),

    // Error colors
    error = androidx.compose.ui.graphics.Color(0xFFEF5350),
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFEF5350).copy(alpha = 0.2f),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFEF5350)
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
 * Always uses dark mode with teal on dark blue.
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
        MapDarkColorScheme
    } else {
        // Rollback to legacy Matrix theme
        MatrixDarkColorScheme
    }

    val typography = if (useMapTheme) {
        MatrixTypography // Keep JetBrains Mono as requested
    } else {
        // Rollback to default typography
        Typography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}