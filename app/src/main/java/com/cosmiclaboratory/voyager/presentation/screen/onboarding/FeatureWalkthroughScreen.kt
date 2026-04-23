package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import kotlinx.coroutines.launch

private data class WalkthroughPage(
    val title: String,
    val body: String
)

private val PAGES = listOf(
    WalkthroughPage(
        title = "Your timeline, on your terms",
        body = "Voyager quietly records where you go and turns it into a timeline you can scroll, search, and learn from."
    ),
    WalkthroughPage(
        title = "Built around privacy",
        body = "Everything stays on this device. No cloud. No accounts. No ads. The only way your data leaves is if you export it yourself."
    ),
    WalkthroughPage(
        title = "Discover your patterns",
        body = "Voyager learns your regular places, picks up on your routines, and surfaces insights about how you actually spend your days."
    )
)

private val PAGE_TINTS = listOf(
    VoyagerColors.Primary,
    VoyagerColors.AccentGreen,
    VoyagerColors.AccentBlue
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureWalkthroughScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()

    val animatedBgTint by animateColorAsState(
        targetValue = PAGE_TINTS[pagerState.currentPage].copy(alpha = 0.04f),
        animationSpec = tween(600),
        label = "bgTint"
    )

    Scaffold(
        containerColor = VoyagerColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(animatedBgTint)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("Skip", color = VoyagerColors.OnSurfaceVariant)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                AnimatedPageContent(
                    pageIndex = pageIndex,
                    page = PAGES[pageIndex],
                    isActive = pagerState.currentPage == pageIndex
                )
            }

            // Animated page indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PAGES.indices.forEach { index ->
                    val selected = pagerState.currentPage == index
                    val dotSize by animateDpAsState(
                        targetValue = if (selected) 12.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "dotSize$index"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) PAGE_TINTS[pagerState.currentPage]
                                      else VoyagerColors.OnSurfaceVariant.copy(alpha = 0.4f),
                        animationSpec = tween(300),
                        label = "dotColor$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < PAGES.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PAGE_TINTS[pagerState.currentPage]
                )
            ) {
                Text(
                    text = if (pagerState.currentPage < PAGES.lastIndex) "Next" else "Get started",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AnimatedPageContent(
    pageIndex: Int,
    page: WalkthroughPage,
    isActive: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (pageIndex) {
            0 -> TimelineIllustration(isActive = isActive)
            1 -> PrivacyIllustration(isActive = isActive)
            2 -> PatternsIllustration(isActive = isActive)
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.OnSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = VoyagerColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/** Page 0: Animated timeline dots with dashed connecting line */
@Composable
private fun TimelineIllustration(isActive: Boolean) {
    val dot1Alpha = remember { Animatable(0f) }
    val dot2Alpha = remember { Animatable(0f) }
    val dot3Alpha = remember { Animatable(0f) }
    val lineProgress = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            dot1Alpha.snapTo(0f); dot2Alpha.snapTo(0f); dot3Alpha.snapTo(0f); lineProgress.snapTo(0f)
            dot1Alpha.animateTo(1f, tween(300))
            lineProgress.animateTo(0.5f, tween(300))
            dot2Alpha.animateTo(1f, tween(300))
            lineProgress.animateTo(1f, tween(300))
            dot3Alpha.animateTo(1f, tween(300))
        }
    }

    Canvas(modifier = Modifier.size(120.dp, 140.dp)) {
        val cx = size.width / 2
        val topY = size.height * 0.15f
        val midY = size.height * 0.5f
        val botY = size.height * 0.85f
        val nodeR = 10.dp.toPx()

        // Line
        drawLine(
            color = VoyagerColors.PrimaryDim.copy(alpha = 0.5f),
            start = Offset(cx, topY + nodeR),
            end = Offset(cx, topY + nodeR + (botY - topY - 2 * nodeR) * lineProgress.value),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
        )
        // Dots
        listOf(topY to dot1Alpha.value, midY to dot2Alpha.value, botY to dot3Alpha.value).forEach { (y, a) ->
            drawCircle(VoyagerColors.Surface, nodeR, Offset(cx, y))
            drawCircle(VoyagerColors.Primary.copy(alpha = a), nodeR * 0.6f, Offset(cx, y))
            drawCircle(
                VoyagerColors.Primary.copy(alpha = a * 0.6f), nodeR,
                Offset(cx, y), style = Stroke(1.5.dp.toPx())
            )
        }
    }
}

/** Page 1: Animated lock — shackle swings closed */
@Composable
private fun PrivacyIllustration(isActive: Boolean) {
    val shackleSweep = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            shackleSweep.snapTo(0f)
            shackleSweep.animateTo(180f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    val color = VoyagerColors.AccentGreen

    Canvas(modifier = Modifier.size(120.dp, 140.dp)) {
        val cx = size.width / 2
        val bodyW = size.width * 0.55f
        val bodyH = size.height * 0.38f
        val bodyTop = size.height * 0.48f
        val bodyLeft = cx - bodyW / 2

        // Lock body
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        // Keyhole
        drawCircle(VoyagerColors.Background, 8.dp.toPx(), Offset(cx, bodyTop + bodyH * 0.4f))
        drawRect(
            VoyagerColors.Background,
            topLeft = Offset(cx - 4.dp.toPx(), bodyTop + bodyH * 0.4f),
            size = Size(8.dp.toPx(), 14.dp.toPx())
        )

        // Shackle arc
        val shackleR = bodyW * 0.3f
        val shackleTop = bodyTop - shackleR * 1.5f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = shackleSweep.value,
            useCenter = false,
            topLeft = Offset(cx - shackleR, shackleTop),
            size = Size(shackleR * 2, shackleR * 2),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Page 2: Animated bar chart with rising trend line */
@Composable
private fun PatternsIllustration(isActive: Boolean) {
    val bar1H = remember { Animatable(0f) }
    val bar2H = remember { Animatable(0f) }
    val bar3H = remember { Animatable(0f) }
    val bar4H = remember { Animatable(0f) }

    val targetHeights = listOf(0.4f, 0.6f, 0.5f, 0.85f)

    LaunchedEffect(isActive) {
        if (isActive) {
            listOf(bar1H, bar2H, bar3H, bar4H).forEach { it.snapTo(0f) }
            launch { bar1H.animateTo(targetHeights[0], tween(400, delayMillis = 0)) }
            launch { bar2H.animateTo(targetHeights[1], tween(400, delayMillis = 80)) }
            launch { bar3H.animateTo(targetHeights[2], tween(400, delayMillis = 160)) }
            launch { bar4H.animateTo(targetHeights[3], tween(400, delayMillis = 240)) }
        }
    }

    Canvas(modifier = Modifier.size(120.dp, 140.dp)) {
        val bars = listOf(bar1H.value, bar2H.value, bar3H.value, bar4H.value)
        val barW = size.width * 0.15f
        val gap = size.width * 0.07f
        val totalW = bars.size * barW + (bars.size - 1) * gap
        val startX = (size.width - totalW) / 2
        val maxBarH = size.height * 0.65f
        val baseY = size.height * 0.85f

        bars.forEachIndexed { i, progress ->
            val barH = maxBarH * progress
            val x = startX + i * (barW + gap)
            drawRoundRect(
                color = VoyagerColors.Primary.copy(alpha = 0.7f + 0.3f * progress),
                topLeft = Offset(x, baseY - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }

        // Trend line connecting bar tops
        val pts = bars.mapIndexed { i, progress ->
            val x = startX + i * (barW + gap) + barW / 2
            Offset(x, baseY - maxBarH * progress)
        }
        for (i in 0 until pts.size - 1) {
            drawLine(
                color = VoyagerColors.AccentBlue.copy(alpha = minOf(bars[i], bars[i + 1]) + 0.1f),
                start = pts[i],
                end = pts[i + 1],
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        pts.forEach { pt ->
            drawCircle(VoyagerColors.AccentBlue, 4.dp.toPx(), pt)
        }
    }
}
