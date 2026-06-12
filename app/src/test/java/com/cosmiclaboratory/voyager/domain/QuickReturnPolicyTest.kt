package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.QuickReturnPolicy
import com.cosmiclaboratory.voyager.domain.usecase.QuickReturnPolicy.Decision
import com.cosmiclaboratory.voyager.domain.usecase.QuickReturnPolicy.GapSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickReturnPolicyTest {

    private val radius = 80.0 // default placeRadiusM → walk threshold = max(160, 100) = 160

    private fun decide(
        timeSinceDepartureMs: Long = 120_000L,
        distFromPreviousM: Double = 10.0,
        placeRadiusM: Double = radius,
        hasPreviousVisit: Boolean = true,
        gapSegments: List<GapSegment> = emptyList(),
    ) = QuickReturnPolicy.decide(
        timeSinceDepartureMs, distFromPreviousM, placeRadiusM, hasPreviousVisit, gapSegments
    )

    @Test
    fun `back inside the place within the window with no gap movement continues the visit`() {
        assertEquals(Decision.CONTINUE, decide())
    }

    @Test
    fun `a return after the window expires forgets the previous visit`() {
        assertEquals(Decision.CLEAR_MEMORY, decide(timeSinceDepartureMs = QuickReturnPolicy.RETURN_WINDOW_MS + 1))
    }

    @Test
    fun `still outside the place within the window keeps waiting and retains memory`() {
        assertEquals(Decision.KEEP_WAITING, decide(distFromPreviousM = radius + 50))
    }

    @Test
    fun `no previous visit id within the window keeps waiting rather than continuing`() {
        assertEquals(Decision.KEEP_WAITING, decide(hasPreviousVisit = false))
    }

    @Test
    fun `a motorised hop in the gap forgets the previous visit`() {
        listOf("DRIVE", "CYCLE", "RUN", "FLIGHT", "TRANSIT").forEach { type ->
            assertEquals(type, Decision.CLEAR_MEMORY, decide(gapSegments = listOf(GapSegment(type, 0.0))))
        }
    }

    @Test
    fun `a long on-foot excursion in the gap forgets the previous visit`() {
        assertEquals(Decision.CLEAR_MEMORY, decide(gapSegments = listOf(GapSegment("WALK", 300.0))))
    }

    @Test
    fun `a brief step-outside walk in the gap still continues the visit`() {
        assertEquals(Decision.CONTINUE, decide(gapSegments = listOf(GapSegment("WALK", 40.0))))
    }

    @Test
    fun `short walks accumulate across the gap and once past the threshold force a new visit`() {
        val segs = listOf(GapSegment("WALK", 90.0), GapSegment("WALK", 90.0)) // 180 > 160
        assertEquals(Decision.CLEAR_MEMORY, decide(gapSegments = segs))
    }

    @Test
    fun `the walk threshold scales with place radius and has a floor`() {
        // Tight 40 m radius → floor of 100 m applies (2 × 40 = 80 < 100).
        assertEquals(100.0, QuickReturnPolicy.walkMovedAwayThresholdM(40.0), 1e-6)
        // Wide 150 m radius → 2 × radius wins.
        assertEquals(300.0, QuickReturnPolicy.walkMovedAwayThresholdM(150.0), 1e-6)
    }

    @Test
    fun `a walk just under a small-radius floor still continues, just over forces a new visit`() {
        // 40 m radius → threshold is the 100 m floor, not 80 m.
        assertEquals(Decision.CONTINUE, decide(placeRadiusM = 40.0, gapSegments = listOf(GapSegment("WALK", 90.0))))
        assertEquals(Decision.CLEAR_MEMORY, decide(placeRadiusM = 40.0, gapSegments = listOf(GapSegment("WALK", 110.0))))
    }

    @Test
    fun `non-displacement gap segments do not break continuation`() {
        // DWELL / GAP / UNKNOWN_MOTION are not movement away from the place.
        val segs = listOf(GapSegment("DWELL", 0.0), GapSegment("GAP", 0.0), GapSegment("UNKNOWN_MOTION", 500.0))
        assertEquals(Decision.CONTINUE, decide(gapSegments = segs))
    }
}
