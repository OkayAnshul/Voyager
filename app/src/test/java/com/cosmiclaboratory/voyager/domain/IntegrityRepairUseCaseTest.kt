package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.IntegrityRepairUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
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

    private val dayKey = "2026-06-10"

    private fun openVisit(id: Long, arrivalAt: Long, dwellMs: Long? = null) = VisitEntity(
        visitId = id, placeId = 1, arrivalAt = arrivalAt, departureAt = null,
        dwellMs = dwellMs, source = "LIVE_DETECTION", dayKey = dayKey
    )

    private fun completedVisit(id: Long, arrivalAt: Long, departureAt: Long) = VisitEntity(
        visitId = id, placeId = 1, arrivalAt = arrivalAt, departureAt = departureAt,
        dwellMs = departureAt - arrivalAt, source = "LIVE_DETECTION", dayKey = dayKey
    )

    private fun seg(id: Long, type: String, startAt: Long, endAt: Long, distanceM: Double = 100.0) =
        MovementSegmentEntity(
            segmentId = id, segmentType = type, startAt = startAt, endAt = endAt,
            dayKey = dayKey, distanceM = distanceM
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

    // ── repairDay: visit-overlap clamping ──

    @Test
    fun `overlapping visits clamp the earlier departure to the next arrival`() = runTest {
        val v1 = completedVisit(1, arrivalAt = 0L, departureAt = 1_000L)   // departs after v2 arrives
        val v2 = completedVisit(2, arrivalAt = 800L, departureAt = 2_000L)
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v1, v2)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns emptyList()
        val dep = slot<Long>(); val dwell = slot<Long>()
        coEvery { visitDao.endVisit(1, capture(dep), capture(dwell)) } returns Unit

        val fixed = useCase.repairDay(dayKey)

        assertEquals(1, fixed)
        assertEquals(800L, dep.captured)    // clamped to v2's arrival
        assertEquals(800L, dwell.captured)  // 800 − 0
    }

    // ── repairDay: movement segments vs completed visits ──

    @Test
    fun `a movement segment enclosed within a visit is deleted`() = runTest {
        val v = completedVisit(1, arrivalAt = 0L, departureAt = 5_000L)
        val enclosed = seg(10, "WALK", startAt = 1_000L, endAt = 2_000L)
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns listOf(enclosed)

        val fixed = useCase.repairDay(dayKey)

        assertEquals(1, fixed)
        coVerify { movementSegmentDao.delete(enclosed) }
    }

    @Test
    fun `a segment starting before a visit is trimmed at the arrival`() = runTest {
        val v = completedVisit(1, arrivalAt = 1_000L, departureAt = 5_000L)
        val s = seg(10, "DRIVE", startAt = 0L, endAt = 2_000L) // starts before arrival, ends inside
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns listOf(s)
        val updated = slot<MovementSegmentEntity>()
        coEvery { movementSegmentDao.update(capture(updated)) } returns Unit

        val fixed = useCase.repairDay(dayKey)

        assertEquals(1, fixed)
        assertEquals(0L, updated.captured.startAt)     // start unchanged
        assertEquals(1_000L, updated.captured.endAt)   // trimmed to arrival
    }

    @Test
    fun `a segment ending after a visit is trimmed at the departure`() = runTest {
        val v = completedVisit(1, arrivalAt = 1_000L, departureAt = 5_000L)
        val s = seg(10, "DRIVE", startAt = 3_000L, endAt = 8_000L) // starts inside, ends after
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns listOf(s)
        val updated = slot<MovementSegmentEntity>()
        coEvery { movementSegmentDao.update(capture(updated)) } returns Unit

        val fixed = useCase.repairDay(dayKey)

        assertEquals(1, fixed)
        assertEquals(5_000L, updated.captured.startAt) // trimmed to departure
        assertEquals(8_000L, updated.captured.endAt)   // end unchanged
    }

    @Test
    fun `a segment spanning the whole visit is split so the post-departure travel survives`() = runTest {
        val v = completedVisit(1, arrivalAt = 2_000L, departureAt = 6_000L)
        val s = seg(10, "DRIVE", startAt = 0L, endAt = 9_000L, distanceM = 500.0) // spans the visit
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns listOf(s)
        val inserted = slot<MovementSegmentEntity>()
        val updated = slot<MovementSegmentEntity>()
        coEvery { movementSegmentDao.insert(capture(inserted)) } returns 11L
        coEvery { movementSegmentDao.update(capture(updated)) } returns Unit

        val fixed = useCase.repairDay(dayKey)

        assertEquals(1, fixed)
        // After-half inserted as a fresh row covering [departure, originalEnd] with no distance.
        assertEquals(0L, inserted.captured.segmentId)
        assertEquals(6_000L, inserted.captured.startAt)
        assertEquals(9_000L, inserted.captured.endAt)
        assertEquals("DRIVE", inserted.captured.segmentType)
        assertEquals(0.0, inserted.captured.distanceM, 0.0)
        // Original kept as the before-half [start, arrival], distance preserved (no inflation).
        assertEquals(0L, updated.captured.startAt)
        assertEquals(2_000L, updated.captured.endAt)
        assertEquals(500.0, updated.captured.distanceM, 0.0)
        coVerify(exactly = 0) { movementSegmentDao.delete(any()) }
    }

    @Test
    fun `stationary segments overlapping a visit are left untouched`() = runTest {
        val v = completedVisit(1, arrivalAt = 0L, departureAt = 5_000L)
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns listOf(
            seg(10, "DWELL", startAt = 1_000L, endAt = 2_000L),
            seg(11, "GAP", startAt = 1_500L, endAt = 6_000L),
            seg(12, "VISIT", startAt = 0L, endAt = 5_000L)
        )

        val fixed = useCase.repairDay(dayKey)

        assertEquals(0, fixed)
        coVerify(exactly = 0) { movementSegmentDao.delete(any()) }
        coVerify(exactly = 0) { movementSegmentDao.update(any()) }
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) }
    }

    @Test
    fun `a clean day with no overlaps repairs nothing`() = runTest {
        val v = completedVisit(1, arrivalAt = 0L, departureAt = 1_000L)
        coEvery { visitDao.getByDayKey(dayKey) } returns listOf(v)
        coEvery { movementSegmentDao.getByDayKey(dayKey) } returns listOf(
            seg(10, "WALK", startAt = 2_000L, endAt = 3_000L) // entirely after the visit
        )

        assertEquals(0, useCase.repairDay(dayKey))
    }
}
