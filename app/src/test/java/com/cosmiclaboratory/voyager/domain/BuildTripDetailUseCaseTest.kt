package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.BuildTripDetailUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.TripDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TripEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests [BuildTripDetailUseCase]: the day-by-day breakdown that powers the trip detail screen
 * and the PDF trip-book — chronological places, per-day distance, empty-day omission, and the
 * deleted-visit / unresolved-place handling.
 */
class BuildTripDetailUseCaseTest {

    private val tripDao = mockk<TripDao>()
    private val visitDao = mockk<VisitDao>()
    private val placeDao = mockk<PlaceDao>()
    private val segmentDao = mockk<MovementSegmentDao>(relaxed = true)
    private val useCase = BuildTripDetailUseCase(tripDao, visitDao, placeDao, segmentDao)

    private fun trip(id: Long, start: String, end: String) = TripEntity(
        tripId = id, startDayKey = start, endDayKey = end, title = "Trip",
        placeCount = 0, visitCount = 0, distanceMeters = 0.0, isOngoing = false, detectedAt = 0L
    )

    private fun visit(placeId: Long, dayKey: String, arrival: Long, deleted: Boolean = false) = VisitEntity(
        placeId = placeId, arrivalAt = arrival, dwellMs = 0L, source = "TEST", dayKey = dayKey,
        deletedAt = if (deleted) 1L else null
    )

    private fun place(id: Long, name: String?) = PlaceEntity(
        placeId = id, centroidLat = 0.0, centroidLng = 0.0, geohash = "x",
        userDisplayName = name, createdAt = 0L
    )

    private fun seg(dayKey: String, distanceM: Double) =
        MovementSegmentEntity(segmentType = "DRIVE", startAt = 0L, endAt = 0L, distanceM = distanceM, dayKey = dayKey)

    @Test
    fun `an unknown trip id yields null`() = runTest {
        coEvery { tripDao.getById(404L) } returns null
        assertThat(useCase.build(404L)).isNull()
    }

    @Test
    fun `places within a day are ordered by arrival and the day distance is summed`() = runTest {
        coEvery { tripDao.getById(1L) } returns trip(1L, "2026-01-01", "2026-01-02")
        coEvery { placeDao.getById(2L) } returns place(2L, "Museum")
        coEvery { placeDao.getById(3L) } returns place(3L, "Cafe")
        coEvery { visitDao.getByDayKey("2026-01-01") } returns listOf(
            visit(2L, "2026-01-01", arrival = 200), // later
            visit(3L, "2026-01-01", arrival = 100)  // earlier
        )
        coEvery { visitDao.getByDayKey("2026-01-02") } returns listOf(visit(2L, "2026-01-02", arrival = 50))
        coEvery { segmentDao.getByDayKey("2026-01-01") } returns listOf(seg("2026-01-01", 3_000.0), seg("2026-01-01", 2_000.0))
        coEvery { segmentDao.getByDayKey("2026-01-02") } returns emptyList()

        val detail = useCase.build(1L)!!

        assertThat(detail.days).hasSize(2)
        assertThat(detail.days[0].places.map { it.displayName }).containsExactly("Cafe", "Museum").inOrder()
        assertThat(detail.days[0].distanceMeters).isWithin(0.001).of(5_000.0)
    }

    @Test
    fun `a day with no visits is omitted from the breakdown`() = runTest {
        coEvery { tripDao.getById(1L) } returns trip(1L, "2026-02-01", "2026-02-03")
        coEvery { placeDao.getById(2L) } returns place(2L, "Hotel")
        coEvery { visitDao.getByDayKey("2026-02-01") } returns listOf(visit(2L, "2026-02-01", arrival = 1))
        coEvery { visitDao.getByDayKey("2026-02-02") } returns emptyList() // gap day
        coEvery { visitDao.getByDayKey("2026-02-03") } returns listOf(visit(2L, "2026-02-03", arrival = 1))

        val detail = useCase.build(1L)!!

        assertThat(detail.days.map { it.dayKey }).containsExactly("2026-02-01", "2026-02-03").inOrder()
    }

    @Test
    fun `an unresolved place renders as Unknown place`() = runTest {
        coEvery { tripDao.getById(1L) } returns trip(1L, "2026-03-01", "2026-03-01")
        coEvery { placeDao.getById(99L) } returns null
        coEvery { visitDao.getByDayKey("2026-03-01") } returns listOf(visit(99L, "2026-03-01", arrival = 1))

        val detail = useCase.build(1L)!!

        assertThat(detail.days.single().places.single().displayName).isEqualTo("Unknown place")
    }

    @Test
    fun `deleted visits are excluded from a trip day`() = runTest {
        coEvery { tripDao.getById(1L) } returns trip(1L, "2026-04-01", "2026-04-01")
        coEvery { placeDao.getById(2L) } returns place(2L, "Kept")
        coEvery { placeDao.getById(3L) } returns place(3L, "Deleted")
        coEvery { visitDao.getByDayKey("2026-04-01") } returns listOf(
            visit(2L, "2026-04-01", arrival = 1),
            visit(3L, "2026-04-01", arrival = 2, deleted = true)
        )

        val detail = useCase.build(1L)!!

        assertThat(detail.days.single().places.map { it.displayName }).containsExactly("Kept")
    }
}
