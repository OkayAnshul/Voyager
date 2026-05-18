package com.cosmiclaboratory.voyager.data.repository

import android.util.Log
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingConflictResolver
import com.cosmiclaboratory.voyager.domain.geocoding.GeocodingProvider
import com.cosmiclaboratory.voyager.domain.model.GeocodeCandidate
import com.cosmiclaboratory.voyager.domain.model.GeocodingResult
import com.cosmiclaboratory.voyager.domain.model.ProviderStatus
import com.cosmiclaboratory.voyager.domain.model.StructuredAddress
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.model.enums.LicenseClass
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.storage.database.dao.GeocodeCandidateDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.entity.GeocodeCandidateEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocodingRepositoryImpl @Inject constructor(
    private val providers: List<@JvmSuppressWildcards GeocodingProvider>,
    private val geocodeCandidateDao: GeocodeCandidateDao,
    private val placeDao: PlaceDao,
    private val conflictResolver: GeocodingConflictResolver
) : GeocodingRepository {

    private val sortedProviders: List<GeocodingProvider>
        get() = providers.sortedBy { it.priority }

    override suspend fun reverseGeocode(lat: Double, lng: Double): GeocodingResult {
        val candidates = mutableListOf<GeocodeCandidate>()
        var rank = 1

        for (provider in sortedProviders) {
            if (!provider.isAvailable) {
                Log.d(TAG, "Skipping unavailable provider: ${provider.providerId}")
                continue
            }

            val result = provider.reverseGeocode(lat, lng)
            result.onSuccess { providerResult ->
                val candidate = GeocodeCandidate(
                    provider = provider.providerId,
                    rank = rank++,
                    displayName = providerResult.displayName,
                    structuredParts = providerResult.structuredParts,
                    confidence = providerResult.confidence,
                    licenseClass = licenseClassFor(provider.providerId),
                    fetchedAt = System.currentTimeMillis()
                )
                candidates.add(candidate)
            }
            result.onFailure { e ->
                Log.w(TAG, "${provider.providerId} reverseGeocode failed: ${e.message}")
            }
        }

        val ranked = conflictResolver.rankCandidates(candidates)
        return GeocodingResult(
            candidates = ranked,
            bestCandidate = ranked.firstOrNull()
        )
    }

    override suspend fun forwardGeocode(query: String): GeocodingResult {
        val candidates = mutableListOf<GeocodeCandidate>()
        var rank = 1

        for (provider in sortedProviders) {
            if (!provider.isAvailable) continue

            val result = provider.forwardGeocode(query)
            result.onSuccess { results ->
                for (providerResult in results) {
                    val candidate = GeocodeCandidate(
                        provider = provider.providerId,
                        rank = rank++,
                        displayName = providerResult.displayName,
                        structuredParts = providerResult.structuredParts,
                        confidence = providerResult.confidence,
                        licenseClass = licenseClassFor(provider.providerId),
                        fetchedAt = System.currentTimeMillis()
                    )
                    candidates.add(candidate)
                }
            }
            result.onFailure { e ->
                Log.w(TAG, "${provider.providerId} forwardGeocode failed: ${e.message}")
            }
        }

        val ranked = conflictResolver.rankCandidates(candidates)
        return GeocodingResult(
            candidates = ranked,
            bestCandidate = ranked.firstOrNull()
        )
    }

    override suspend fun resolveDisplayName(placeId: Long): String {
        val place = placeDao.getById(placeId)
            ?: return "Unknown Place"

        val candidates = getCandidatesForPlace(placeId)
        val bestProviderName = candidates.firstOrNull()?.displayName

        return conflictResolver.resolveDisplayName(
            userDisplayName = place.userDisplayName,
            userCategory = place.userCategory,
            bestProviderName = bestProviderName ?: place.bestProviderName,
            nearbyContext = buildNearbyContext(candidates),
            semanticLabel = null,
            lat = place.centroidLat,
            lng = place.centroidLng
        )
    }

    /**
     * Build a "nearby" context string from candidates' structured address parts.
     * Picks the most specific contextual info: neighborhood > street > city.
     * Returns null if no useful context found.
     */
    private fun buildNearbyContext(candidates: List<GeocodeCandidate>): String? {
        for (candidate in candidates) {
            val parts = candidate.structuredParts ?: continue
            // Prefer neighborhood (most specific landmark-like context)
            if (!parts.neighborhood.isNullOrBlank()) return parts.neighborhood
        }
        // Fallback: street name from any candidate
        for (candidate in candidates) {
            val parts = candidate.structuredParts ?: continue
            if (!parts.street.isNullOrBlank()) {
                // If street + city available, compose "Street, City"
                return if (!parts.city.isNullOrBlank()) {
                    "${parts.street}, ${parts.city}"
                } else {
                    parts.street
                }
            }
        }
        // Last resort: city only
        for (candidate in candidates) {
            val parts = candidate.structuredParts ?: continue
            if (!parts.city.isNullOrBlank()) return parts.city
        }
        return null
    }

    override suspend fun refreshGeocodeForPlace(placeId: Long): Result<Unit> {
        return try {
            val place = placeDao.getById(placeId)
                ?: return Result.failure(IllegalArgumentException("Place $placeId not found"))

            geocodeCandidateDao.deleteByPlaceId(placeId)

            val geocodingResult = reverseGeocode(place.centroidLat, place.centroidLng)

            val now = System.currentTimeMillis()
            val entities = geocodingResult.candidates.map { candidate ->
                GeocodeCandidateEntity(
                    placeId = placeId,
                    provider = candidate.provider.name,
                    rank = candidate.rank,
                    displayName = candidate.displayName,
                    structuredPartsJson = candidate.structuredParts?.toJson(),
                    confidence = candidate.confidence,
                    licenseClass = candidate.licenseClass.name,
                    cachedUntil = now + CANDIDATE_TTL_MS,
                    fetchedAt = candidate.fetchedAt
                )
            }
            if (entities.isNotEmpty()) {
                geocodeCandidateDao.insertAll(entities)
            }

            geocodingResult.bestCandidate?.let { best ->
                placeDao.update(
                    place.copy(
                        bestProviderName = best.displayName,
                        bestProviderSource = best.provider.name
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "refreshGeocodeForPlace failed for placeId=$placeId", e)
            Result.failure(e)
        }
    }

    override suspend fun getCandidatesForPlace(placeId: Long): List<GeocodeCandidate> {
        return geocodeCandidateDao.getByPlaceId(placeId).map { entity ->
            GeocodeCandidate(
                provider = try {
                    GeocodingProviderId.valueOf(entity.provider)
                } catch (_: Exception) {
                    GeocodingProviderId.CUSTOM
                },
                rank = entity.rank,
                displayName = entity.displayName,
                structuredParts = entity.structuredPartsJson?.let { parseStructuredAddress(it) },
                confidence = entity.confidence,
                licenseClass = try {
                    LicenseClass.valueOf(entity.licenseClass)
                } catch (_: Exception) {
                    LicenseClass.FREE
                },
                fetchedAt = entity.fetchedAt
            )
        }
    }

    override suspend fun getProviderStatus(): List<ProviderStatus> {
        return sortedProviders.map { provider ->
            ProviderStatus(
                providerId = provider.providerId,
                isAvailable = provider.isAvailable,
                lastSuccessAt = null,
                errorCount = 0,
                rateLimitRemaining = null
            )
        }
    }

    private fun licenseClassFor(providerId: GeocodingProviderId): LicenseClass {
        return when (providerId) {
            GeocodingProviderId.ANDROID_GEOCODER -> LicenseClass.FREE
            GeocodingProviderId.OVERPASS -> LicenseClass.ATTRIBUTION
            GeocodingProviderId.PHOTON -> LicenseClass.ATTRIBUTION
            GeocodingProviderId.NOMINATIM -> LicenseClass.ATTRIBUTION
            GeocodingProviderId.GOOGLE -> LicenseClass.PAID
            GeocodingProviderId.CUSTOM -> LicenseClass.FREE
        }
    }

    private fun StructuredAddress.toJson(): String {
        val parts = mutableListOf<String>()
        street?.let { parts.add("\"street\":\"${escapeJson(it)}\"") }
        houseNumber?.let { parts.add("\"houseNumber\":\"${escapeJson(it)}\"") }
        city?.let { parts.add("\"city\":\"${escapeJson(it)}\"") }
        state?.let { parts.add("\"state\":\"${escapeJson(it)}\"") }
        country?.let { parts.add("\"country\":\"${escapeJson(it)}\"") }
        postalCode?.let { parts.add("\"postalCode\":\"${escapeJson(it)}\"") }
        neighborhood?.let { parts.add("\"neighborhood\":\"${escapeJson(it)}\"") }
        return "{${parts.joinToString(",")}}"
    }

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun parseStructuredAddress(json: String): StructuredAddress? {
        return try {
            val obj = org.json.JSONObject(json)
            StructuredAddress(
                street = obj.optString("street", "").takeIf { it.isNotBlank() },
                houseNumber = obj.optString("houseNumber", "").takeIf { it.isNotBlank() },
                city = obj.optString("city", "").takeIf { it.isNotBlank() },
                state = obj.optString("state", "").takeIf { it.isNotBlank() },
                country = obj.optString("country", "").takeIf { it.isNotBlank() },
                postalCode = obj.optString("postalCode", "").takeIf { it.isNotBlank() },
                neighborhood = obj.optString("neighborhood", "").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse structured address JSON", e)
            null
        }
    }

    companion object {
        private const val TAG = "GeocodingRepository"
        private const val CANDIDATE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}
