package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.DevicePhoto
import com.cosmiclaboratory.voyager.domain.photo.PhotoLibrary
import com.cosmiclaboratory.voyager.domain.usecase.BuildDayStoryUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [BuildDayStoryUseCase]: photo↔visit correlation by capture time, the
 * EXIF-location tie-break among overlapping visits, and the unplaced-photo bucket.
 */
class BuildDayStoryUseCaseTest {

    private val visitDao = mockk<VisitDao>()
    private val placeDao = mockk<PlaceDao>()

    private companion object {
        const val DAY = "2026-05-10"
    }

    /** Fake that ignores the day window — tests drive correlation purely via timestamps. */
    private class FakePhotoLibrary(
        private val photos: List<DevicePhoto>,
        private val permitted: Boolean = true
    ) : PhotoLibrary {
        override suspend fun photosBetween(startMs: Long, endMs: Long): List<DevicePhoto> =
            if (permitted) photos else emptyList()
        override fun hasPermission(): Boolean = permitted
    }

    private fun useCase(library: PhotoLibrary) = BuildDayStoryUseCase(visitDao, placeDao, library)

    private fun visit(
        id: Long,
        placeId: Long,
        arrival: Long,
        departure: Long?,
        lat: Double? = null,
        lng: Double? = null
    ) = VisitEntity(
        visitId = id,
        placeId = placeId,
        arrivalAt = arrival,
        departureAt = departure,
        source = "TEST",
        dayKey = DAY,
        centroidLat = lat,
        centroidLng = lng
    )

    private fun place(id: Long, name: String) = PlaceEntity(
        placeId = id,
        centroidLat = 0.0,
        centroidLng = 0.0,
        geohash = "x",
        userDisplayName = name,
        createdAt = 0L
    )

    private fun photo(takenAt: Long, lat: Double? = null, lng: Double? = null) =
        DevicePhoto(uri = "content://photo/$takenAt", takenAt = takenAt, lat = lat, lng = lng)

    @Test
    fun `a photo inside a visit window is pinned to that place`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(visit(1, 10, 1_000, 5_000))
        coEvery { placeDao.getById(10) } returns place(10, "Blue Bottle Cafe")
        val uc = useCase(FakePhotoLibrary(listOf(photo(3_000))))

        val story = uc.build(DAY)

        assertThat(story.places).hasSize(1)
        assertThat(story.places[0].displayName).isEqualTo("Blue Bottle Cafe")
        assertThat(story.places[0].photos).hasSize(1)
        assertThat(story.unplacedPhotos).isEmpty()
        assertThat(story.totalPhotoCount).isEqualTo(1)
    }

    @Test
    fun `a photo outside every visit window is unplaced`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(visit(1, 10, 1_000, 2_000))
        coEvery { placeDao.getById(10) } returns place(10, "Cafe")
        val uc = useCase(FakePhotoLibrary(listOf(photo(9_000))))

        val story = uc.build(DAY)

        assertThat(story.places).isEmpty()
        assertThat(story.unplacedPhotos).hasSize(1)
        assertThat(story.totalPhotoCount).isEqualTo(1)
    }

    @Test
    fun `among overlapping visits a geotagged photo pins to the nearest place`() = runTest {
        // Two visits both contain t=3000; photo's GPS sits next to place 20.
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(
            visit(1, 10, 1_000, 9_000, lat = 40.000, lng = -73.000),
            visit(2, 20, 2_000, 9_000, lat = 51.500, lng = -0.120)
        )
        coEvery { placeDao.getById(10) } returns place(10, "Far Place")
        coEvery { placeDao.getById(20) } returns place(20, "Near Place")
        val uc = useCase(FakePhotoLibrary(listOf(photo(3_000, lat = 51.501, lng = -0.121))))

        val story = uc.build(DAY)

        assertThat(story.places).hasSize(1)
        assertThat(story.places[0].displayName).isEqualTo("Near Place")
    }

    @Test
    fun `among overlapping visits without location the shortest window wins`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(
            visit(1, 10, 1_000, 9_000),  // 8000ms window
            visit(2, 20, 2_500, 4_000)   // 1500ms window — tighter
        )
        coEvery { placeDao.getById(10) } returns place(10, "Wide Window")
        coEvery { placeDao.getById(20) } returns place(20, "Tight Window")
        val uc = useCase(FakePhotoLibrary(listOf(photo(3_000))))

        val story = uc.build(DAY)

        assertThat(story.places).hasSize(1)
        assertThat(story.places[0].displayName).isEqualTo("Tight Window")
    }

    @Test
    fun `an open visit captures photos taken after arrival`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(visit(1, 10, 1_000, null))
        coEvery { placeDao.getById(10) } returns place(10, "Still Here")
        val uc = useCase(FakePhotoLibrary(listOf(photo(7_000))))

        val story = uc.build(DAY)

        assertThat(story.places).hasSize(1)
        assertThat(story.places[0].photos).hasSize(1)
    }

    @Test
    fun `no photos yields an empty story`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(visit(1, 10, 1_000, 5_000))
        val uc = useCase(FakePhotoLibrary(emptyList()))

        val story = uc.build(DAY)

        assertThat(story.isEmpty).isTrue()
        assertThat(story.places).isEmpty()
    }

    @Test
    fun `without permission the story is empty and the flag is reported`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns emptyList()
        val uc = useCase(FakePhotoLibrary(listOf(photo(3_000)), permitted = false))

        assertThat(uc.hasPhotoPermission()).isFalse()
        assertThat(uc.build(DAY).isEmpty).isTrue()
    }

    @Test
    fun `places order by arrival, photos by time, and a photo-less visit is omitted`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(
            visit(1, 10, arrival = 5_000, departure = 8_000),  // later place (Museum)
            visit(2, 20, arrival = 1_000, departure = 3_000),  // earlier place (Cafe)
            visit(3, 30, arrival = 8_500, departure = 9_000)   // no photos → omitted
        )
        coEvery { placeDao.getById(10) } returns place(10, "Museum")
        coEvery { placeDao.getById(20) } returns place(20, "Cafe")
        coEvery { placeDao.getById(30) } returns place(30, "Office")
        val uc = useCase(
            FakePhotoLibrary(
                listOf(
                    photo(6_000), photo(5_500), // both in Museum's window, out of order
                    photo(2_000),               // Cafe
                    photo(20_000)               // after every window → unplaced
                )
            )
        )

        val story = uc.build(DAY)

        // Chronological by arrival; the Office visit (no photos) is dropped.
        assertThat(story.places.map { it.displayName }).containsExactly("Cafe", "Museum").inOrder()
        // Photos within a place are sorted by capture time.
        assertThat(story.places[1].photos.map { it.takenAt }).containsExactly(5_500L, 6_000L).inOrder()
        assertThat(story.unplacedPhotos.map { it.takenAt }).containsExactly(20_000L)
        assertThat(story.totalPhotoCount).isEqualTo(4)
    }

    @Test
    fun `a visit whose place can't be resolved shows as Unknown place`() = runTest {
        coEvery { visitDao.getByDayKey(DAY) } returns listOf(visit(1, 99, 1_000, 5_000))
        coEvery { placeDao.getById(99) } returns null
        val uc = useCase(FakePhotoLibrary(listOf(photo(3_000))))

        val story = uc.build(DAY)

        assertThat(story.places).hasSize(1)
        assertThat(story.places[0].displayName).isEqualTo("Unknown place")
        assertThat(story.places[0].photos).hasSize(1)
    }
}
