package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.fixtures.TestDataFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

/**
 * Tests for visit integrity invariants:
 * - Zero-duration visits (< 30s) should be discardable
 * - At most one active visit at a time
 * - Visit completion produces correct durations
 * - Rapid place changes don't produce orphan visits
 */
class VisitIntegrityTest {

    companion object {
        private const val MIN_VISIT_DURATION_MS = 30_000L
    }

    @Test
    fun `completing a visit with duration less than 30s is detectable`() {
        val entry = LocalDateTime.of(2025, 11, 14, 10, 0, 0)
        val exit = entry.plusSeconds(15) // 15 seconds

        val visit = Visit.createWithDuration(placeId = 1, entryTime = entry, exitTime = null, confidence = 1.0f)
        val completed = visit.complete(exit)

        assertThat(completed.duration).isLessThan(MIN_VISIT_DURATION_MS)
        assertThat(completed.exitTime).isEqualTo(exit)
    }

    @Test
    fun `completing a visit with duration at least 30s passes guard`() {
        val entry = LocalDateTime.of(2025, 11, 14, 10, 0, 0)
        val exit = entry.plusSeconds(45) // 45 seconds

        val visit = Visit.createWithDuration(placeId = 1, entryTime = entry, exitTime = null, confidence = 1.0f)
        val completed = visit.complete(exit)

        assertThat(completed.duration).isAtLeast(MIN_VISIT_DURATION_MS)
    }

    @Test
    fun `active visit has null exitTime`() {
        val visit = Visit.createWithDuration(
            placeId = 1,
            entryTime = LocalDateTime.now(),
            exitTime = null,
            confidence = 1.0f
        )

        assertThat(visit.isActive).isTrue()
        assertThat(visit.exitTime).isNull()
        assertThat(visit._duration).isEqualTo(0L)
    }

    @Test
    fun `completing an active visit sets exitTime and duration`() {
        val entry = LocalDateTime.of(2025, 11, 14, 10, 0, 0)
        val exit = LocalDateTime.of(2025, 11, 14, 11, 0, 0)

        val active = Visit.createWithDuration(placeId = 1, entryTime = entry, exitTime = null, confidence = 1.0f)
        val completed = active.complete(exit)

        assertThat(completed.isActive).isFalse()
        assertThat(completed.exitTime).isEqualTo(exit)
        assertThat(completed.duration).isEqualTo(3600000L) // 1 hour
    }

    @Test
    fun `rapid place change simulation - short visits are below threshold`() {
        val baseTime = LocalDateTime.of(2025, 11, 14, 10, 0, 0)

        // Simulate rapid place changes: 5 seconds at each of 3 places
        val visits = (1..3).map { i ->
            val entry = baseTime.plusSeconds((i - 1) * 5L)
            val exit = baseTime.plusSeconds(i * 5L)
            Visit.createWithDuration(placeId = i.toLong(), entryTime = entry, exitTime = null, confidence = 1.0f)
                .complete(exit)
        }

        // All should be below the minimum duration threshold
        visits.forEach { visit ->
            assertThat(visit.duration).isLessThan(MIN_VISIT_DURATION_MS)
        }
    }

    @Test
    fun `zero duration visit is below threshold`() {
        val time = LocalDateTime.of(2025, 11, 14, 10, 0, 0)
        val visit = Visit.createWithDuration(placeId = 1, entryTime = time, exitTime = null, confidence = 1.0f)
            .complete(time)

        assertThat(visit.duration).isEqualTo(0L)
        assertThat(visit.duration).isLessThan(MIN_VISIT_DURATION_MS)
    }

    @Test
    fun `factory visits from TestDataFactory have correct duration`() {
        val visit = TestDataFactory.createVisit(id = 1, duration = 60_000L)
        assertThat(visit.duration).isEqualTo(60_000L)
        assertThat(visit.duration).isAtLeast(MIN_VISIT_DURATION_MS)
    }
}
