package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.DetectTripsUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.TripDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for [DetectTripsUseCase]: the away-from-home run scan, the multi-day-span
 * threshold, trip titling, and the Home-anchor requirement.
 */
class DetectTripsUseCaseTest {

    private val visitDao = mockk<VisitDao>()
    private val placeDao = mockk<PlaceDao>()
    private val segmentDao = mockk<MovementSegmentDao>(relaxed = true)
    private val tripDao = mockk<TripDao>(relaxed = true)
    private val useCase = DetectTripsUseCase(visitDao, placeDao, segmentDao, tripDao)

    private val homeId = 1L

    private fun place(id: Long, name: String?, category: String = "UNKNOWN") = PlaceEntity(
        placeId = id,
        centroidLat = 0.0,
        centroidLng = 0.0,
        geohash = "x",
        userDisplayName = name,
        category = category,
        createdAt = 0L
    )

    private fun visit(placeId: Long, dayKey: String, dwell: Long = 0L) = VisitEntity(
        placeId = placeId,
        arrivalAt = LocalDate.parse(dayKey).toEpochDay() * 86_400_000L,
        dwellMs = dwell,
        source = "TEST",
        dayKey = dayKey
    )

    private fun withHome() {
        coEvery { placeDao.getHomePlace() } returns place(homeId, "Home", "HOME")
    }

    /** Wires the per-day visit lookups for a map of dayKey → visits. */
    private fun withDays(days: Map<String, List<VisitEntity>>) {
        coEvery { visitDao.getAllDayKeys() } returns days.keys.sorted()
        days.forEach { (dayKey, visits) ->
            coEvery { visitDao.getByDayKey(dayKey) } returns visits
        }
    }

    @Test
    fun `no home place yields no trips`() = runTest {
        coEvery { placeDao.getHomePlace() } returns null

        assertThat(useCase.detect()).isEmpty()
        assertThat(useCase.hasHomeAnchor()).isFalse()
    }

    @Test
    fun `consecutive away days form one trip`() = runTest {
        withHome()
        coEvery { placeDao.getById(2L) } returns place(2L, "Paris")
        withDays(
            mapOf(
                "2026-01-01" to listOf(visit(2L, "2026-01-01")),
                "2026-01-02" to listOf(visit(2L, "2026-01-02")),
                "2026-01-03" to listOf(visit(2L, "2026-01-03"))
            )
        )

        val trips = useCase.detect()

        assertThat(trips).hasSize(1)
        assertThat(trips[0].startDayKey).isEqualTo("2026-01-01")
        assertThat(trips[0].endDayKey).isEqualTo("2026-01-03")
        assertThat(trips[0].title).isEqualTo("Trip to Paris")
        assertThat(trips[0].placeCount).isEqualTo(1)
        assertThat(trips[0].isOngoing).isFalse()
    }

    @Test
    fun `a day spent at home breaks the run`() = runTest {
        withHome()
        coEvery { placeDao.getById(2L) } returns place(2L, "Berlin")
        withDays(
            mapOf(
                "2026-02-01" to listOf(visit(2L, "2026-02-01")),   // away (1-day run)
                "2026-02-02" to listOf(visit(homeId, "2026-02-02")), // home — breaks
                "2026-02-03" to listOf(visit(2L, "2026-02-03")),   // away
                "2026-02-04" to listOf(visit(2L, "2026-02-04"))    // away
            )
        )

        val trips = useCase.detect()

        // The lone 2026-02-01 away day spans 0 days → not a trip; 02-03..02-04 is.
        assertThat(trips).hasSize(1)
        assertThat(trips[0].startDayKey).isEqualTo("2026-02-03")
        assertThat(trips[0].endDayKey).isEqualTo("2026-02-04")
    }

    @Test
    fun `a single away day is not a multi-day trip`() = runTest {
        withHome()
        withDays(mapOf("2026-03-10" to listOf(visit(2L, "2026-03-10"))))

        assertThat(useCase.detect()).isEmpty()
    }

    @Test
    fun `title follows the place with the most dwell time`() = runTest {
        withHome()
        coEvery { placeDao.getById(2L) } returns place(2L, "Airport")
        coEvery { placeDao.getById(3L) } returns place(3L, "Rome")
        withDays(
            mapOf(
                "2026-04-01" to listOf(visit(2L, "2026-04-01", dwell = 1_000L)),
                "2026-04-02" to listOf(visit(3L, "2026-04-02", dwell = 9_000L))
            )
        )

        val trips = useCase.detect()

        assertThat(trips).hasSize(1)
        assertThat(trips[0].title).isEqualTo("Trip to Rome")
        assertThat(trips[0].placeCount).isEqualTo(2)
    }

    @Test
    fun `distance is summed across the trip day range`() = runTest {
        withHome()
        coEvery { placeDao.getById(2L) } returns place(2L, "Oslo")
        withDays(
            mapOf(
                "2026-05-01" to listOf(visit(2L, "2026-05-01")),
                "2026-05-02" to listOf(visit(2L, "2026-05-02"))
            )
        )
        coEvery { segmentDao.getByDayKey("2026-05-01") } returns
            listOf(segment(distanceM = 4_000.0))
        coEvery { segmentDao.getByDayKey("2026-05-02") } returns
            listOf(segment(distanceM = 6_000.0))

        val trips = useCase.detect()

        assertThat(trips).hasSize(1)
        assertThat(trips[0].distanceMeters).isWithin(0.001).of(10_000.0)
    }

    @Test
    fun `a run reaching today is marked ongoing`() = runTest {
        withHome()
        coEvery { placeDao.getById(2L) } returns place(2L, "Tokyo")
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        withDays(
            mapOf(
                yesterday.toString() to listOf(visit(2L, yesterday.toString())),
                today.toString() to listOf(visit(2L, today.toString()))
            )
        )

        val trips = useCase.detect()

        assertThat(trips).hasSize(1)
        assertThat(trips[0].isOngoing).isTrue()
    }

    private fun segment(distanceM: Double) = MovementSegmentEntity(
        segmentType = "DRIVE",
        startAt = 0L,
        endAt = 0L,
        distanceM = distanceM,
        dayKey = "x"
    )
}
