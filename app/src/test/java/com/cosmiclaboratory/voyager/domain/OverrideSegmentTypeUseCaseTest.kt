package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.OverrideSegmentTypeUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverrideSegmentTypeUseCaseTest {

    private val segmentDao = mockk<MovementSegmentDao>(relaxed = true)
    private val routeDao = mockk<RouteDao>(relaxed = true)
    private val healthLogDao = mockk<HealthLogDao>(relaxed = true)
    private val useCase = OverrideSegmentTypeUseCase(segmentDao, routeDao, healthLogDao)

    private fun segment(type: String, routeId: Long? = 10L) = MovementSegmentEntity(
        segmentId = 1, segmentType = type, startAt = 0, endAt = 1000, dayKey = "2026-06-12", routeId = routeId
    )

    private fun route(transportMode: String) = RouteEntity(
        routeId = 10, segmentId = 1, encodedPolyline = "x", totalDistanceM = 100.0,
        totalDurationMs = 60_000, avgSpeedMps = 1.6f, maxSpeedMps = 2f,
        transportMode = transportMode, sampleCount = 5
    )

    @Test
    fun `applying an override sets it, syncs the route, and logs`() = runTest {
        coEvery { segmentDao.getById(1) } returns segment("WALK")
        coEvery { routeDao.getById(10) } returns route("WALK")
        val overrideAt = slot<Long?>()
        coEvery { segmentDao.setUserOverride(1, "DRIVE", captureNullable(overrideAt)) } returns Unit

        useCase.setOverride(1, SegmentType.DRIVE)

        assertEquals("override timestamp is set", true, overrideAt.captured != null)
        coVerify { routeDao.update(match { it.transportMode == "DRIVE" }) }
        coVerify { healthLogDao.insert(match { it.eventType == "USER_OVERRIDE_SEGMENT_TYPE" }) }
    }

    @Test
    fun `clearing reverts the route to the classifier label and logs the clear`() = runTest {
        coEvery { segmentDao.getById(1) } returns segment("WALK")     // classifier said WALK
        coEvery { routeDao.getById(10) } returns route("DRIVE")        // route was overridden to DRIVE
        val overrideAt = slot<Long?>()
        coEvery { segmentDao.setUserOverride(1, null, captureNullable(overrideAt)) } returns Unit

        useCase.setOverride(1, null)

        assertNull("clearing nulls the override timestamp", overrideAt.captured)
        coVerify { routeDao.update(match { it.transportMode == "WALK" }) } // reverts to classifier
        coVerify { healthLogDao.insert(match { it.eventType == "USER_CLEARED_SEGMENT_OVERRIDE" }) }
    }

    @Test
    fun `an unknown segment is a no-op`() = runTest {
        coEvery { segmentDao.getById(99) } returns null
        useCase.setOverride(99, SegmentType.DRIVE)
        coVerify(exactly = 0) { segmentDao.setUserOverride(any(), any(), any()) }
        coVerify(exactly = 0) { routeDao.update(any()) }
        coVerify(exactly = 0) { healthLogDao.insert(any<HealthLogEntity>()) }
    }

    @Test
    fun `a route already on the effective type is not rewritten`() = runTest {
        coEvery { segmentDao.getById(1) } returns segment("WALK")
        coEvery { routeDao.getById(10) } returns route("DRIVE") // already DRIVE
        useCase.setOverride(1, SegmentType.DRIVE)
        coVerify(exactly = 0) { routeDao.update(any()) } // no redundant write
    }
}
