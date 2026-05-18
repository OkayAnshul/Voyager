package com.cosmiclaboratory.voyager.domain.geocoding

import com.cosmiclaboratory.voyager.domain.model.ConfidenceTier
import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
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

    // ---- Accuracy gate ----

    /**
     * The user-facing name safe to show for [best], coarsened to the precision the
     * evidence actually supports — so a wrong house number or street is never
     * presented as fact. See [confidenceTier].
     *
     *  - HIGH   → the full result, exactly as today.
     *  - MEDIUM → street + city (no house number).
     *  - LOW    → neighbourhood or city only.
     *  - NONE   → coordinates.
     *
     * POI/name-type results (no street precision to mis-state) are shown as-is at
     * any tier above NONE.
     */
    fun safeDisplayName(
        best: GeocodeCandidate,
        allCandidates: List<GeocodeCandidate>,
        lat: Double,
        lng: Double
    ): String {
        val tier = confidenceTier(best, allCandidates)
        if (tier == ConfidenceTier.NONE) return formatCoordinates(lat, lng)

        val parts = best.structuredParts
        // Name-type result (POI, or a result with no street-level precision) — there
        // is no house number/street to get wrong, so show it as-is.
        if (parts == null || (parts.street.isNullOrBlank() && parts.houseNumber.isNullOrBlank())) {
            return best.displayName
        }

        return when (tier) {
            ConfidenceTier.HIGH -> best.displayName
            ConfidenceTier.MEDIUM ->
                listOfNotNull(parts.street, parts.city)
                    .joinToString(", ")
                    .ifBlank { best.displayName }
            ConfidenceTier.LOW ->
                (parts.neighborhood ?: parts.city)?.takeIf { it.isNotBlank() }
                    ?: formatCoordinates(lat, lng)
            ConfidenceTier.NONE -> formatCoordinates(lat, lng)
        }
    }

    /**
     * Trust tier for [best], from its normalized confidence, raised when multiple
     * providers independently agree on the city (and street) — shared facts are
     * trustworthy even when no single provider is highly confident.
     */
    fun confidenceTier(
        best: GeocodeCandidate,
        allCandidates: List<GeocodeCandidate>
    ): ConfidenceTier {
        val norm = normalizedConfidence(best)
        var tier = when {
            norm >= HIGH_CONFIDENCE -> ConfidenceTier.HIGH
            norm >= MEDIUM_CONFIDENCE -> ConfidenceTier.MEDIUM
            norm >= LOW_CONFIDENCE -> ConfidenceTier.LOW
            else -> ConfidenceTier.NONE
        }
        val cityAgreement = agreementCount(allCandidates) { it.structuredParts?.city }
        val streetAgreement = agreementCount(allCandidates) { it.structuredParts?.street }
        if (cityAgreement >= 2 && streetAgreement >= 2) {
            tier = bumpUp(tier)
        } else if (cityAgreement >= 2 && tier == ConfidenceTier.NONE) {
            tier = ConfidenceTier.LOW
        }
        return tier
    }

    /**
     * Scales a provider's ad-hoc raw confidence onto one comparable 0–1 trust scale,
     * so the accuracy gate compares providers fairly.
     */
    fun normalizedConfidence(candidate: GeocodeCandidate): Float {
        val factor = when (candidate.provider) {
            GeocodingProviderId.ANDROID_GEOCODER -> 1.0f
            GeocodingProviderId.NOMINATIM -> 1.0f
            GeocodingProviderId.OVERPASS -> 1.0f
            GeocodingProviderId.PHOTON -> 0.95f
            else -> 0.8f
        }
        return (candidate.confidence.coerceIn(0f, 1f) * factor).coerceIn(0f, 1f)
    }

    /** Size of the largest set of candidates sharing one non-blank [selector] value. */
    private fun agreementCount(
        candidates: List<GeocodeCandidate>,
        selector: (GeocodeCandidate) -> String?
    ): Int {
        return candidates
            .mapNotNull { selector(it)?.trim()?.lowercase()?.takeIf { v -> v.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .values
            .maxOrNull() ?: 0
    }

    private fun bumpUp(tier: ConfidenceTier): ConfidenceTier = when (tier) {
        ConfidenceTier.NONE -> ConfidenceTier.LOW
        ConfidenceTier.LOW -> ConfidenceTier.MEDIUM
        ConfidenceTier.MEDIUM -> ConfidenceTier.HIGH
        ConfidenceTier.HIGH -> ConfidenceTier.HIGH
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

        // Accuracy-gate tier thresholds, on the normalized-confidence scale.
        const val HIGH_CONFIDENCE = 0.85f
        const val MEDIUM_CONFIDENCE = 0.65f
        const val LOW_CONFIDENCE = 0.45f
    }
}
