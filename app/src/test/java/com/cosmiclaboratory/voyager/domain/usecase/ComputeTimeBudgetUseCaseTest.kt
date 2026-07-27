package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ComputeTimeBudgetUseCaseTest {

    private val useCase = ComputeTimeBudgetUseCase(
        placeDao = mockk<PlaceDao>(relaxed = true),
        visitDao = mockk<VisitDao>(relaxed = true),
        dailyRollupDao = mockk<DailyRollupDao>(relaxed = true),
    )

    private val hour = 3_600_000L

    private fun visit(placeId: Long, startHour: Double, endHour: Double?): VisitEntity {
        val arrival = (startHour * hour).toLong()
        val departure = endHour?.let { (it * hour).toLong() }
        return VisitEntity(
            placeId = placeId,
            arrivalAt = arrival,
            departureAt = departure,
            dwellMs = departure?.let { it - arrival },
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = "2026-01-06",
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    @Test
    fun `splits a day across home, work, out, moving and untracked`() {
        val visits = listOf(
            visit(1L, 0.0, 8.0),     // home 8h
            visit(2L, 9.0, 17.0),    // work 8h
            visit(3L, 17.5, 18.5),   // out 1h
            visit(1L, 20.0, 24.0),   // home 4h
        )
        val budget = useCase.compute(
            visits = visits,
            homeId = 1L,
            workIds = setOf(2L),
            movingMs = 2 * hour,
            windowStartMs = 0L,
            windowEndMs = 24 * hour
        )
        assertEquals(12 * hour, budget.homeMs)
        assertEquals(8 * hour, budget.workMs)
        assertEquals(1 * hour, budget.outMs)
        assertEquals(2 * hour, budget.movingMs)
        assertEquals(1 * hour, budget.untrackedMs)
        assertEquals(24 * hour, budget.totalMs)
    }

    @Test
    fun `dwell is clamped to the window and open visits run to window end`() {
        val visits = listOf(
            visit(1L, -2.0, 2.0),   // starts before window: only 2h counts
            visit(3L, 22.0, null),  // open visit: runs to window end → 2h
        )
        val budget = useCase.compute(
            visits = visits,
            homeId = 1L,
            workIds = emptySet(),
            movingMs = 0L,
            windowStartMs = 0L,
            windowEndMs = 24 * hour
        )
        assertEquals(2 * hour, budget.homeMs)
        assertEquals(2 * hour, budget.outMs)
    }

    @Test
    fun `placeId zero is ignored`() {
        val visits = listOf(visit(0L, 0.0, 5.0))
        val budget = useCase.compute(visits, 1L, emptySet(), 0L, 0L, 24 * hour)
        assertTrue(budget.isEmpty)
    }
}
