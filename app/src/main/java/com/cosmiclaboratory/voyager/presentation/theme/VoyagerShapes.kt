package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Voyager Design System — Shape Scale
 *
 * The bold reinvention softens and enlarges the corner language for a more
 * premium, visionOS-adjacent feel. Cards breathe at 20dp, glass surfaces at
 * 24dp, sheets at 28dp. Pills are fully rounded.
 *
 * Centralised so the whole app shares one radius vocabulary.
 */
object VoyagerShapes {
    /** 8dp — tiny chips, badges, inline tags */
    val badge = RoundedCornerShape(8.dp)
    /** 14dp — buttons, segmented controls */
    val button = RoundedCornerShape(14.dp)
    /** 20dp — standard cards */
    val card = RoundedCornerShape(20.dp)
    /** 24dp — glass / hero surfaces */
    val glass = RoundedCornerShape(24.dp)
    /** Top-rounded 28dp — bottom sheets */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Fully rounded — pills, avatars, dots */
    val pill = CircleShape
}
