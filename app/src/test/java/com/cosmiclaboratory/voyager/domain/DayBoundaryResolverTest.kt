package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.util.DayBoundaryResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class DayBoundaryResolverTest {

    private val resolver = DayBoundaryResolver()
    private val tz = "UTC"
    private val hour = 3_600_000L

    @Test
    fun `overlap is the full interval when it sits inside the window`() {
        val s = resolver.getDayStartEpochMs("2026-06-10", tz)
        val e = resolver.getDayEndEpochMs("2026-06-10", tz)
        assertEquals(2 * hour, resolver.overlapMs(s + 9 * hour, s + 11 * hour, s, e))
    }

    @Test
    fun `overlap is zero when the interval is outside the window`() {
        val s = resolver.getDayStartEpochMs("2026-06-10", tz)
        val e = resolver.getDayEndEpochMs("2026-06-10", tz)
        assertEquals(0L, resolver.overlapMs(e + hour, e + 2 * hour, s, e))
    }

    @Test
    fun `overnight stay splits across both days and sums to the full dwell (T11)`() {
        val day1Start = resolver.getDayStartEpochMs("2026-06-10", tz)
        val day1End = resolver.getDayEndEpochMs("2026-06-10", tz)
        val day2Start = resolver.getDayStartEpochMs("2026-06-11", tz)
        val day2End = resolver.getDayEndEpochMs("2026-06-11", tz)
        // Arrive 22:00 on the 10th, leave 07:00 on the 11th = 9h dwell.
        val arrival = day1Start + 22 * hour
        val departure = day2Start + 7 * hour

        val day1Portion = resolver.overlapMs(arrival, departure, day1Start, day1End)
        val day2Portion = resolver.overlapMs(arrival, departure, day2Start, day2End)

        assertEquals(2 * hour, day1Portion)  // 22:00 → midnight
        assertEquals(7 * hour, day2Portion)  // midnight → 07:00
        // The whole point of T11: each day gets its share and nothing is double- or under-counted.
        assertEquals(departure - arrival, day1Portion + day2Portion)
    }

    @Test
    fun `consecutive day windows are contiguous and 24h long`() {
        val s = resolver.getDayStartEpochMs("2026-06-10", tz)
        val e = resolver.getDayEndEpochMs("2026-06-10", tz)
        assertEquals(24 * hour, e - s)
        assertEquals(e, resolver.getDayStartEpochMs("2026-06-11", tz)) // day N end == day N+1 start
    }
}
