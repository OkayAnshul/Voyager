package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.IntegrityRepairUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegrityRepairUseCaseTest {

    private val visitDao = mockk<VisitDao>(relaxed = true)
    private val movementSegmentDao = mockk<MovementSegmentDao>(relaxed = true)
    private val healthLogDao = mockk<HealthLogDao>(relaxed = true)
    private val useCase = IntegrityRepairUseCase(visitDao, movementSegmentDao, healthLogDao)

    private fun openVisit(id: Long, arrivalAt: Long, dwellMs: Long? = null) = VisitEntity(
        visitId = id, placeId = 1, arrivalAt = arrivalAt, departureAt = null,
        dwellMs = dwellMs, source = "LIVE_DETECTION", dayKey = "2026-06-10"
    )

    @Test
    fun `stranded visit closes at last-known-alive, not the selection cutoff`() = runTest {
        // T3 regression: closing at the cutoff truncated the dwell by the stale-gap window.
        val arrival = 1_000_000L
        val lastKnownAlive = arrival + 3_600_000L        // 1h of real data before death
        val staleBefore = lastKnownAlive - 1_800_000L    // 30-min stale window
        coEvery { visitDao.getStaleOpenVisits(staleBefore) } returns listOf(openVisit(7, arrival))
        val departure = slot<Long>()
        val dwell = slot<Long>()
        coEvery { visitDao.endVisit(7, capture(departure), capture(dwell)) } returns Unit

        val closed = useCase.closeStaleVisits(staleBeforeMs = staleBefore, closeAtMs = lastKnownAlive)

        assertEquals(1, closed)
        assertEquals(lastKnownAlive, departure.captured)         // not staleBefore
        assertEquals(lastKnownAlive - arrival, dwell.captured)   // full hour, not 30 min
    }

    @Test
    fun `a stored dwell is preferred over close-at`() = runTest {
        val arrival = 1_000_000L
        val storedDwell = 600_000L
        coEvery { visitDao.getStaleOpenVisits(any()) } returns listOf(openVisit(8, arrival, storedDwell))
        val departure = slot<Long>()
        coEvery { visitDao.endVisit(8, capture(departure), any()) } returns Unit

        useCase.closeStaleVisits(staleBeforeMs = 5_000_000L, closeAtMs = 9_000_000L)

        assertEquals(arrival + storedDwell, departure.captured)
    }

    @Test
    fun `close-at is clamped so dwell is never negative`() = runTest {
        val arrival = 2_000_000L
        coEvery { visitDao.getStaleOpenVisits(any()) } returns listOf(openVisit(9, arrival))
        val dwell = slot<Long>()
        coEvery { visitDao.endVisit(9, any(), capture(dwell)) } returns Unit

        // Pathological closeAt earlier than arrival must not yield a negative dwell.
        useCase.closeStaleVisits(staleBeforeMs = arrival + 1, closeAtMs = arrival - 500_000L)

        assertEquals(0L, dwell.captured)
    }

    @Test
    fun `no stale visits closes nothing`() = runTest {
        coEvery { visitDao.getStaleOpenVisits(any()) } returns emptyList()
        assertEquals(0, useCase.closeStaleVisits(1L, 2L))
        coVerify(exactly = 0) { visitDao.endVisit(any(), any(), any()) }
    }
}
