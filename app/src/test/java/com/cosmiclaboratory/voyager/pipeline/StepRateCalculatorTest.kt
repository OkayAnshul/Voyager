package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.pipeline.stage.StepRateCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepRateCalculatorTest {

    /** A bucket of [steps] steps spanning [startMs]..[endMs]. */
    private fun bucket(steps: Int, startMs: Long, endMs: Long) =
        StepBucket(stepCount = steps, periodStart = startMs, periodEnd = endMs)

    @Test
    fun `no buckets means no signal`() {
        assertNull(StepRateCalculator.stepsPerMinute(emptyList()))
    }

    @Test
    fun `a degenerate zero-length span yields no signal`() {
        assertNull(StepRateCalculator.stepsPerMinute(listOf(bucket(10, 1_000L, 1_000L))))
    }

    @Test
    fun `a brief step burst does not extrapolate to a phantom run`() {
        // 5 steps in a 2 s bucket. Old logic: (5 / 2 s) × 60 = 150 spm → RUNNING. Fixed: the
        // denominator floors at 30 s → 10 spm, which fusion will NOT treat as walking/running.
        val rate = StepRateCalculator.stepsPerMinute(listOf(bucket(5, 0L, 2_000L)))!!
        assertEquals(10f, rate, 0.01f)
        assertTrue("burst must stay below the 100 spm walking threshold", rate < 100f)
    }

    @Test
    fun `a sustained real walk reads as a walking cadence`() {
        // ~105 steps over a 58 s span (span > 30 s floor, so it is used directly) ≈ 108 spm.
        val rate = StepRateCalculator.stepsPerMinute(listOf(bucket(105, 0L, 58_000L)))!!
        assertEquals(108.6f, rate, 0.5f)
        assertTrue("a real walk must clear the 100 spm threshold", rate > 100f)
    }

    @Test
    fun `a running cadence reads above the run threshold`() {
        // 150 steps over a 60 s span = 150 spm.
        val rate = StepRateCalculator.stepsPerMinute(listOf(bucket(150, 0L, 60_000L)))!!
        assertEquals(150f, rate, 0.5f)
        assertTrue("a run must clear the 140 spm threshold", rate > 140f)
    }

    @Test
    fun `near-zero steps over the window yields a near-zero rate, not null`() {
        // Stillness must stay a usable signal (fusion's "<5 spm ⇒ STILL" correction).
        val rate = StepRateCalculator.stepsPerMinute(listOf(bucket(1, 0L, 60_000L)))!!
        assertTrue("expected a near-zero rate, got $rate", rate < 5f)
    }

    @Test
    fun `steps are summed and span measured across multiple buckets`() {
        val buckets = listOf(
            bucket(50, 0L, 20_000L),
            bucket(55, 20_000L, 58_000L)
        )
        // 105 steps over a 58 s observed span ≈ 108.6 spm.
        assertEquals(108.6f, StepRateCalculator.stepsPerMinute(buckets)!!, 0.5f)
    }

    @Test
    fun `out-of-order buckets still measure the full observed span`() {
        val buckets = listOf(
            bucket(55, 20_000L, 58_000L),
            bucket(50, 0L, 20_000L)
        )
        assertEquals(108.6f, StepRateCalculator.stepsPerMinute(buckets)!!, 0.5f)
    }
}
