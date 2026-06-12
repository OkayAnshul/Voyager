package com.cosmiclaboratory.voyager.presentation.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayNavigationStateHolderTest {

    private val holder = DayNavigationStateHolder()

    @Test
    fun `navigate next never goes past today`() {
        val today = holder.currentDayKey.value // defaults to today
        holder.navigateNextDay()
        assertEquals("can't navigate into the future", today, holder.currentDayKey.value)
    }

    @Test
    fun `navigate next is allowed on a past day`() {
        holder.navigateToDay("2020-01-01")
        holder.navigateNextDay()
        assertEquals("2020-01-02", holder.currentDayKey.value)
    }

    @Test
    fun `navigate previous crosses a month boundary correctly`() {
        holder.navigateToDay("2026-03-01")
        holder.navigatePreviousDay()
        assertEquals("2026-02-28", holder.currentDayKey.value) // 2026 is not a leap year
    }

    @Test
    fun `focusing a segment clears any focused visit, and vice versa`() {
        holder.focusVisit(9)
        holder.focusSegment(5)
        assertEquals(5L, holder.focusedSegmentId.value)
        assertNull(holder.focusedVisitId.value)

        holder.focusVisit(9)
        assertEquals(9L, holder.focusedVisitId.value)
        assertNull(holder.focusedSegmentId.value)
    }

    @Test
    fun `changing day clears focus`() {
        holder.focusSegment(5)
        holder.navigateToDay("2025-12-25")
        assertNull(holder.focusedSegmentId.value)
        assertNull(holder.focusedVisitId.value)
    }

    @Test
    fun `a malformed day key leaves the day unchanged`() {
        holder.navigateToDay("not-a-date")
        holder.navigatePreviousDay() // offsetDay falls back to the same key
        assertEquals("not-a-date", holder.currentDayKey.value)
    }
}
