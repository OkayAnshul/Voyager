package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ComputeTrackingStreakUseCaseTest {

    private val useCase = ComputeTrackingStreakUseCase(mockk<DailyRollupDao>(relaxed = true))
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `current streak counts back from today`() {
        val days = setOf(d("2026-07-24"), d("2026-07-25"), d("2026-07-26"))
        assertEquals(3, useCase.currentStreak(days, today = d("2026-07-26")))
    }

    @Test
    fun `current streak counts from yesterday when today not yet active`() {
        val days = setOf(d("2026-07-24"), d("2026-07-25"))
        assertEquals(2, useCase.currentStreak(days, today = d("2026-07-26")))
    }

    @Test
    fun `current streak is zero when neither today nor yesterday is active`() {
        val days = setOf(d("2026-07-20"), d("2026-07-21"))
        assertEquals(0, useCase.currentStreak(days, today = d("2026-07-26")))
    }

    @Test
    fun `current streak stops at the first gap`() {
        val days = setOf(d("2026-07-26"), d("2026-07-25"), d("2026-07-23")) // gap on the 24th
        assertEquals(2, useCase.currentStreak(days, today = d("2026-07-26")))
    }

    @Test
    fun `longest streak finds the longest consecutive run`() {
        val days = setOf(
            d("2026-07-01"),
            d("2026-07-05"), d("2026-07-06"), d("2026-07-07"), d("2026-07-08"), // run of 4
            d("2026-07-10"), d("2026-07-11")
        )
        assertEquals(4, useCase.longestStreak(days))
    }

    @Test
    fun `longest streak of an empty set is zero`() {
        assertEquals(0, useCase.longestStreak(emptySet()))
    }
}
