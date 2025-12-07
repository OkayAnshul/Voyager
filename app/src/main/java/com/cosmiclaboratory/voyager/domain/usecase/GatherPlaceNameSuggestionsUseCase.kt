package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceNameSuggestion
import com.cosmiclaboratory.voyager.domain.model.SuggestionSource
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.UserCorrectionRepository
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * ISSUE #2: Gathers place name suggestions from ALL available sources
 *
 * Sources:
 * 1. OSM Overpass - Business/POI names
 * 2. OSM Nominatim - Place names from geocoding
 * 3. Android Geocoder - Address components
 * 4. Nearby POIs - Similar places nearby
 * 5. User History - Previous corrections at this location
 * 6. Category Default - Default name based on category
 */
class GatherPlaceNameSuggestionsUseCase @Inject constructor(
    private val geocodingRepository: GeocodingRepository,
    private val placeRepository: PlaceRepository,
    private val userCorrectionRepository: UserCorrectionRepository,
    private val logger: ProductionLogger
) {

    suspend operator fun invoke(place: Place): List<PlaceNameSuggestion> {
        val suggestions = mutableListOf<PlaceNameSuggestion>()

        try {
            // 1. OSM Overpass - Business/POI names from place details
            val placeDetails = geocodingRepository.getPlaceDetailsForCoordinates(
                place.latitude,
                place.longitude
            )
            placeDetails?.let { details ->
                if (!details.name.isNullOrBlank() && details.name != place.name) {
                    suggestions.add(
                        PlaceNameSuggestion(
                            name = details.name,
                            source = SuggestionSource.OSM_OVERPASS,
                            confidence = 0.9f,
                            metadata = mapOf(
                                "type" to (details.type ?: "unknown"),
                                "osmType" to (details.osmType ?: "unknown")
                            )
                        )
                    )
                }
            }

            // 2. OSM Nominatim - Place name from address result
            val addressResult = geocodingRepository.getAddressForCoordinates(
                place.latitude,
                place.longitude
            )
            addressResult?.let { result ->
                // Try to extract a meaningful place name from address
                val placeName = result.locality ?: result.streetName ?: result.subLocality
                if (!placeName.isNullOrBlank() && placeName != place.name) {
                    suggestions.add(
                        PlaceNameSuggestion(
                            name = placeName,
                            source = SuggestionSource.OSM_NOMINATIM,
                            confidence = 0.8f,
                            metadata = mapOf(
                                "full_address" to result.formattedAddress
                            )
                        )
                    )
                }

                // 3. Geocoding - Address components as suggestions
                if (!result.streetName.isNullOrBlank() && result.streetName != place.name) {
                    suggestions.add(
                        PlaceNameSuggestion(
                            name = result.streetName,
                            source = SuggestionSource.GEOCODING,
                            confidence = 0.7f,
                            metadata = mapOf("component" to "street")
                        )
                    )
                }

                if (!result.locality.isNullOrBlank() && result.locality != place.name) {
                    suggestions.add(
                        PlaceNameSuggestion(
                            name = result.locality,
                            source = SuggestionSource.GEOCODING,
                            confidence = 0.6f,
                            metadata = mapOf("component" to "locality")
                        )
                    )
                }
            }

            // 4. Nearby POIs - Find similar named places within 0.2km radius
            val nearbyPlaces = placeRepository.getPlacesNearLocation(
                latitude = place.latitude,
                longitude = place.longitude,
                radiusKm = 0.2
            )

            nearbyPlaces
                .filter { it.id != place.id && it.isUserRenamed }
                .take(3) // Limit to top 3 nearby
                .forEach { nearbyPlace ->
                    if (nearbyPlace.name != place.name) {
                        suggestions.add(
                            PlaceNameSuggestion(
                                name = nearbyPlace.name,
                                source = SuggestionSource.NEARBY_POI,
                                confidence = 0.5f,
                                metadata = mapOf(
                                    "distance" to String.format("%.0fm", calculateDistance(place, nearbyPlace)),
                                    "visits" to nearbyPlace.visitCount.toString()
                                )
                            )
                        )
                    }
                }

            // 5. User History - Previous name corrections for this place
            try {
                val recentCorrections = userCorrectionRepository.getCorrectionsForPlace(place.id)
                    .first()
                    .filter { it.correctionType == CorrectionType.NAME_CHANGE }
                    .take(2)

                recentCorrections.forEach { correction ->
                    if (correction.newValue != place.name) {
                        suggestions.add(
                            PlaceNameSuggestion(
                                name = correction.newValue,
                                source = SuggestionSource.USER_HISTORY,
                                confidence = 0.85f,
                                metadata = mapOf(
                                    "timestamp" to correction.correctionTime.toString(),
                                    "original" to correction.oldValue
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // User corrections are optional, continue if they fail
                logger.d("GatherPlaceNameSuggestionsUseCase", "Failed to load user corrections: ${e.message}")
            }

            // 6. Category Default - Default name based on category
            val categoryDefault = getCategoryDefaultName(place)
            if (categoryDefault != null && categoryDefault != place.name) {
                suggestions.add(
                    PlaceNameSuggestion(
                        name = categoryDefault,
                        source = SuggestionSource.CATEGORY_DEFAULT,
                        confidence = 0.4f,
                        metadata = mapOf("category" to place.category.name)
                    )
                )
            }

            // Sort by confidence (highest first) and remove duplicates
            val uniqueSuggestions = suggestions
                .distinctBy { it.name.lowercase().trim() }
                .sortedByDescending { it.confidence }
                .take(10) // Limit to top 10 suggestions

            logger.d("GatherPlaceNameSuggestionsUseCase",
                "Found ${uniqueSuggestions.size} suggestions for place: ${place.name}")

            return uniqueSuggestions

        } catch (e: Exception) {
            logger.e("GatherPlaceNameSuggestionsUseCase",
                "Failed to gather suggestions for place ${place.id}", e)
            return emptyList()
        }
    }

    private fun getCategoryDefaultName(place: Place): String? {
        return when (place.category) {
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.HOME -> "Home"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.WORK -> "Work"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.GYM -> "Gym"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.RESTAURANT -> "Restaurant"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.SHOPPING -> "Shopping"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.ENTERTAINMENT -> "Entertainment"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.TRANSPORT -> "Transport"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.TRAVEL -> "Travel"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.HEALTHCARE -> "Healthcare"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.EDUCATION -> "School"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.OUTDOOR -> "Outdoor Location"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.SOCIAL -> "Social"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.SERVICES -> "Services"
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.CUSTOM -> null
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.UNKNOWN -> null
        }
    }

    /**
     * Calculate distance between two places in meters
     */
    private fun calculateDistance(place1: Place, place2: Place): Double {
        return calculateDistance(
            place1.latitude, place1.longitude,
            place2.latitude, place2.longitude
        )
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     * @return Distance in meters
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}
