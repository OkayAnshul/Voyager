package com.cosmiclaboratory.voyager.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// MAP THEME PALETTE - Professional Dark Blue/Teal
// ============================================================================

/** Dark blue background - modern map app aesthetic */
val MapBlue = Color(0xFF0A1929)

/** Darker blue surface - for cards and elevated components */
val MapBlueDark = Color(0xFF0D1F2D)

/** Teal primary accent - for highlights and interactive elements */
val Teal = Color(0xFF00BCD4)

/** Darker teal - for pressed states and darker accents */
val TealDark = Color(0xFF00ACC1)

/** Light blue secondary - for secondary actions */
val LightBlue = Color(0xFF1976D2)

/** Dimmed teal - for borders and subtle highlights (30% opacity) */
val TealDim = Color(0xFF00BCD4).copy(alpha = 0.3f)

// ============================================================================
// LEGACY MATRIX COLORS - Kept for reference
// ============================================================================

/** Primary Matrix green - bright terminal green for text and highlights */
val MatrixGreen = Color(0xFF00FF41)

/** Darker Matrix green - for secondary elements and accents */
val MatrixGreenDark = Color(0xFF00AA2B)

/** Dimmed Matrix green - for borders and subtle highlights (30% opacity) */
val MatrixGreenDim = Color(0xFF00FF41).copy(alpha = 0.3f)

/** Pure black background - OLED optimized */
val MatrixBlack = Color(0xFF000000)

/** Dark gray surface - for cards and elevated components */
val MatrixGray = Color(0xFF1A1A1A)

/** Darker gray - for elevated surfaces */
val MatrixDarkGray = Color(0xFF0D0D0D)

// ============================================================================
// SEMANTIC COLORS - Status Indicators
// ============================================================================

/** Success state color */
val MatrixSuccess = MatrixGreen

/** Warning state color */
val MatrixWarning = Color(0xFFFFAA00)

/** Error state color */
val MatrixError = Color(0xFFFF0000)

/** Info state color */
val MatrixInfo = MatrixGreenDark

// ============================================================================
// LEGACY COLORS - Kept for gradual migration and rollback
// ============================================================================

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)