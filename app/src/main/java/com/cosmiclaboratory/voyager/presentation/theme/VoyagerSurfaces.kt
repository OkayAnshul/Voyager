package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Voyager Design System — Surfaces ("Aurora")
 *
 * Cards are uniform flat solids (no frosted glass — see [VoyagerCard]); the only
 * shared surface brushes left are the [glassSurface] hero tint and the signature
 * multi-stop aurora accents used for CTAs, ring tracks, and live-status washes.
 * Screens never inline aurora literals.
 */
object VoyagerSurfaces {

    // ── Surface tint ─────────────────────────────────────────────────────
    /** Semi-opaque card body behind the dashboard live-status hero. */
    val glassSurface: Color = Color(0xFF1A1A2E).copy(alpha = 0.62f)

    // ── Aurora signature accent ──────────────────────────────────────────
    /** Blue → indigo → violet sweep for hero moments, CTAs, ring tracks, wordmark. */
    val aurora: Brush = Brush.linearGradient(
        colors = listOf(
            VoyagerColors.Primary,        // #3B82F6 vivid blue
            Color(0xFF6366F1),            // indigo
            VoyagerColors.AccentPurple    // #AB47BC violet
        )
    )

    /** Low-alpha aurora wash for card backgrounds and glows. */
    val auroraSoft: Brush = Brush.linearGradient(
        colors = listOf(
            VoyagerColors.Primary.copy(alpha = 0.16f),
            Color(0xFF6366F1).copy(alpha = 0.10f),
            VoyagerColors.AccentPurple.copy(alpha = 0.06f)
        )
    )

    /** Live-tracking aurora (adds the active green) for "capturing now" surfaces. */
    val auroraActive: Brush = Brush.linearGradient(
        colors = listOf(
            VoyagerColors.AccentGreen.copy(alpha = 0.16f),
            VoyagerColors.Primary.copy(alpha = 0.10f),
            Color.Transparent
        )
    )

    /** Gold wash reserved for Pro / Proof surfaces. */
    val premiumWash: Brush = Brush.linearGradient(
        colors = listOf(
            VoyagerColors.Premium.copy(alpha = 0.18f),
            VoyagerColors.PremiumDim.copy(alpha = 0.06f),
            Color.Transparent
        )
    )
}
