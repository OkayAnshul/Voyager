package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ComputeDayRhythmUseCaseTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val useCase = ComputeDayRhythmUseCase(
        placeDao = mockk<PlaceDao>(relaxed = true),
        visitDao = mockk<VisitDao>(relaxed = true),
    )

    private fun visit(placeId: Long, date: LocalDate, arrH: Int, depH: Int, depDayOffset: Int = 0): VisitEntity {
        val arrival = date.atTime(arrH, 0).atZone(zone).toInstant().toEpochMilli()
        val departure = date.plusDays(depDayOffset.toLong()).atTime(depH, 0).atZone(zone).toInstant().toEpochMilli()
        return VisitEntity(
            placeId = placeId,
            arrivalAt = arrival,
            departureAt = departure,
            dwellMs = departure - arrival,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = date.toString(),
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    @Test
    fun `weekday band reflects home overnight, work by day, and gaps between`() {
        // Mon 2026-01-05 .. Fri 2026-01-09
        val start = LocalDate.of(2026, 1, 5)
        val end = LocalDate.of(2026, 1, 9)
        val visits = (0..4).flatMap { i ->
            val d = start.plusDays(i.toLong())
            listOf(
                visit(1L, d, 0, 8),                 // home morning
                visit(2L, d, 9, 17),                // work
                visit(1L, d, 18, 0, depDayOffset = 1), // home evening → midnight
            )
        }
        val nowMs = LocalDate.of(2026, 1, 10).atStartOfDay(zone).toInstant().toEpochMilli()

        val rhythm = useCase.compute(visits, homeId = 1L, workIds = setOf(2L), start, end, nowMs, zone)

        assertEquals(DayState.HOME, rhythm.weekday[3].state)   // 03:30 at home
        assertEquals(DayState.WORK, rhythm.weekday[12].state)  // 12:30 at work
        assertEquals(DayState.HOME, rhythm.weekday[20].state)  // 20:30 at home
        assertEquals(DayState.AWAY, rhythm.weekday[8].state)   // 08:30 between home and work
        assertEquals(5, rhythm.daysObserved)
    }

    @Test
    fun `unsampled buckets read as away with zero confidence`() {
        // A single Monday range: weekday hours are observed (nothing covers them,
        // so confidently AWAY); weekend hours are never sampled at all.
        val start = LocalDate.of(2026, 1, 5) // Monday
        val end = LocalDate.of(2026, 1, 5)
        val nowMs = LocalDate.of(2026, 1, 6).atStartOfDay(zone).toInstant().toEpochMilli()
        val rhythm = useCase.compute(emptyList(), 1L, setOf(2L), start, end, nowMs, zone)

        assertEquals(DayState.AWAY, rhythm.weekday[12].state)   // observed, uncovered
        assertEquals(1f, rhythm.weekday[12].confidence, 0.0001f)

        assertEquals(DayState.AWAY, rhythm.weekend[12].state)   // never sampled
        assertEquals(0f, rhythm.weekend[12].confidence, 0.0001f)
    }
}
