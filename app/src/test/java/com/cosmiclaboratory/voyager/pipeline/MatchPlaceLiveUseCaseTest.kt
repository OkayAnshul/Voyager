package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.MatchPlaceLiveUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MatchPlaceLiveUseCaseTest {

    private val placeDao = mockk<PlaceDao>(relaxed = true)
    private lateinit var useCase: MatchPlaceLiveUseCase

    private val lat = 37.0
    private val lng = -122.0
    private val metersPerDegLat = 111_320.0

    @Before
    fun setup() {
        useCase = MatchPlaceLiveUseCase(placeDao)
    }

    private fun place(id: Long, radiusM: Float = 80f) =
        PlaceEntity(placeId = id, centroidLat = lat, centroidLng = lng, radiusM = radiusM, geohash = "9q8y", createdAt = 0L)

    /** Latitude offset for an approximate north-south distance in metres. */
    private fun offsetLat(meters: Double) = meters / metersPerDegLat

    @Test
    fun `no nearby places yields no match`() = runTest {
        coEvery { placeDao.getByGeohashPrefix(any()) } returns emptyList()
        assertNull(useCase.matchSample(lat, lng, accuracyM = 10f).matchedPlace)
    }

    @Test
    fun `matches a small place after entry hysteresis`() = runTest {
        coEvery { placeDao.getByGeohashPrefix(any()) } returns listOf(place(1, radiusM = 50f))
        val s = lat + offsetLat(10.0)
        assertNull("first sample only builds hysteresis", useCase.matchSample(s, lng, 10f).matchedPlace)
        assertEquals(1L, useCase.matchSample(s, lng, 10f).matchedPlace?.placeId)
    }

    @Test
    fun `large venue matches when inside its footprint despite accurate GPS (T8)`() = runTest {
        // Mall-sized place (radius 300m); user 150m from centroid; accurate GPS → searchRadius 50m.
        // Pre-fix this was rejected (150 > 50) before the place footprint was ever considered.
        coEvery { placeDao.getByGeohashPrefix(any()) } returns listOf(place(1, radiusM = 300f))
        val s = lat + offsetLat(150.0)
        useCase.matchSample(s, lng, 10f)            // hysteresis 1
        val r = useCase.matchSample(s, lng, 10f)    // hysteresis 2 → confirmed
        assertEquals("inside the venue footprint should match", 1L, r.matchedPlace?.placeId)
    }

    @Test
    fun `sample outside both the search radius and the footprint does not match`() = runTest {
        coEvery { placeDao.getByGeohashPrefix(any()) } returns listOf(place(1, radiusM = 50f))
        val s = lat + offsetLat(500.0) // beyond searchRadius (<=250) and the 50m footprint
        useCase.matchSample(s, lng, 10f)
        assertNull(useCase.matchSample(s, lng, 10f).matchedPlace)
    }
}
