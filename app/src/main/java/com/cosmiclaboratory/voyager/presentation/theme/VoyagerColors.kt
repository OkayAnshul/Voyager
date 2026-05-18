package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Voyager Design System — Full Color Palette
 *
 * Polished dark UI with blue identity. OLED-friendly backgrounds.
 * Transport mode, accent, severity, and surface colors for all screens.
 */
object VoyagerColors {
    // ── Primary ──────────────────────────────────────────────────────────
    val Primary = Color(0xFF3B82F6)           // Vivid blue
    val PrimaryDim = Color(0xFF2563EB)        // Deeper blue
    val PrimaryContainer = Color(0xFF1E3A5F)  // Dark navy container

    // ── Surfaces ─────────────────────────────────────────────────────────
    val Background = Color(0xFF0F0F1A)        // Screen background (OLED-friendly)
    val Surface = Color(0xFF1A1A2E)           // Cards
    val SurfaceVariant = Color(0xFF252540)    // Elevated cards
    val SurfaceBright = Color(0xFF2E2E4A)     // Elevated surface
    val SurfaceOverlay = Color(0xFF2A2A4A)    // Bottom sheets, dialogs

    // ── Text ─────────────────────────────────────────────────────────────
    val OnSurface = Color(0xFFE8E8F0)         // Off-white
    val OnSurfaceVariant = Color(0xFF8888A0)  // Muted grey
    val OnPrimary = Color(0xFFFFFFFF)         // White

    // ── Status ───────────────────────────────────────────────────────────
    val Error = Color(0xFFEF5350)             // Warm red
    val ErrorContainer = Color(0xFF3D1A1A)    // Dark red
    val Success = Color(0xFF66BB6A)           // Green
    val Warning = Color(0xFFFFA726)           // Amber

    // ── Accents ──────────────────────────────────────────────────────────
    val AccentBlue = Color(0xFF42A5F5)        // Movement, distance
    val AccentPurple = Color(0xFFAB47BC)      // Patterns, routines
    val AccentAmber = Color(0xFFFFA726)       // Warnings
    val AccentGreen = Color(0xFF66BB6A)       // Success, active tracking
    val AccentRed = Color(0xFFEF5350)         // Errors
    val AccentOrange = Color(0xFFFF7043)      // Transit

    // ── Premium ──────────────────────────────────────────────────────────
    // Reserved for Pro surfaces (paywall, Pro-only features, lock states).
    // Used sparingly so "Pro" reads as distinct, not just another accent.
    val Premium = Color(0xFFE6B450)           // Warm gold — Pro tier
    val PremiumDim = Color(0xFFB8902F)        // Deeper gold for borders/pressed

    // ── Transport Mode Colors ────────────────────────────────────────────
    val TransportWalk = Color(0xFF66BB6A)     // WALK/RUN segments
    val TransportDrive = Color(0xFFAB47BC)    // DRIVE segments
    val TransportCycle = Color(0xFF42A5F5)    // CYCLE segments
    val TransportTransit = Color(0xFFFF7043)  // TRANSIT segments
    val TransportGap = Color(0xFF616161)      // GAP segments (dashed)

    // ── Severity (anomalies) ─────────────────────────────────────────────
    val SeverityHigh = Error                  // RED
    val SeverityMedium = Warning              // AMBER
    val SeverityLow = AccentBlue              // BLUE
    val SeverityInfo = Primary                // BLUE

    // ── Confidence Gradient ──────────────────────────────────────────────
    val ConfidenceLow = Error                 // <40%
    val ConfidenceMedium = Warning            // 40-70%
    val ConfidenceHigh = AccentGreen          // >70%
}

// ============================================================================
// Voyager Gradient System
//
// All reusable Brush definitions live here. Screens pull from this object
// instead of inlining gradient literals — keeps the visual language unified.
//
// Naming:
//   screenBackground  — full-screen atmospheric backdrop (size-dependent, function)
//   heroCard          — top-of-card blue→indigo wash
//   activeCard        — green-tinted surface for live-tracking states
//   primaryGlow       — radial halo for focal data points (size-dependent, function)
//   sectionDivider    — horizontal fade stripe for section separators
//   topBar            — subtle indigo bloom behind the TopAppBar crown
//   navBar            — Background→elevated fade for the BottomNavigationBar
// ============================================================================
object VoyagerGradients {

    // ── Screen background ─────────────────────────────────────────────────
    // Deep-indigo nebula bloom at the top-centre, fading to OLED black.
    // Usage: Modifier.drawBehind { drawRect(VoyagerGradients.screenBackground(size.width, size.height)) }
    fun screenBackground(width: Float, height: Float): Brush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1C0F42),       // Deep indigo nebula core
            VoyagerColors.Background // Seamless edge — no hard cut
        ),
        center = Offset(width * 0.50f, height * 0.08f),
        radius = height * 0.60f
    )

    // ── Card surfaces ─────────────────────────────────────────────────────
    // Blue→indigo vertical wash. Apply inside hero cards as Modifier.background(heroCard).
    val heroCard: Brush = Brush.verticalGradient(
        colors = listOf(
            VoyagerColors.Primary.copy(alpha = 0.13f),      // Rich primary tint at crown
            VoyagerColors.AccentPurple.copy(alpha = 0.05f), // Faint indigo mid-tone
            Color.Transparent                               // Dissolves into card base
        )
    )

    // Green-tinted surface for "you are here" / live-tracking cards.
    val activeCard: Brush = Brush.verticalGradient(
        colors = listOf(
            VoyagerColors.AccentGreen.copy(alpha = 0.10f),
            Color.Transparent
        )
    )

    // ── Glows ─────────────────────────────────────────────────────────────
    // Soft radial halo for focal data points — ActivityRing centre, hero icons.
    // Usage: drawBehind { drawRect(VoyagerGradients.primaryGlow(size.width, size.height)) }
    fun primaryGlow(width: Float, height: Float): Brush = Brush.radialGradient(
        colors = listOf(
            VoyagerColors.Primary.copy(alpha = 0.28f),
            Color.Transparent
        ),
        center = Offset(width / 2f, height / 2f),
        radius = width.coerceAtLeast(height) * 0.55f
    )

    // ── Structural ────────────────────────────────────────────────────────
    // Horizontal fade stripe — transparent → Primary tint → transparent.
    // Usage: Box(Modifier.fillMaxWidth().height(1.dp).background(sectionDivider))
    val sectionDivider: Brush = Brush.horizontalGradient(
        0.00f to Color.Transparent,
        0.20f to VoyagerColors.Primary.copy(alpha = 0.22f),
        0.80f to VoyagerColors.Primary.copy(alpha = 0.22f),
        1.00f to Color.Transparent
    )

    // Barely-perceptible indigo at the status-bar crown, melting into Background.
    // Apply as Box background behind the TopAppBar with containerColor = Transparent.
    val topBar: Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF15102E),       // Faint indigo at top
            VoyagerColors.Background // Background at the AppBar bottom edge
        )
    )

    // Background at the content edge rising to a faintly elevated surface at the bottom.
    // Removes the jarring Surface-colour shelf and gives the nav bar a floating quality.
    // Apply as Box background behind NavigationBar with containerColor = Transparent.
    val navBar: Brush = Brush.verticalGradient(
        colors = listOf(
            VoyagerColors.Background,   // Seamless with screen content above
            Color(0xFF141428)           // Barely elevated at the gesture-nav area
        )
    )
}
