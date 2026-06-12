package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.pipeline.stage.SpoofHeuristics
import com.cosmiclaboratory.voyager.pipeline.stage.SpoofHeuristics.Fix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofHeuristicsTest {

    // San Francisco
    private val baseLat = 37.7749
    private val baseLng = -122.4194

    /** A fix [dtMs] after t0, displaced [lng] (≈ 88 km per degree at this latitude). */
    private fun fixAt(capturedAt: Long, lat: Double = baseLat, lng: Double = baseLng) =
        Fix(lat, lng, capturedAt)

    @Test
    fun `a teleport across the continent in seconds is implausible`() {
        // SF → New York (~4100 km) in 5 s → ~820 km/s. Obvious spoof.
        val prev = fixAt(0L)
        val curr = fixAt(5_000L, lat = 40.7128, lng = -74.0060)
        assertTrue(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `a normal driving fix is plausible`() {
        // ~1 km east in 60 s ≈ 16.7 m/s (~60 km/h).
        val prev = fixAt(0L)
        val curr = fixAt(60_000L, lng = baseLng + 0.0114)
        assertFalse(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `a real flight at cruise speed is plausible`() {
        // ~150 km in 600 s ≈ 250 m/s (~900 km/h) — a commercial jet, below the Mach-1 ceiling,
        // with the delta kept inside the gap window so the speed gate actually runs.
        val prev = fixAt(0L)
        val curr = fixAt(SpoofHeuristics.MAX_TIME_DELTA_MS, lng = baseLng + 1.7)
        assertFalse(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `a jump straddling a tracking gap is not judged`() {
        // 4100 km but over 2 h — could be a real flight while untracked. dt exceeds the gap window.
        val prev = fixAt(0L)
        val curr = fixAt(2 * 60 * 60 * 1000L, lat = 40.7128, lng = -74.0060)
        assertFalse(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `sub-second deltas are not judged`() {
        val prev = fixAt(0L)
        val curr = fixAt(500L, lat = 40.7128, lng = -74.0060)
        assertFalse(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `a non-positive time delta is not judged`() {
        val prev = fixAt(10_000L)
        val curr = fixAt(5_000L, lat = 40.7128, lng = -74.0060)
        assertFalse(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `high-accuracy jitter under the jump floor is never flagged`() {
        // ~50 m wobble in 1 s would imply 50 m/s but is below the 1 km jump floor.
        val prev = fixAt(0L)
        val curr = fixAt(1_000L, lat = baseLat + 0.00045)
        assertFalse(SpoofHeuristics.isImplausibleJump(prev, curr))
    }

    @Test
    fun `just over the speed ceiling within the window is flagged`() {
        // ~340 m/s ceiling: 250 km in 600 s ≈ 417 m/s → implausible.
        val prev = fixAt(0L)
        val curr = fixAt(600_000L, lng = baseLng + 2.85)
        assertTrue(SpoofHeuristics.isImplausibleJump(prev, curr))
    }
}
