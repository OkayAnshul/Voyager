package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.DetectRecurringPatternsUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DetectRecurringPatternsUseCaseTest {

    private val tz = TimeZone.getTimeZone("UTC")
    private val useCase = DetectRecurringPatternsUseCase(
        visitDao = mockk<VisitDao>(relaxed = true),
        placeDao = mockk<PlaceDao>(relaxed = true)
    )

    private fun visit(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): VisitEntity {
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        val arrival = cal.timeInMillis
        val dwell = 60 * 60_000L
        return VisitEntity(
            placeId = 1L,
            arrivalAt = arrival,
            departureAt = arrival + dwell,
            dwellMs = dwell,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = "2026-${"%02d".format(month)}-${"%02d".format(day)}",
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    @Test
    fun `fewer than 4 visits produce no pattern`() {
        val visits = listOf(visit(2026, 1, 6, 19), visit(2026, 1, 13, 19), visit(2026, 1, 20, 19))
        assertTrue(useCase.detect(1L, visits, tz).isEmpty())
    }

    @Test
    fun `weekly Tuesday 7pm visits produce a single Tuesday pattern`() {
        // 6 Tuesdays in Jan-Feb 2026 (Tue is day 3 in Calendar: SUN=1).
        // Jan 6, 13, 20, 27, Feb 3, 10
        val visits = listOf(
            visit(2026, 1, 6, 19),
            visit(2026, 1, 13, 19),
            visit(2026, 1, 20, 19),
            visit(2026, 1, 27, 19),
            visit(2026, 2, 3, 19),
            visit(2026, 2, 10, 19),
        )
        val patterns = useCase.detect(1L, visits, tz)
        assertEquals(1, patterns.size)
        val tuesdayPattern = patterns.single()
        assertEquals(Calendar.TUESDAY, tuesdayPattern.dayOfWeek)
        assertEquals(19, tuesdayPattern.typicalHour)
        assertEquals(6, tuesdayPattern.visitCount)
        assertTrue("confidence in valid range", tuesdayPattern.confidence in 0.4f..0.95f)
    }

    @Test
    fun `±1h spread is still a single pattern`() {
        // Tuesdays at 18:30, 19:00, 19:15, 19:30, 19:45, 20:00 — all "around 7pm".
        // After hour bucketing: 18, 19, 19, 19, 19, 20. Adjacent ±1h merged.
        val visits = listOf(
            visit(2026, 1, 6, 18, 30),
            visit(2026, 1, 13, 19, 0),
            visit(2026, 1, 20, 19, 15),
            visit(2026, 1, 27, 19, 30),
            visit(2026, 2, 3, 19, 45),
            visit(2026, 2, 10, 20, 0),
        )
        val patterns = useCase.detect(1L, visits, tz)
        assertEquals("Adjacent hours merge into one pattern", 1, patterns.size)
        assertEquals(6, patterns.single().visitCount)
    }

    @Test
    fun `wide hour scatter on same day produces no pattern`() {
        // Same day of week but hours all over: 9, 14, 19, 7, 22 — high std dev.
        val visits = listOf(
            visit(2026, 1, 6, 9),
            visit(2026, 1, 13, 14),
            visit(2026, 1, 20, 19),
            visit(2026, 1, 27, 7),
            visit(2026, 2, 3, 22),
        )
        val patterns = useCase.detect(1L, visits, tz)
        // All scatter — no single bucket has ≥4 with tight stddev.
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun `different weekdays produce separate patterns`() {
        // 4 Mondays at 9am AND 4 Wednesdays at 7pm — two patterns.
        val visits = listOf(
            // Mondays in Jan-Feb 2026: 5, 12, 19, 26
            visit(2026, 1, 5, 9),
            visit(2026, 1, 12, 9),
            visit(2026, 1, 19, 9),
            visit(2026, 1, 26, 9),
            // Wednesdays: 7, 14, 21, 28
            visit(2026, 1, 7, 19),
            visit(2026, 1, 14, 19),
            visit(2026, 1, 21, 19),
            visit(2026, 1, 28, 19),
        )
        val patterns = useCase.detect(1L, visits, tz)
        assertEquals(2, patterns.size)
        val byDow = patterns.associateBy { it.dayOfWeek }
        assertEquals(9, byDow[Calendar.MONDAY]?.typicalHour)
        assertEquals(19, byDow[Calendar.WEDNESDAY]?.typicalHour)
    }
}
