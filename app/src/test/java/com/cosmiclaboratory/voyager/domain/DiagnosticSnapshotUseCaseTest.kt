package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.DiagnosticSnapshotUseCase
import com.cosmiclaboratory.voyager.platform.battery.BatteryUsageReporter
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests [DiagnosticSnapshotUseCase]: the read-only "is Voyager behaving?" aggregation —
 * battery passthrough, the 24h sample/event window, and the worker-failure count.
 */
class DiagnosticSnapshotUseCaseTest {

    private val battery = mockk<BatteryUsageReporter>()
    private val rawLocationSampleDao = mockk<RawLocationSampleDao>()
    private val healthLogDao = mockk<HealthLogDao>()
    private val useCase = DiagnosticSnapshotUseCase(battery, rawLocationSampleDao, healthLogDao)

    private val day = 24L * 60 * 60 * 1000

    private fun event(type: String) = HealthLogEntity(eventType = type, eventAt = 0L, detailsJson = "{}")

    @Test
    fun `snapshot maps battery, sample count, failures, and total events`() = runTest {
        coEvery { battery.estimate(any()) } returns BatteryUsageReporter.Estimate(percentPerDay = 12, measuredOverHours = 48)
        coEvery { rawLocationSampleDao.getByTimeRange(any(), any()) } returns
            List(5) { mockk<RawLocationSampleEntity>(relaxed = true) }
        coEvery { healthLogDao.getEventsSince(any()) } returns listOf(
            event("WORKER_FAILURE"), event("WORKER_FAILURE"),
            event("WORKER_COMPLETE"), event("SAMPLE_GAP")
        )

        val snap = useCase.snapshot(now = 1_000_000_000_000L)

        assertThat(snap.batteryPctPerDay).isEqualTo(12)
        assertThat(snap.batteryMeasuredOverHours).isEqualTo(48)
        assertThat(snap.sampleCountLast24h).isEqualTo(5)
        assertThat(snap.workerFailuresLast24h).isEqualTo(2) // only WORKER_FAILURE rows
        assertThat(snap.healthEventsLast24h).isEqualTo(4)   // every event
    }

    @Test
    fun `the sample and event windows are measured back 24h from now`() = runTest {
        val now = 1_700_000_000_000L
        coEvery { battery.estimate(any()) } returns BatteryUsageReporter.Estimate(null, 0)
        coEvery { rawLocationSampleDao.getByTimeRange(any(), any()) } returns emptyList()
        coEvery { healthLogDao.getEventsSince(any()) } returns emptyList()

        useCase.snapshot(now = now)

        coVerify { rawLocationSampleDao.getByTimeRange(now - day, now) }
        coVerify { healthLogDao.getEventsSince(now - day) }
        coVerify { battery.estimate(now) }
    }

    @Test
    fun `a null battery estimate passes through unchanged`() = runTest {
        coEvery { battery.estimate(any()) } returns BatteryUsageReporter.Estimate(percentPerDay = null, measuredOverHours = 3)
        coEvery { rawLocationSampleDao.getByTimeRange(any(), any()) } returns emptyList()
        coEvery { healthLogDao.getEventsSince(any()) } returns emptyList()

        val snap = useCase.snapshot(now = 1L)

        assertThat(snap.batteryPctPerDay).isNull()
        assertThat(snap.batteryMeasuredOverHours).isEqualTo(3)
    }

    @Test
    fun `no activity yields a clean zero snapshot`() = runTest {
        coEvery { battery.estimate(any()) } returns BatteryUsageReporter.Estimate(null, 0)
        coEvery { rawLocationSampleDao.getByTimeRange(any(), any()) } returns emptyList()
        coEvery { healthLogDao.getEventsSince(any()) } returns emptyList()

        val snap = useCase.snapshot(now = 1L)

        assertThat(snap.sampleCountLast24h).isEqualTo(0)
        assertThat(snap.workerFailuresLast24h).isEqualTo(0)
        assertThat(snap.healthEventsLast24h).isEqualTo(0)
    }
}
