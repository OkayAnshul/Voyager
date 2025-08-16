package com.cosmiclaboratory.voyager.domain.geocoding

import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scores and ranks geocoding candidates from multiple providers.
 *
 * Scoring formula:
 *   score = priority * 0.4 + confidence * 0.3 + specificity * 0.2 + recency * 0.1
 *
 * Display name resolution chain:
 *   userDisplayName > userCategory > bestProviderName > semantic > coordinates
 */
@Singleton
class GeocodingConflictResolver @Inject constructor() {

    /**
     * Rank candidates by composite score (highest first).
     */
    fun rankCandidates(candidates: List<GeocodeCandidate>): List<GeocodeCandidate> {
        if (candidates.isEmpty()) return emptyList()
        return candidates.sortedByDescending { scoreCandidateInternal(it, candidates) }
    }

    /**
     * Return the single best candidate, or null if list is empty.
     */
    fun bestCandidate(candidates: List<GeocodeCandidate>): GeocodeCandidate? {
        return rankCandidates(candidates).firstOrNull()
    }

    /**
     * Compute the composite score for a single candidate in context of all candidates.
     */
    fun scoreCandidate(candidate: GeocodeCandidate, allCandidates: List<GeocodeCandidate>): Float {
        return scoreCandidateInternal(candidate, allCandidates)
    }

    /**
     * Resolve the display name for a place using the chain:
     *   userDisplayName > userCategory > bestProviderName (+ nearby) > nearbyContext > semantic > coordinates
     *
     * @param userDisplayName User-set custom name for the place
     * @param userCategory User-set category (e.g., "HOME", "WORK")
     * @param bestProviderName Best name from geocoding providers
     * @param nearbyContext Contextual "Near [landmark/neighborhood]" string built from structured parts
     * @param semanticLabel Semantic context label (e.g., inferred from visit patterns)
     * @param lat Latitude for coordinate fallback
     * @param lng Longitude for coordinate fallback
     */
    fun resolveDisplayName(
        userDisplayName: String?,
        userCategory: String?,
        bestProviderName: String?,
        nearbyContext: String? = null,
        semanticLabel: String?,
        lat: Double,
        lng: Double
    ): String {
        // 1. User-set name always wins
        if (!userDisplayName.isNullOrBlank()) return userDisplayName

        // 2. User category as label (e.g., "Home", "Work")
        if (!userCategory.isNullOrBlank() && userCategory != "UNKNOWN") {
            return userCategory.lowercase().replaceFirstChar { it.uppercase() }
        }

        // 3. Best provider name (exact address from winning provider)
        if (!bestProviderName.isNullOrBlank()) return bestProviderName

        // 4. Semantic label from visit pattern inference
        if (!semanticLabel.isNullOrBlank()) return semanticLabel

        // 5. Coordinate fallback
        return formatCoordinates(lat, lng)
    }

    // ---- Internal scoring ----

    private fun scoreCandidateInternal(
        candidate: GeocodeCandidate,
        allCandidates: List<GeocodeCandidate>
    ): Float {
        val priorityScore = computePriorityScore(candidate.rank)
        val confidenceScore = candidate.confidence.coerceIn(0f, 1f)
        val specificityScore = computeSpecificityScore(candidate.structuredParts)
        val recencyScore = computeRecencyScore(candidate.fetchedAt, allCandidates)

        return priorityScore * WEIGHT_PRIORITY +
                confidenceScore * WEIGHT_CONFIDENCE +
                specificityScore * WEIGHT_SPECIFICITY +
                recencyScore * WEIGHT_RECENCY
    }

    /**
     * Lower rank number = higher priority = higher score.
     * Maps rank 1..5 to score 1.0..0.2
     */
    private fun computePriorityScore(rank: Int): Float {
        return (1f - (rank - 1).coerceIn(0, 4) * 0.2f)
    }

    /**
     * More filled-in structured address fields = higher specificity.
     */
    private fun computeSpecificityScore(structured: StructuredAddress?): Float {
        if (structured == null) return 0.1f
        var filled = 0
        if (!structured.street.isNullOrBlank()) filled++
        if (!structured.houseNumber.isNullOrBlank()) filled++
        if (!structured.city.isNullOrBlank()) filled++
        if (!structured.state.isNullOrBlank()) filled++
        if (!structured.country.isNullOrBlank()) filled++
        if (!structured.postalCode.isNullOrBlank()) filled++
        if (!structured.neighborhood.isNullOrBlank()) filled++
        return (filled.toFloat() / 7f).coerceIn(0f, 1f)
    }

    /**
     * Most recent fetch gets highest score. Oldest gets 0.
     */
    private fun computeRecencyScore(fetchedAt: Long, allCandidates: List<GeocodeCandidate>): Float {
        if (allCandidates.size <= 1) return 1f
        val oldest = allCandidates.minOf { it.fetchedAt }
        val newest = allCandidates.maxOf { it.fetchedAt }
        if (newest == oldest) return 1f
        return ((fetchedAt - oldest).toFloat() / (newest - oldest).toFloat()).coerceIn(0f, 1f)
    }

    private fun formatCoordinates(lat: Double, lng: Double): String {
        return "%.4f, %.4f".format(lat, lng)
    }

    companion object {
        private const val WEIGHT_PRIORITY = 0.4f
        private const val WEIGHT_CONFIDENCE = 0.3f
        private const val WEIGHT_SPECIFICITY = 0.2f
        private const val WEIGHT_RECENCY = 0.1f
    }
}
