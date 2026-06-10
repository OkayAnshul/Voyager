package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.capture.SampleAdmissionGate.Admission
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class SampleAdmissionGateTest {

    // Fixed wall-clock reference well clear of the slack windows.
    private val now = 1_700_000_000_000L
    private val tenMin = SampleAdmissionGate.TEN_MIN_MS
    private val oneHour = SampleAdmissionGate.ONE_HOUR_MS

    private fun gate() = SampleAdmissionGate()

    @Test
    fun `first sample is accepted`() {
        assertEquals(Admission.Accepted, gate().admit(now, now))
    }

    @Test
    fun `strictly increasing timestamps are accepted`() {
        val g = gate()
        assertEquals(Admission.Accepted, g.admit(now, now))
        assertEquals(Admission.Accepted, g.admit(now + 1_000, now))
        assertEquals(Admission.Accepted, g.admit(now + 2_000, now))
    }

    @Test
    fun `exact duplicate timestamp is dropped`() {
        // F6: equal timestamps (same fix delivered by both active + passive callbacks)
        // must be deduped, not accepted twice.
        val g = gate()
        assertEquals(Admission.Accepted, g.admit(now, now))
        assertEquals(Admission.Duplicate, g.admit(now, now))
    }

    @Test
    fun `slightly older fix within the past window is dropped as duplicate`() {
        // F1 (documented behavior): an out-of-order-but-valid fix older than the watermark
        // yet within the 10-min window is not a teleport, so it's dropped by the dedup CAS
        // rather than logged as a PAST rejection.
        val g = gate()
        assertEquals(Admission.Accepted, g.admit(now, now))
        assertEquals(Admission.Duplicate, g.admit(now - (tenMin - 1), now))
    }

    @Test
    fun `fix far in the past is rejected as PAST`() {
        val g = gate()
        assertEquals(Admission.Accepted, g.admit(now, now))
        assertEquals(Admission.RejectedPast, g.admit(now - tenMin - 1, now))
    }

    @Test
    fun `fix far in the future is rejected as FUTURE`() {
        assertEquals(Admission.RejectedFuture, gate().admit(now + oneHour + 1, now))
    }

    @Test
    fun `fix within the future slack is accepted`() {
        assertEquals(Admission.Accepted, gate().admit(now + oneHour - 1, now))
    }

    @Test
    fun `non-positive timestamp is invalid`() {
        assertEquals(Admission.Invalid, gate().admit(0L, now))
        assertEquals(Admission.Invalid, gate().admit(-1L, now))
    }

    @Test
    fun `seed rejects stale cached fixes but lets newer fixes through`() {
        val g = gate()
        g.seed(now)
        // A stale cached fix from before the seed teleports into the past.
        assertEquals(Admission.RejectedPast, g.admit(now - tenMin - 1, now))
        // A fix equal to the seed watermark is a duplicate.
        assertEquals(Admission.Duplicate, g.admit(now, now))
        // A genuinely newer fix advances the watermark.
        assertEquals(Admission.Accepted, g.admit(now + 1_000, now))
    }

    @Test
    fun `seed never moves the watermark backwards`() {
        val g = gate()
        g.seed(now)
        g.seed(now - oneHour) // older seed must not lower the watermark
        assertEquals(now, g.lastAccepted)
    }

    @Test
    fun `concurrent admits of the same timestamp accept exactly one`() {
        val g = gate()
        val threads = 64
        val accepted = AtomicInteger(0)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)

        val workers = (0 until threads).map {
            Thread {
                ready.countDown()
                go.await()
                if (g.admit(now, now) == Admission.Accepted) accepted.incrementAndGet()
            }.apply { start() }
        }

        ready.await()
        go.countDown()
        workers.forEach { it.join() }

        assertEquals(1, accepted.get())
    }
}
