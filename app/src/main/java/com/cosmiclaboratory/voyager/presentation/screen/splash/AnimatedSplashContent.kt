package com.cosmiclaboratory.voyager.presentation.screen.splash

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.ui.theme.GreatVibesFontFamily
import kotlinx.coroutines.delay

/**
 * Animated splash content shown on cold start.
 *
 * Displays "Voyager" in Great Vibes font with animated Blue↔Silver color,
 * and "- Aravya by Anshul" subtitle that fades in after a delay.
 * Auto-completes after 2 seconds.
 */
@Composable
fun AnimatedSplashContent(onComplete: () -> Unit) {
    // Title scale-in and fade-in
    val titleAlpha = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.8f) }

    // Subtitle fade-in (delayed)
    val subtitleAlpha = remember { Animatable(0f) }

    // Animated color between Teal and Silver (reused from removed AnimatedVoyagerTitle)
    val infiniteTransition = rememberInfiniteTransition(label = "splashColor")
    val animatedColor by infiniteTransition.animateColor(
        initialValue = VoyagerColors.Primary,
        targetValue = Color(0xFFC0C0C0), // Silver
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titleColor"
    )

    LaunchedEffect(Unit) {
        // Title appears
        titleAlpha.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        titleScale.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))

        // Subtitle fades in after 500ms
        delay(500)
        subtitleAlpha.animateTo(1f, animationSpec = tween(500))

        // Wait and then complete
        delay(900)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // "Voyager" title
            Text(
                text = "Voyager",
                fontFamily = GreatVibesFontFamily,
                fontSize = 52.sp,
                fontWeight = FontWeight.Normal,
                color = animatedColor,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .scale(titleScale.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // "- Aravya by Anshul" subtitle
            Text(
                text = "- Aravya by Anshul",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.alpha(subtitleAlpha.value)
            )
        }
    }
}
