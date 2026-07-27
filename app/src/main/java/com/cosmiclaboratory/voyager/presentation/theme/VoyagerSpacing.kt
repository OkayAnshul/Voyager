package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Voyager Design System — Spacing Scale
 *
 * One 4dp baseline grid for the whole app. Screens and components pull from
 * here instead of sprinkling magic numbers, so vertical rhythm stays consistent
 * as the redesign propagates across every surface.
 *
 * Usage:
 *   Modifier.padding(VoyagerSpacing.lg)
 *   Arrangement.spacedBy(VoyagerSpacing.md)
 */
object VoyagerSpacing {
    /** 2dp — hairline gaps, icon-to-text micro spacing */
    val xxs: Dp = 2.dp
    /** 4dp — tight inner spacing */
    val xs: Dp = 4.dp
    /** 8dp — default chip / inline gap */
    val sm: Dp = 8.dp
    /** 12dp — list row gap, compact card padding */
    val md: Dp = 12.dp
    /** 16dp — standard card padding, default content gap */
    val lg: Dp = 16.dp
    /** 24dp — between major sections */
    val xl: Dp = 24.dp
    /** 32dp — generous hero spacing */
    val xxl: Dp = 32.dp
    /** 48dp — large empty-state / onboarding rhythm */
    val xxxl: Dp = 48.dp

    /** Horizontal inset for screen content (editorial, slightly wider than card padding). */
    val screen: Dp = 20.dp
    /** Vertical gap between titled sections. */
    val section: Dp = 24.dp
    /** Minimum interactive touch target (accessibility floor). */
    val touchTarget: Dp = 48.dp
}
