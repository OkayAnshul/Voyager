package com.cosmiclaboratory.voyager.domain.usecase

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import javax.inject.Inject
import javax.inject.Singleton

data class EnrichmentResult(
    val displayName: String,
    val providerSource: String
)

data class FullEnrichmentResult(
    val best: EnrichmentResult?,
    val allCandidates: List<GeocodeCandidate>
)

/**
 * Use case for enriching a place with geocoded display name.
 *
 * Uses the multi-provider GeocodingRepository.reverseGeocode() pipeline to
 * resolve a human-readable name for a place's coordinates.
 */
@Singleton
class EnrichPlaceWithDetailsUseCase @Inject constructor(
    private val geocodingRepository: GeocodingRepository
) {
    /**
     * Reverse-geocode coordinates and return the best display name, or null on failure.
     */
    suspend operator fun invoke(lat: Double, lng: Double): String? {
        return enrichWithSource(lat, lng)?.displayName
    }

    /**
     * Reverse-geocode and return both the display name and provider source.
     */
    suspend fun enrichWithSource(lat: Double, lng: Double): EnrichmentResult? {
        return enrichFull(lat, lng).best
    }

    /**
     * Reverse-geocode and return the best result plus all provider candidates.
     */
    suspend fun enrichFull(lat: Double, lng: Double): FullEnrichmentResult {
        return try {
            val result = geocodingRepository.reverseGeocode(lat, lng)
            val best = result.bestCandidate?.let {
                EnrichmentResult(
                    // Accuracy-gated name — coarsened when confidence is low.
                    displayName = it.safeDisplayName ?: it.displayName,
                    providerSource = it.provider.name
                )
            }
            FullEnrichmentResult(best = best, allCandidates = result.candidates)
        } catch (e: Exception) {
            // Coordinates omitted — this error survives the release log strip
            // and Voyager never writes the user's location to logcat.
            Log.e(TAG, "Failed to enrich place", e)
            FullEnrichmentResult(best = null, allCandidates = emptyList())
        }
    }

    companion object {
        private const val TAG = "EnrichPlaceUseCase"
    }
}
