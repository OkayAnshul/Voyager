package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.capture.StepDeltaResolver.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepDeltaResolverTest {

    private val window = StepDeltaResolver.DEFAULT_BATCH_WINDOW_MS
    private val t0 = 1_000_000L

    private fun resolver() = StepDeltaResolver().apply { reset(t0) }

    @Test
    fun `first reading sets baseline`() {
        assertTrue(resolver().onCount(8000, t0) is Outcome.Baseline)
    }

    @Test
    fun `steps within the window are buffered, not emitted`() {
        val r = resolver()
        r.onCount(8000, t0)
        assertTrue(r.onCount(8010, t0 + 1_000) is Outcome.Buffer)
    }

    @Test
    fun `emits accumulated delta after the window elapses`() {
        val r = resolver()
        r.onCount(8000, t0)
        r.onCount(8010, t0 + 1_000)                 // buffered
        val out = r.onCount(8025, t0 + window + 1) as Outcome.Emit
        assertEquals(25, out.steps)                 // 8025 - 8000, no loss across the buffered reading
        assertEquals(t0, out.periodStart)
        assertEquals(t0 + window + 1, out.periodEnd)
    }

    @Test
    fun `reboot resets baseline and emits no spike`() {
        val r = resolver()
        r.onCount(8000, t0)
        assertTrue(r.onCount(12, t0 + window + 1) is Outcome.Reboot)   // counter reset on reboot
        val next = r.onCount(40, t0 + 2 * window + 2) as Outcome.Emit
        assertEquals(28, next.steps)                // 40 - 12, never 40 - 8000
    }

    @Test
    fun `does not double-count after an emit`() {
        val r = resolver()
        r.onCount(8000, t0)
        assertEquals(30, (r.onCount(8030, t0 + window + 1) as Outcome.Emit).steps)
        assertEquals(15, (r.onCount(8045, t0 + 2 * window + 2) as Outcome.Emit).steps)
    }

    @Test
    fun `flush emits the buffered tail`() {
        val r = resolver()
        r.onCount(8000, t0)
        r.onCount(8007, t0 + 1_000)                 // buffered, under the window
        val flushed = r.flush(t0 + 2_000) as Outcome.Emit
        assertEquals(7, flushed.steps)
        assertEquals(t0, flushed.periodStart)
    }

    @Test
    fun `flush returns null when nothing is pending`() {
        val r = resolver()
        r.onCount(8000, t0)
        assertNull(r.flush(t0 + 1_000))             // latest == baseline → no steps
    }

    @Test
    fun `flush returns null before any reading`() {
        assertNull(resolver().flush(t0 + 1_000))
    }
}
