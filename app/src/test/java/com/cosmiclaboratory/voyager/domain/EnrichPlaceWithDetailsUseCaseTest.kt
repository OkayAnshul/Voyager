package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.model.GeocodingResult
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.model.enums.LicenseClass
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.domain.usecase.EnrichPlaceWithDetailsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrichPlaceWithDetailsUseCaseTest {

    private val repo = mockk<GeocodingRepository>()
    private val useCase = EnrichPlaceWithDetailsUseCase(repo)

    private fun candidate(displayName: String, safe: String? = null, category: PlaceCategory? = null) =
        GeocodeCandidate(
            provider = GeocodingProviderId.OVERPASS, rank = 1, displayName = displayName,
            structuredParts = null, confidence = 0.9f, licenseClass = LicenseClass.FREE,
            fetchedAt = 0L, safeDisplayName = safe, inferredCategory = category
        )

    @Test
    fun `enrichFull maps the best candidate, preferring the accuracy-gated safe name`() = runTest {
        val c = candidate("123 Main St, Springfield", safe = "Main St, Springfield", category = PlaceCategory.RESTAURANT)
        coEvery { repo.reverseGeocode(1.0, 2.0) } returns GeocodingResult(listOf(c), c)

        val out = useCase.enrichFull(1.0, 2.0)

        assertEquals("Main St, Springfield", out.best?.displayName)
        assertEquals("OVERPASS", out.best?.providerSource)
        assertEquals(PlaceCategory.RESTAURANT, out.best?.inferredCategory)
        assertEquals(1, out.allCandidates.size)
    }

    @Test
    fun `falls back to the raw display name when there is no safe name`() = runTest {
        val c = candidate("Blue Bottle Coffee", safe = null)
        coEvery { repo.reverseGeocode(any(), any()) } returns GeocodingResult(listOf(c), c)
        assertEquals("Blue Bottle Coffee", useCase.enrichWithSource(1.0, 2.0)?.displayName)
    }

    @Test
    fun `invoke returns just the display name`() = runTest {
        val c = candidate("Riverside Park", safe = "Riverside Park")
        coEvery { repo.reverseGeocode(any(), any()) } returns GeocodingResult(listOf(c), c)
        assertEquals("Riverside Park", useCase(1.0, 2.0))
    }

    @Test
    fun `a geocoding failure yields an empty result, not a crash`() = runTest {
        coEvery { repo.reverseGeocode(any(), any()) } throws RuntimeException("network down")
        val out = useCase.enrichFull(1.0, 2.0)
        assertNull(out.best)
        assertTrue(out.allCandidates.isEmpty())
        assertNull(useCase(1.0, 2.0))
    }
}
