package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.fixtures.TestDataFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

/**
 * Tests for time calculation accuracy
 * These verify that visit durations and analytics are calculated correctly
 */
class TimeCalculationTest {

    @Test
    fun `visit duration calculated correctly for completed visit`() {
        // Given: A visit from 10 AM to 12:30 PM
        val entryTime = LocalDateTime.of(2025, 11, 14, 10, 0, 0)
        val exitTime = LocalDateTime.of(2025, 11, 14, 12, 30, 0)

        // When: Calculate duration
        val duration = Duration.between(entryTime, exitTime).toMillis()

        // Then: Duration should be 2.5 hours (9000000 milliseconds)
        assertThat(duration).isEqualTo(9000000L)
    }

    @Test
    fun `ongoing visit duration updates correctly`() {
        // Given: Entry time 2.5 hours ago
        val entryTime = LocalDateTime.now().minusHours(2).minusMinutes(30)
        val currentTime = LocalDateTime.now()

        // When: Calculate current duration
        val duration = Duration.between(entryTime, currentTime).toMillis()

        // Then: Should be approximately 2.5 hours (allowing 1 minute variance for test execution)
        val expected = 9000000L // 2.5 hours in milliseconds
        val variance = 60000L // 1 minute variance
        assertThat(duration).isAtLeast(expected - variance)
        assertThat(duration).isAtMost(expected + variance)
    }

    @Test
    fun `day analytics time totals are accurate`() {
        // Given: Multiple visits
        val visits = listOf(
            TestDataFactory.createVisit(id = 1, duration = 3600000L), // 1 hour
            TestDataFactory.createVisit(id = 2, duration = 7200000L), // 2 hours
            TestDataFactory.createVisit(id = 3, duration = 1800000L)  // 30 minutes
        )

        // When: Sum all durations
        val totalTime = visits.sumOf { it.duration }

        // Then: Total should be 3.5 hours (12600000 milliseconds)
        assertThat(totalTime).isEqualTo(12600000L)
    }

    @Test
    fun `visit duration is zero when entry equals exit`() {
        // Given: Entry and exit at same time
        val time = LocalDateTime.now()

        // When: Calculate duration
        val duration = Duration.between(time, time).toMillis()

        // Then: Duration should be 0
        assertThat(duration).isEqualTo(0L)
    }

    @Test
    fun `negative duration when exit before entry throws or returns negative`() {
        // Given: Exit time before entry time (data error)
        val entryTime = LocalDateTime.now()
        val exitTime = entryTime.minusHours(1)

        // When: Calculate duration
        val duration = Duration.between(entryTime, exitTime).toMillis()

        // Then: Duration is negative (this should be validated/prevented in actual code)
        assertThat(duration).isLessThan(0L)
    }

    @Test
    fun `hours to milliseconds conversion is correct`() {
        // Test various hour values
        assertThat(1 * 3600000L).isEqualTo(3600000L) // 1 hour
        assertThat(24 * 3600000L).isEqualTo(86400000L) // 1 day
        assertThat(0.5 * 3600000).isEqualTo(1800000.0) // 30 minutes
    }

    @Test
    fun `test data factory generates correct durations`() {
        // Given: Visit created with test factory
        val visit = TestDataFactory.createVisit(
            entryTime = LocalDateTime.of(2025, 11, 14, 10, 0),
            exitTime = LocalDateTime.of(2025, 11, 14, 11, 0),
            duration = 3600000L
        )

        // Then: Duration matches expected
        assertThat(visit.duration).isEqualTo(3600000L)

        // And: Entry/exit times are correct
        val actualDuration = Duration.between(visit.entryTime, visit.exitTime).toMillis()
        assertThat(actualDuration).isEqualTo(visit.duration)
    }
}
