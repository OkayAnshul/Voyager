package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.usecase.BuildMileageLogUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.MileageClassificationDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.entity.MileageClassificationEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [BuildMileageLogUseCase]: the drive↔classification join, derived totals,
 * and the "absence of a row = unclassified" classification semantics.
 */
class BuildMileageLogUseCaseTest {

    private val segmentDao = mockk<MovementSegmentDao>()
    private val classificationDao = mockk<MileageClassificationDao>()
    private val useCase = BuildMileageLogUseCase(segmentDao, classificationDao)

    private fun drive(id: Long, meters: Double) = MovementSegmentEntity(
        segmentId = id,
        segmentType = "DRIVE",
        startAt = id * 1000,
        endAt = id * 1000 + 500,
        distanceM = meters,
        dayKey = "2026-05-01"
    )

    @Test
    fun `drives with no classification are unclassified`() = runTest {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns
            listOf(drive(1, 1609.344), drive(2, 3218.688))
        coEvery { classificationDao.observeAll() } returns flowOf(emptyList())

        val log = useCase.observeLog(DateRangePeriod.Last30Days).first()

        assertThat(log.entries).hasSize(2)
        assertThat(log.entries.all { it.purpose == MileagePurpose.UNCLASSIFIED }).isTrue()
        assertThat(log.unclassifiedCount).isEqualTo(2)
        assertThat(log.totalMiles).isWithin(0.001).of(3.0)
        assertThat(log.deductibleMeters).isEqualTo(0.0)
    }

    @Test
    fun `classifications join onto matching segments and feed totals`() = runTest {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns
            listOf(drive(1, 1609.344), drive(2, 1609.344), drive(3, 1609.344))
        coEvery { classificationDao.observeAll() } returns flowOf(
            listOf(
                MileageClassificationEntity(segmentId = 1, purpose = "BUSINESS"),
                MileageClassificationEntity(segmentId = 2, purpose = "PERSONAL")
            )
        )

        val log = useCase.observeLog(DateRangePeriod.Last30Days).first()

        assertThat(log.milesFor(MileagePurpose.BUSINESS)).isWithin(0.001).of(1.0)
        assertThat(log.milesFor(MileagePurpose.PERSONAL)).isWithin(0.001).of(1.0)
        assertThat(log.unclassifiedCount).isEqualTo(1)
        // Only business is deductible here.
        assertThat(log.deductibleMeters).isWithin(0.001).of(1609.344)
    }

    @Test
    fun `classify business upserts a row with a bumped revision`() = runTest {
        coEvery { classificationDao.getBySegmentId(7) } returns
            MileageClassificationEntity(segmentId = 7, purpose = "PERSONAL", revision = 4)
        val saved = slot<MileageClassificationEntity>()
        coEvery { classificationDao.upsert(capture(saved)) } just Runs

        useCase.classify(7, MileagePurpose.BUSINESS, note = "client site")

        assertThat(saved.captured.purpose).isEqualTo("BUSINESS")
        assertThat(saved.captured.note).isEqualTo("client site")
        assertThat(saved.captured.revision).isEqualTo(5)
    }

    @Test
    fun `classify as unclassified with no note deletes the row`() = runTest {
        coEvery { classificationDao.deleteBySegmentId(9) } just Runs

        useCase.classify(9, MileagePurpose.UNCLASSIFIED)

        coVerify { classificationDao.deleteBySegmentId(9) }
    }

    @Test
    fun `medical and charitable both count toward deductible meters`() = runTest {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns
            listOf(drive(1, 1609.344), drive(2, 1609.344), drive(3, 1609.344), drive(4, 1609.344))
        coEvery { classificationDao.observeAll() } returns flowOf(
            listOf(
                MileageClassificationEntity(segmentId = 1, purpose = "BUSINESS"),
                MileageClassificationEntity(segmentId = 2, purpose = "MEDICAL"),
                MileageClassificationEntity(segmentId = 3, purpose = "CHARITABLE"),
                MileageClassificationEntity(segmentId = 4, purpose = "PERSONAL")
            )
        )

        val log = useCase.observeLog(DateRangePeriod.Last30Days).first()

        // Business + medical + charitable are deductible; personal is not.
        assertThat(log.deductibleMeters).isWithin(0.001).of(3 * 1609.344)
        assertThat(log.milesFor(MileagePurpose.MEDICAL)).isWithin(0.001).of(1.0)
        assertThat(log.milesFor(MileagePurpose.CHARITABLE)).isWithin(0.001).of(1.0)
    }

    @Test
    fun `a purpose with no drives reports zero miles`() = runTest {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns listOf(drive(1, 1609.344))
        coEvery { classificationDao.observeAll() } returns flowOf(
            listOf(MileageClassificationEntity(segmentId = 1, purpose = "BUSINESS"))
        )

        val log = useCase.observeLog(DateRangePeriod.Last30Days).first()

        assertThat(log.milesFor(MileagePurpose.MEDICAL)).isEqualTo(0.0)
    }

    @Test
    fun `an unknown stored purpose falls back to unclassified`() = runTest {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns listOf(drive(1, 1609.344))
        coEvery { classificationDao.observeAll() } returns flowOf(
            listOf(MileageClassificationEntity(segmentId = 1, purpose = "LEGACY_GARBAGE"))
        )

        val log = useCase.observeLog(DateRangePeriod.Last30Days).first()

        assertThat(log.entries.single().purpose).isEqualTo(MileagePurpose.UNCLASSIFIED)
        assertThat(log.deductibleMeters).isEqualTo(0.0)
    }

    @Test
    fun `a classification for a segment outside the range is ignored`() = runTest {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns listOf(drive(1, 1609.344))
        coEvery { classificationDao.observeAll() } returns flowOf(
            listOf(
                MileageClassificationEntity(segmentId = 1, purpose = "BUSINESS"),
                MileageClassificationEntity(segmentId = 99, purpose = "BUSINESS") // no such drive in range
            )
        )

        val log = useCase.observeLog(DateRangePeriod.Last30Days).first()

        assertThat(log.entries).hasSize(1)
        assertThat(log.entries.single().segmentId).isEqualTo(1L)
    }

    @Test
    fun `classify as unclassified WITH a note keeps the row so the note survives`() = runTest {
        coEvery { classificationDao.getBySegmentId(5) } returns null
        val saved = slot<MileageClassificationEntity>()
        coEvery { classificationDao.upsert(capture(saved)) } just Runs

        useCase.classify(5, MileagePurpose.UNCLASSIFIED, note = "toll receipt attached")

        assertThat(saved.captured.purpose).isEqualTo("UNCLASSIFIED")
        assertThat(saved.captured.note).isEqualTo("toll receipt attached")
        assertThat(saved.captured.revision).isEqualTo(1L)
    }

    @Test
    fun `clearClassification deletes the row`() = runTest {
        coEvery { classificationDao.deleteBySegmentId(3) } just Runs

        useCase.clearClassification(3)

        coVerify { classificationDao.deleteBySegmentId(3) }
    }
}
