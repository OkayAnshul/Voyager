package com.cosmiclaboratory.voyager.presentation.screen.analytics

import com.cosmiclaboratory.voyager.domain.model.CarbonFootprint
import com.cosmiclaboratory.voyager.domain.model.ModeFootprint
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.SleepRhythm
import com.cosmiclaboratory.voyager.domain.usecase.TimeBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightNarratorsTest {

    private fun weekly(distanceChange: Double, places: Int = 7) = WeeklyComparisonData(
        dateRange = "This Week",
        placesThisWeek = places,
        placesLastWeek = 5,
        placesChange = 0.0,
        distanceThisWeek = 42_600.0,
        distanceLastWeek = 36_000.0,
        distanceChange = distanceChange,
        timeAwayThisWeek = 31,
        timeAwayLastWeek = 32,
        timeAwayChange = -4.0
    )

    @Test
    fun `positive distance change reads as farther and celebrates`() {
        val n = InsightNarrators.overview(weekly(18.4), movement = null, placeCount = 7, periodLabel = "This Week")

        assertEquals(NarrativeTone.CELEBRATE, n.tone)
        assertEquals("Overview · this week", n.eyebrow)
        // Direction is a WORD, not colour alone.
        assertTrue(n.plainText.contains("farther"))
        assertTrue(n.plainText.contains("18%"))
        // The one highlighted figure is the accent segment.
        val accent = n.segments.single { it.emphasis == NarrativeEmphasis.ACCENT }
        assertEquals("18% farther", accent.text)
    }

    @Test
    fun `negative distance change reads as less and stays gentle`() {
        val n = InsightNarrators.overview(weekly(-4.0), movement = null, placeCount = 7, periodLabel = "This Week")

        assertEquals(NarrativeTone.GENTLE, n.tone)
        assertTrue(n.plainText.contains("less"))
        assertTrue(n.plainText.contains("4%"))
    }

    @Test
    fun `period label is never hardcoded`() {
        val n = InsightNarrators.overview(weekly(10.0), movement = null, placeCount = 3, periodLabel = "This Month")

        assertEquals("Overview · this month", n.eyebrow)
        assertTrue(n.plainText.contains("this month"))
    }

    @Test
    fun `place tail is omitted when there are no places`() {
        val n = InsightNarrators.overview(weekly(10.0, places = 0), movement = null, placeCount = 0, periodLabel = "This Week")

        assertTrue(!n.plainText.contains("across"))
        assertTrue(n.plainText.trim().endsWith("."))
        assertTrue(n.segments.none { it.emphasis == NarrativeEmphasis.NUMBER })
    }

    @Test
    fun `single place is not pluralised`() {
        val n = InsightNarrators.overview(weekly(10.0, places = 1), movement = null, placeCount = 1, periodLabel = "This Week")

        assertTrue(n.plainText.contains("across"))
        assertTrue(n.plainText.contains("1 place."))
        assertTrue(!n.plainText.contains("1 places"))
    }

    @Test
    fun `no comparison yet falls back to a gentle opener`() {
        val n = InsightNarrators.overview(weekly = null, movement = null, placeCount = 0, periodLabel = "This Week")

        assertEquals(NarrativeTone.GENTLE, n.tone)
        assertTrue(n.plainText.contains("Keep moving"))
        assertTrue(n.segments.all { it.emphasis == NarrativeEmphasis.PLAIN })
    }

    // ── Weekly ──────────────────────────────────────────────────────────

    @Test
    fun `weekly leads on more places when the count rose`() {
        val n = InsightNarrators.weekly(weekly(distanceChange = 5.0, places = 7), "This Week")

        assertEquals(NarrativeTone.CELEBRATE, n.tone)
        assertTrue(n.plainText.contains("2 more places"))
        assertEquals("2 more", n.segments.single { it.emphasis == NarrativeEmphasis.ACCENT }.text)
    }

    @Test
    fun `weekly leads on fewer places when the count fell`() {
        val n = InsightNarrators.weekly(weekly(distanceChange = 5.0, places = 3), "This Week")

        assertEquals(NarrativeTone.GENTLE, n.tone)
        assertTrue(n.plainText.contains("2 fewer places"))
    }

    @Test
    fun `weekly falls back to distance when place count is unchanged`() {
        val n = InsightNarrators.weekly(weekly(distanceChange = -8.0, places = 5), "This Week")

        assertTrue(n.plainText.contains("less"))
        assertTrue(n.plainText.contains("8%"))
    }

    @Test
    fun `weekly with no data is gentle and keeps the period`() {
        val n = InsightNarrators.weekly(null, "This Month")

        assertEquals(NarrativeTone.GENTLE, n.tone)
        assertTrue(n.plainText.contains("this month"))
    }

    // ── Highlights ──────────────────────────────────────────────────────

    @Test
    fun `highlights invites when there are moments`() {
        val n = InsightNarrators.highlights(highlightCount = 2, memoryCount = 1)

        assertEquals(NarrativeTone.CELEBRATE, n.tone)
        assertTrue(n.plainText.contains("worth keeping"))
    }

    @Test
    fun `highlights explains the empty state gently`() {
        val n = InsightNarrators.highlights(0, 0)

        assertEquals(NarrativeTone.GENTLE, n.tone)
        assertTrue(n.plainText.contains("collect here"))
    }

    // ── Pro lenses ──────────────────────────────────────────────────────

    @Test
    fun `movement leads with distance covered`() {
        val n = InsightNarrators.movement(
            MovementStats(totalDistanceKm = 42.6, avgSpeedKmh = 5.0, mostActiveDay = "2026-07-25"),
            "This Week"
        )
        assertEquals(NarrativeTone.CELEBRATE, n.tone)
        assertTrue(n.plainText.contains("42.6 km"))
        assertEquals("42.6 km", n.segments.single { it.emphasis == NarrativeEmphasis.ACCENT }.text)
    }

    @Test
    fun `balance names where most time was spent`() {
        val budget = TimeBudget(homeMs = 10_000, workMs = 3_000, outMs = 2_000, movingMs = 1_000, untrackedMs = 0)
        val n = InsightNarrators.balance(budget, "This Week")

        assertTrue(n.plainText.contains("home"))
        assertTrue(n.plainText.contains("%"))
    }

    @Test
    fun `carbon celebrates a high share of foot and bike travel`() {
        val modes = listOf(
            ModeFootprint(SegmentType.WALK, distanceKm = 6.0, kgCo2 = 0.0, gramsPerKm = 0.0),
            ModeFootprint(SegmentType.DRIVE, distanceKm = 4.0, kgCo2 = 0.68, gramsPerKm = 170.0)
        )
        val fp = CarbonFootprint(rangeLabel = "This Week", modes = modes, totalKgCo2 = 0.68, totalDistanceKm = 10.0)
        val n = InsightNarrators.carbon(fp)

        assertEquals(NarrativeTone.CELEBRATE, n.tone)
        assertTrue(n.plainText.contains("foot or by bike"))
        assertEquals("60%", n.segments.single { it.emphasis == NarrativeEmphasis.ACCENT }.text)
    }

    @Test
    fun `anomalies are framed as curiosity, never a warning`() {
        val n = InsightNarrators.anomalies(anomalyCount = 2, breakCount = 0, periodLabel = "This Week")

        assertEquals(NarrativeTone.CURIOUS, n.tone)
        assertTrue(n.plainText.contains("unusual"))
        assertTrue(!n.plainText.lowercase().contains("warning"))
    }

    @Test
    fun `anomalies reassure when nothing is unusual`() {
        val n = InsightNarrators.anomalies(0, 0, "This Week")

        assertEquals(NarrativeTone.NEUTRAL, n.tone)
        assertTrue(n.plainText.contains("Nothing unusual"))
    }

    @Test
    fun `rhythm celebrates a consistent beat`() {
        val sleep = SleepRhythm(
            nightsAnalyzed = 20,
            medianOvernightMs = 8 * 3_600_000L,
            settleMinuteOfDay = 1400,
            wakeMinuteOfDay = 420,
            consistency = SleepRhythm.Consistency.CONSISTENT
        )
        val n = InsightNarrators.rhythm(sleep)

        assertEquals(NarrativeTone.CELEBRATE, n.tone)
        assertTrue(n.plainText.contains("steady beat"))
    }
}
