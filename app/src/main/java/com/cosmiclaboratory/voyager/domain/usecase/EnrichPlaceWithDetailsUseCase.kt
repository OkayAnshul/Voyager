package com.cosmiclaboratory.voyager.domain.usecase

import android.util.Log
import com.cosmiclaboratory.voyager.data.api.AddressResult
import com.cosmiclaboratory.voyager.data.api.PlaceDetails
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.utils.OsmTypeMapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for enriching places with real addresses and names from geocoding
 *
 * Strategy:
 * 1. Get address from geocoding repository (cache → Android Geocoder → Nominatim)
 * 2. Try to get place details (business name, type) from Nominatim
 * 3. Generate smart name combining:
 *    - Business name from geocoding (if available)
 *    - ML category (Home, Work, Gym)
 *    - Location context (neighborhood/city)
 * 4. Update place with all available information
 *
 * Smart Naming Priority:
 * 1. Business name from Nominatim ("Starbucks", "Planet Fitness")
 * 2. Category for Home/Work ("Home", "Work")
 * 3. Category + locality ("Gym in Downtown", "Restaurant in Midtown")
 * 4. Fallback to category name ("Unknown Place")
 */
@Singleton
class EnrichPlaceWithDetailsUseCase @Inject constructor(
    private val geocodingRepository: GeocodingRepository
) {
    /**
     * Enrich a place with real address and intelligent name
     * @param place Place to enrich
     * @return Updated place with address details and smart name
     */
    suspend operator fun invoke(place: Place): Place {
        Log.d(TAG, "Enriching place: ${place.name} at ${place.latitude}, ${place.longitude}")

        // Don't re-enrich if user has manually renamed the place
        if (place.isUserRenamed) {
            Log.d(TAG, "Place is user-renamed, skipping enrichment")
            return place
        }

        try {
            // Get address from geocoding repository
            val address = geocodingRepository.getAddressForCoordinates(
                place.latitude,
                place.longitude
            )

            // Try to get business name and type
            val placeDetails = geocodingRepository.getPlaceDetailsForCoordinates(
                place.latitude,
                place.longitude
            )

            // Week 4: Map OSM type to category (store as SUGGESTION only - don't auto-apply)
            val osmSuggestedCategory = OsmTypeMapper.mapOsmToCategory(
                placeDetails?.osmType,
                placeDetails?.osmValue
            )

            // CRITICAL: Do NOT override category - let user confirm OSM suggestion
            // OSM suggestion is stored separately in osmSuggestedCategory field
            // Category remains unchanged until user explicitly confirms

            // Generate smart name
            val smartName = generateSmartName(place, address, placeDetails)

            // Return enriched place
            return place.copy(
                // category = place.category,  // Keep original category unchanged
                name = smartName,
                address = address?.formattedAddress,
                streetName = address?.streetName,
                locality = address?.locality,
                subLocality = address?.subLocality,
                postalCode = address?.postalCode,
                countryCode = address?.countryCode,
                // Week 4: OSM enrichment fields
                osmSuggestedName = placeDetails?.name,
                osmSuggestedCategory = osmSuggestedCategory,
                osmPlaceType = OsmTypeMapper.getOsmTypeDescription(
                    placeDetails?.osmType,
                    placeDetails?.osmValue
                )
            ).also {
                Log.d(TAG, "Enriched place: ${it.name} (address: ${it.address}, OSM: ${it.osmPlaceType})")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich place: ${place.name}", e)
            // Return original place on error
            return place
        }
    }

    /**
     * Generate smart place name combining multiple data sources
     *
     * Priority:
     * 1. Real business name from Overpass/Nominatim (e.g., "Starbucks Coffee", "Planet Fitness")
     * 2. Category-only for HOME/WORK/EDUCATION (e.g., "Home", "Work", "Education")
     * 3. Address-based name if no business name (e.g., "Location on Main St")
     * 4. Category + location context (e.g., "Gym in Downtown")
     * 5. Category name (e.g., "Restaurant", "Shopping")
     */
    private fun generateSmartName(
        place: Place,
        address: AddressResult?,
        details: PlaceDetails?
    ): String {
        // Check if user has manually labeled this place
        if (place.isUserRenamed && place.name.isNotBlank()) {
            Log.i(TAG, "Using user-provided name: ${place.name}")
            return place.name  // "My Home", "My Office", "Favorite Gym"
        }

        // PRIORITY 1: Real business name from Overpass/Nominatim (HIGHEST)
        details?.name?.let { name ->
            if (name.isNotBlank() && name != "null" && !isGenericName(name)) {
                Log.i(TAG, "Using business name: $name")
                return name  // "Starbucks", "Planet Fitness", "Joe's Pizza"
            }
        }

        // PRIORITY 2: Address-based name (specific street or landmark)
        address?.streetName?.let { streetName ->
            if (streetName.isNotBlank()) {
                Log.d(TAG, "Using address-based name: $streetName")
                return streetName
            }
        }

        // PRIORITY 3: Locality/neighborhood name
        val locationContext = address?.subLocality ?: address?.locality
        if (locationContext != null && locationContext.isNotBlank()) {
            Log.d(TAG, "Using locality name: $locationContext")
            return locationContext
        }

        // PRIORITY 4: Coordinates as fallback (lat, lng)
        val coordName = "Location (${String.format("%.4f", place.latitude)}, ${String.format("%.4f", place.longitude)})"
        Log.d(TAG, "Using coordinate fallback: $coordName")
        return coordName
    }

    /**
     * Check if name is generic/useless
     */
    private fun isGenericName(name: String): Boolean {
        val lowerName = name.lowercase()
        val generic = setOf("building", "place", "location", "unnamed", "null", "untitled", "unknown")
        return generic.any { lowerName.contains(it) }
    }

    companion object {
        private const val TAG = "EnrichPlaceUseCase"
    }
}
