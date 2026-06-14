package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.PlaceConfidenceDecay
import org.junit.Assert.*
import org.junit.Test

class PlaceConfidenceDecayTest {

    private val msPerDay = 86_400_000L
    private val now = 1_700_000_000_000L

    @Test
    fun `no decay within grace period`() {
        val visited = now - 90 * msPerDay
        val out = PlaceConfidenceDecay.decay(0.9f, visited, now)
        assertEquals(0.9f, out, 0.0001f)
    }

    @Test
    fun `confidence at floor is unaffected`() {
        val visited = now - 365 * msPerDay
        val out = PlaceConfidenceDecay.decay(PlaceConfidenceDecay.FLOOR, visited, now)
        assertEquals(PlaceConfidenceDecay.FLOOR, out, 0.0001f)
    }

    @Test
    fun `null lastVisited leaves confidence untouched`() {
        val out = PlaceConfidenceDecay.decay(0.9f, null, now)
        assertEquals(0.9f, out, 0.0001f)
    }

    @Test
    fun `confidence decays beyond grace period`() {
        val visited = now - 365 * msPerDay // 185 days past grace
        val out = PlaceConfidenceDecay.decay(0.9f, visited, now)
        // 0.99^185 ≈ 0.156 → 0.9 * 0.156 = 0.14, floored to 0.4
        assertEquals(PlaceConfidenceDecay.FLOOR, out, 0.0001f)
    }

    @Test
    fun `decay is gradual just past the grace boundary`() {
        val visited = now - (PlaceConfidenceDecay.GRACE_DAYS + 10) * msPerDay
        val out = PlaceConfidenceDecay.decay(0.9f, visited, now)
        // 0.99^10 ≈ 0.904 → 0.9 * 0.904 ≈ 0.814; clearly between 0.9 and floor
        assertTrue("expected decayed in (floor, 0.9), got $out",
            out < 0.9f && out > PlaceConfidenceDecay.FLOOR)
    }

    @Test
    fun `decay never carries confidence below floor`() {
        val visited = now - 5000L * msPerDay
        val out = PlaceConfidenceDecay.decay(0.9f, visited, now)
        assertTrue(out >= PlaceConfidenceDecay.FLOOR)
    }

    // ── Repeatability resists decay (C1) ──

    @Test
    fun `a recurring place keeps its confidence where a one-off would decay`() {
        val visited = now - 365 * msPerDay // 185 days past the base grace
        val oneOff = PlaceConfidenceDecay.decay(0.9f, visited, now, repeatability = 0f)
        val recurring = PlaceConfidenceDecay.decay(0.9f, visited, now, repeatability = 1f)
        // The one-off decays to the floor; the recurring place's extended grace spares it.
        assertEquals(PlaceConfidenceDecay.FLOOR, oneOff, 0.0001f)
        assertEquals(0.9f, recurring, 0.0001f)
    }

    @Test
    fun `a recurring place has a raised floor`() {
        val visited = now - 5000L * msPerDay // far past any grace
        val recurring = PlaceConfidenceDecay.decay(0.65f, visited, now, repeatability = 1f)
        // 0.65 sits below the raised floor (~0.7), so it's preserved, not decayed.
        assertEquals(0.65f, recurring, 0.0001f)
        // Same place treated as a one-off would have decayed below 0.65.
        assertTrue(PlaceConfidenceDecay.decay(0.65f, visited, now, repeatability = 0f) < 0.65f)
    }
}
