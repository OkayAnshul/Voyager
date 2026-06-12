package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DashboardViewModelTest {

    private val today = LocalDate.of(2026, 6, 12)
    private fun key(d: LocalDate) = d.toString()

    @Test
    fun `streak counts today plus the run of active days behind it`() {
        val days = (0..4).map { key(today.minusDays(it.toLong())) }.toSet() // today + 4 prior
        assertEquals(5, DashboardViewModel.computeStreak(days, today))
    }

    @Test
    fun `streak survives a not-yet-active today by counting from yesterday`() {
        // Mon-Fri active, today (Sat) not active yet — the streak isn't broken, show 5 not 0.
        val days = (1..5).map { key(today.minusDays(it.toLong())) }.toSet()
        assertEquals(5, DashboardViewModel.computeStreak(days, today))
    }

    @Test
    fun `a gap breaks the streak`() {
        // today + yesterday active, then a missing day, then more — only the recent run counts.
        val days = setOf(
            key(today), key(today.minusDays(1)),
            // gap at day-2
            key(today.minusDays(3)), key(today.minusDays(4))
        )
        assertEquals(2, DashboardViewModel.computeStreak(days, today))
    }

    @Test
    fun `no activity at all is a zero streak`() {
        assertEquals(0, DashboardViewModel.computeStreak(emptySet(), today))
    }

    @Test
    fun `neither today nor yesterday active is a zero streak`() {
        val days = setOf(key(today.minusDays(2)), key(today.minusDays(3)))
        assertEquals(0, DashboardViewModel.computeStreak(days, today))
    }
}
