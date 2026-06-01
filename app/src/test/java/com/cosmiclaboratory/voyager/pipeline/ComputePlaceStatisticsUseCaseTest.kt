package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.ComputePlaceStatisticsUseCase
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ComputePlaceStatisticsUseCaseTest {

    private val tz = TimeZone.getTimeZone("UTC")
    private val useCase = ComputePlaceStatisticsUseCase(visitDao = io.mockk.mockk())

    private fun atHour(year: Int, month: Int, day: Int, hour: Int): Long {
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }
        return cal.timeInMillis
    }

    private fun visit(arrival: Long, dwellHours: Double?): VisitEntity {
        val dwellMs = dwellHours?.let { (it * 3_600_000L).toLong() }
        return VisitEntity(
            placeId = 1L,
            arrivalAt = arrival,
            departureAt = dwellMs?.let { arrival + it },
            dwellMs = dwellMs,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = "2026-01-01",
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    @Test
    fun `empty list returns EMPTY`() {
        val stats = useCase.compute(emptyList(), tz)
        assertEquals(0, stats.visitCount)
        assertEquals(0L, stats.medianDwellMs)
        assertNull(stats.firstVisitAt)
    }

    @Test
    fun `percentiles are correct for known distribution`() {
        // Dwells: 1h, 2h, 3h, 4h, 5h → median = 3h, p25 = 2h, p75 = 4h
        val visits = listOf(1, 2, 3, 4, 5).mapIndexed { i, hours ->
            visit(atHour(2026, 1, i + 1, 9), hours.toDouble())
        }
        val stats = useCase.compute(visits, tz)
        assertEquals(5, stats.visitCount)
        assertEquals(5, stats.closedVisitCount)
        assertEquals(3 * 3_600_000L, stats.medianDwellMs)
        assertEquals(2 * 3_600_000L, stats.p25DwellMs)
        assertEquals(4 * 3_600_000L, stats.p75DwellMs)
        assertEquals(15 * 3_600_000L, stats.totalDwellMs)
    }

    @Test
    fun `open visits count toward total but not toward dwell stats`() {
        val visits = listOf(
            visit(atHour(2026, 1, 1, 9), 2.0),  // closed
            visit(atHour(2026, 1, 2, 9), null), // open
        )
        val stats = useCase.compute(visits, tz)
        assertEquals(2, stats.visitCount)
        assertEquals(1, stats.closedVisitCount)
        // Median over the single closed dwell.
        assertEquals(2 * 3_600_000L, stats.medianDwellMs)
    }

    @Test
    fun `hour histogram captures arrival hours`() {
        val visits = listOf(
            visit(atHour(2026, 1, 1, 9), 1.0),
            visit(atHour(2026, 1, 2, 9), 1.0),
            visit(atHour(2026, 1, 3, 18), 1.0),
        )
        val stats = useCase.compute(visits, tz)
        assertEquals(2, stats.hourHistogram[9])
        assertEquals(1, stats.hourHistogram[18])
        assertEquals(0, stats.hourHistogram[12])
    }

    @Test
    fun `first and last visit timestamps are tracked`() {
        val v1 = visit(atHour(2026, 1, 1, 9), 1.0)
        val v2 = visit(atHour(2026, 1, 5, 9), 1.0)
        val v3 = visit(atHour(2026, 1, 3, 9), 1.0)
        val stats = useCase.compute(listOf(v1, v2, v3), tz)
        assertEquals(v1.arrivalAt, stats.firstVisitAt)
        assertEquals(v2.arrivalAt, stats.lastVisitAt)
    }
}
