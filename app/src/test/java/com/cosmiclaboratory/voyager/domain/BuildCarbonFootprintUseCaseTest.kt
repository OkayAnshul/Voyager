package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.BuildCarbonFootprintUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [BuildCarbonFootprintUseCase]: per-mode distance grouping, the emission
 * factors, zero-emission active modes, and the descending-by-CO2 ordering.
 */
class BuildCarbonFootprintUseCaseTest {

    private val segmentDao = mockk<MovementSegmentDao>()
    private val useCase = BuildCarbonFootprintUseCase(segmentDao)

    private val range = DateRange("2026-05-01", "2026-05-31")

    private fun segment(type: SegmentType, meters: Double) = MovementSegmentEntity(
        segmentType = type.name,
        startAt = 0L,
        endAt = 0L,
        distanceM = meters,
        dayKey = "2026-05-10"
    )

    private fun givenSegments(vararg segments: MovementSegmentEntity) {
        coEvery { segmentDao.getByTypesBetween(any(), any(), any()) } returns segments.toList()
    }

    @Test
    fun `driving distance is converted with the car emission factor`() = runTest {
        givenSegments(segment(SegmentType.DRIVE, 10_000.0))

        val footprint = useCase.build(range, "This Month")

        val drive = footprint.modes.single { it.mode == SegmentType.DRIVE }
        assertThat(drive.distanceKm).isWithin(0.001).of(10.0)
        // 10 km × 170 g/km = 1700 g = 1.7 kg
        assertThat(drive.kgCo2).isWithin(0.001).of(1.7)
        assertThat(footprint.totalKgCo2).isWithin(0.001).of(1.7)
    }

    @Test
    fun `same-mode segments are summed`() = runTest {
        givenSegments(
            segment(SegmentType.TRANSIT, 4_000.0),
            segment(SegmentType.TRANSIT, 6_000.0)
        )

        val footprint = useCase.build(range, "This Month")

        val transit = footprint.modes.single { it.mode == SegmentType.TRANSIT }
        assertThat(transit.distanceKm).isWithin(0.001).of(10.0)
        // 10 km × 41 g/km = 0.41 kg
        assertThat(transit.kgCo2).isWithin(0.001).of(0.41)
    }

    @Test
    fun `active modes appear with zero emissions`() = runTest {
        givenSegments(segment(SegmentType.WALK, 3_000.0))

        val footprint = useCase.build(range, "This Month")

        val walk = footprint.modes.single { it.mode == SegmentType.WALK }
        assertThat(walk.distanceKm).isWithin(0.001).of(3.0)
        assertThat(walk.kgCo2).isEqualTo(0.0)
        assertThat(footprint.totalKgCo2).isEqualTo(0.0)
    }

    @Test
    fun `modes are ordered by descending CO2`() = runTest {
        givenSegments(
            segment(SegmentType.WALK, 5_000.0),
            segment(SegmentType.TRANSIT, 20_000.0),
            segment(SegmentType.DRIVE, 20_000.0)
        )

        val footprint = useCase.build(range, "This Month")

        assertThat(footprint.modes.map { it.mode })
            .containsExactly(SegmentType.DRIVE, SegmentType.TRANSIT, SegmentType.WALK)
            .inOrder()
    }

    @Test
    fun `no travel yields an empty footprint`() = runTest {
        givenSegments()

        val footprint = useCase.build(range, "This Month")

        assertThat(footprint.isEmpty).isTrue()
        assertThat(footprint.totalKgCo2).isEqualTo(0.0)
    }

    @Test
    fun `tree-year equivalent scales with total CO2`() = runTest {
        // 100 km of driving → 17 kg CO2 → 17 / 21 ≈ 0.81 tree-years.
        givenSegments(segment(SegmentType.DRIVE, 100_000.0))

        val footprint = useCase.build(range, "This Month")

        assertThat(footprint.totalKgCo2).isWithin(0.001).of(17.0)
        assertThat(footprint.treeYearEquivalent).isWithin(0.01).of(17.0 / 21.0)
    }
}
