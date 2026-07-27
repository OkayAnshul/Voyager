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

class AnalyzeCommuteUseCaseTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val useCase = AnalyzeCommuteUseCase(
        placeDao = mockk<PlaceDao>(relaxed = true),
        visitDao = mockk<VisitDao>(relaxed = true),
    )

    private fun visit(placeId: Long, day: Int, arrH: Int, arrM: Int, depH: Int, depM: Int): VisitEntity {
        val arrival = LocalDate.of(2026, 1, day).atTime(arrH, arrM).atZone(zone).toInstant().toEpochMilli()
        val departure = LocalDate.of(2026, 1, day).atTime(depH, depM).atZone(zone).toInstant().toEpochMilli()
        return VisitEntity(
            placeId = placeId,
            arrivalAt = arrival,
            departureAt = departure,
            dwellMs = departure - arrival,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = "2026-01-%02d".format(day),
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    /** home(07-08) → work(08:30-17) → home-evening(17:30-18:30), for [days] days. */
    private fun commuteWeek(days: IntRange): List<VisitEntity> = days.flatMap { d ->
        listOf(
            visit(1L, d, 7, 0, 8, 0),
            visit(2L, d, 8, 30, 17, 0),
            visit(1L, d, 17, 30, 18, 30),
        )
    }

    @Test
    fun `four regular days yield both legs with 30-minute medians`() {
        val stats = useCase.detect(1L, setOf(2L), commuteWeek(6..9), zone)!!
        val toWork = stats.toWork!!
        assertEquals(4, toWork.samples)
        assertEquals(30 * 60_000L, toWork.medianDurationMs)
        assertEquals(8 * 60, toWork.typicalDepartureMinuteOfDay) // left home ~08:00
        val toHome = stats.toHome!!
        assertEquals(4, toHome.samples)
        assertEquals(30 * 60_000L, toHome.medianDurationMs)
        assertEquals(17 * 60, toHome.typicalDepartureMinuteOfDay) // left work ~17:00
    }

    @Test
    fun `fewer than three commutes reports no leg`() {
        val stats = useCase.detect(1L, setOf(2L), commuteWeek(6..7), zone) // 2 days
        assertNull(stats)
    }

    @Test
    fun `an over-long gap is not counted as a commute`() {
        // Home leaves 08:00, arrives work 12:00 — a 4h gap exceeds the 3h cap.
        val visits = (6..9).flatMap { d ->
            listOf(visit(1L, d, 7, 0, 8, 0), visit(2L, d, 12, 0, 17, 0))
        }
        val stats = useCase.detect(1L, setOf(2L), visits, zone)
        assertNull(stats)
    }
}
