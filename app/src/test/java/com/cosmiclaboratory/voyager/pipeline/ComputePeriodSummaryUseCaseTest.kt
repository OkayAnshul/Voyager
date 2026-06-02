package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.ComputePeriodSummaryUseCase
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ComputePeriodSummaryUseCaseTest {

    private val useCase = ComputePeriodSummaryUseCase(dailyRollupDao = mockk(relaxed = true))

    private fun day(
        key: String,
        dist: Double = 0.0,
        steps: Int = 0,
        active: Boolean = true
    ) = DailyRollupEntity(
        dayKey = key,
        totalDistanceM = dist,
        totalSteps = steps,
        firstActivityAt = if (active) 1L else null,
        computedAt = 0L
    )

    @Test
    fun `empty rollup list returns EMPTY`() {
        val summary = useCase.aggregate(emptyList())
        assertEquals(ComputePeriodSummaryUseCase.EMPTY, summary)
    }

    @Test
    fun `aggregates totals and best day`() {
        val days = listOf(
            day("2026-01-01", dist = 5_000.0, steps = 8_000),
            day("2026-01-02", dist = 12_000.0, steps = 15_000),
            day("2026-01-03", dist = 0.0, steps = 0, active = false),
        )
        val summary = useCase.aggregate(days)
        assertEquals(3, summary.dayCount)
        assertEquals(2, summary.activeDayCount)
        assertEquals(17_000.0, summary.totalDistanceM, 0.01)
        assertEquals(12_000.0, summary.bestDayDistanceM, 0.01)
        assertEquals("2026-01-02", summary.bestDayKey)
        assertEquals(23_000, summary.totalSteps)
    }

    @Test
    fun `avgActiveDailyDistance excludes inactive days`() {
        val days = listOf(
            day("2026-01-01", dist = 10_000.0),
            day("2026-01-02", dist = 0.0, active = false),
            day("2026-01-03", dist = 20_000.0),
        )
        val summary = useCase.aggregate(days)
        assertNotNull(summary.avgActiveDailyDistanceM)
        assertEquals(15_000.0, summary.avgActiveDailyDistanceM!!, 0.01)
    }
}
