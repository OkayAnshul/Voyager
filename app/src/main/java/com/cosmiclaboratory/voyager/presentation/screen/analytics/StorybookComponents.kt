package com.cosmiclaboratory.voyager.presentation.screen.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmiclaboratory.voyager.presentation.components.ShimmerLine
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.ui.theme.InterFontFamily
import com.cosmiclaboratory.voyager.ui.theme.JetBrainsMonoFontFamily
import com.cosmiclaboratory.voyager.ui.theme.MonoStatSmall

// ============================================================================
// Storybook primitives — flat, typographic, gradient-free.
// Colour lives ONLY on text (chapter label + the one key figure), never in fills.
// ============================================================================

/** Per-chapter identity colour, applied only to type. */
fun chapterAccent(tab: StatisticsTab): Color = when (tab) {
    StatisticsTab.OVERVIEW -> VoyagerColors.Primary
    StatisticsTab.WEEKLY -> VoyagerColors.AccentBlue
    StatisticsTab.PATTERNS -> VoyagerColors.AccentPurple
    StatisticsTab.HIGHLIGHTS -> VoyagerColors.Premium
    StatisticsTab.RHYTHM -> Color(0xFF8B8DF7)   // indigo — chapter identity only
    StatisticsTab.MOVEMENT -> VoyagerColors.AccentGreen
    StatisticsTab.BALANCE -> VoyagerColors.AccentAmber
    StatisticsTab.CARBON -> VoyagerColors.AccentGreen
    StatisticsTab.ANOMALIES -> VoyagerColors.AccentAmber
}

/**
 * The lead of every chapter: a mono eyebrow above one Inter sentence in which
 * [NarrativeEmphasis.NUMBER] runs switch to mono (ink) and the single
 * [NarrativeEmphasis.ACCENT] run switches to mono in the chapter [accent] colour.
 * This is the flat, gradient-free replacement for the old SynthesisHero —
 * hierarchy comes from type, not a tint.
 */
@Composable
fun StoryHero(narrative: InsightNarrative, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = narrative.eyebrow.uppercase(),
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = accent
        )
        Spacer(Modifier.height(8.dp))
        val sentence = buildAnnotatedString {
            narrative.segments.forEach { segment ->
                when (segment.emphasis) {
                    NarrativeEmphasis.PLAIN -> append(segment.text)
                    NarrativeEmphasis.NUMBER -> withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = VoyagerColors.OnSurface
                        )
                    ) { append(segment.text) }
                    NarrativeEmphasis.ACCENT -> withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    ) { append(segment.text) }
                }
            }
        }
        Text(
            text = sentence,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = VoyagerColors.OnSurface
        )
        narrative.body?.let { body ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

/** One line in a [DataLedger]. */
data class LedgerRow(
    val key: String,
    val value: String,
    val delta: String? = null,
    val deltaUp: Boolean? = null,
    /** Optional prior-period value shown muted as "was X" before the value. */
    val previous: String? = null
)

/**
 * Dense, typeset data table — replaces the big 2×2 stat tiles. Inter key on the
 * left, mono value on the right, an optional mono green/amber delta, hairline
 * rules between rows.
 */
@Composable
fun DataLedger(rows: List<LedgerRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index == 0) {
                HorizontalDivider(
                    color = VoyagerColors.SurfaceBright.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.key,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                row.previous?.let { prev ->
                    Text(
                        text = "was $prev",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = VoyagerColors.OnSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Text(
                    text = row.value,
                    style = MonoStatSmall,
                    color = VoyagerColors.OnSurface
                )
                row.delta?.let { delta ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = delta,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = when (row.deltaUp) {
                            true -> VoyagerColors.AccentGreen
                            false -> VoyagerColors.AccentAmber
                            null -> VoyagerColors.OnSurfaceVariant
                        }
                    )
                }
            }
            HorizontalDivider(
                color = VoyagerColors.SurfaceBright.copy(alpha = 0.25f),
                thickness = 1.dp
            )
        }
    }
}

/**
 * A collectible "keepsake" — a flat card with a mono accent eyebrow, one Inter
 * sentence, and optional mono meta. Used for Highlights (firsts, records,
 * "on this day" memories).
 */
@Composable
fun KeepsakeCard(
    eyebrow: String,
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    meta: String? = null
) {
    VoyagerCard(modifier = modifier.fillMaxWidth(), padding = 14.dp) {
        Text(
            text = eyebrow.uppercase(),
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            letterSpacing = 1.4.sp,
            color = accent
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = VoyagerColors.OnSurface
        )
        meta?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}

/** The "01 / 09" flip-book position indicator (mono). */
@Composable
fun ChapterPosition(index: Int, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "%02d / %02d".format(index + 1, count),
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = VoyagerColors.OnSurfaceVariant,
        modifier = modifier
    )
}

/**
 * A slice of the week for [BalanceType], pre-computed by the caller.
 * [dominant] marks the largest share (drawn in the chapter accent).
 */
data class BalanceSlice(val label: String, val pct: Int, val dominant: Boolean = false)

/**
 * Balance rendered as type, not a chart: each part of the week is a word **sized
 * by its share** (home largest → untracked tiny). The size *is* the metaphor —
 * no pie, no bar. The dominant slice takes the chapter accent; the rest fade with
 * size. Fulfils the brief's "beautiful visual metaphor, not a pie chart".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceType(slices: List<BalanceSlice>, accent: Color, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        slices.forEach { slice ->
            val size = (12f + slice.pct * 0.4f).coerceIn(12f, 32f)
            val color = when {
                slice.dominant -> accent
                slice.pct < 8 -> VoyagerColors.OnSurfaceVariant
                else -> VoyagerColors.OnSurface
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = slice.label,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = size.sp,
                    color = color
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${slice.pct}%",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = (size * 0.45f).coerceAtLeast(10f).sp,
                    color = VoyagerColors.OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = (size * 0.12f).dp)
                )
            }
        }
    }
}

/**
 * The typographic Pro gate for locked chapters — replaces the generic
 * FeatureGate on Insights only. Aspirational, flat, one tasteful card: gold mono
 * PRO eyebrow + an invitation + an unlock action. Never nags.
 */
@Composable
fun ChapterLockCard(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "VOYAGER PRO",
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = VoyagerColors.Premium
        )
        Text(
            text = "A deeper story is waiting here.",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            color = VoyagerColors.OnSurface
        )
        Text(
            text = "Routines, rhythm, movement, balance, carbon and anomalies — the patterns shaping your week.",
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = VoyagerColors.OnSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        VoyagerButton(onClick = onUnlock) { Text("Unlock Pro") }
    }
}

/** Loading skeleton mirroring a chapter page (mono eyebrow + sentence + ledger). */
@Composable
fun StoryPageSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerLine(widthFraction = 0.35f, height = 10.dp)
        ShimmerLine(widthFraction = 0.9f, height = 22.dp)
        ShimmerLine(widthFraction = 0.7f, height = 22.dp)
        Spacer(Modifier.height(8.dp))
        repeat(4) { ShimmerLine(widthFraction = 1f, height = 16.dp) }
    }
}
