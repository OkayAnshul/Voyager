package com.cosmiclaboratory.voyager.data.api

/**
 * Interface for geocoding services
 * Abstracts different geocoding providers (Android Geocoder, Nominatim, etc.)
 */
interface GeocodingService {
    /**
     * Reverse geocode coordinates to address
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return AddressResult or null if unavailable
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): AddressResult?

    /**
     * Get place details from coordinates
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return PlaceDetails with name, type, etc. or null
     */
    suspend fun getPlaceDetails(latitude: Double, longitude: Double): PlaceDetails?

    /**
     * Check if service is available
     * @return true if service can be used
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Result of reverse geocoding
 */
data class AddressResult(
    val formattedAddress: String,
    val streetName: String? = null,
    val locality: String? = null,         // City/town
    val subLocality: String? = null,      // Neighborhood/area
    val postalCode: String? = null,
    val countryCode: String? = null
)

/**
 * Place details from geocoding
 */
data class PlaceDetails(
    val name: String? = null,             // Business name (e.g., "Starbucks")
    val type: String? = null,             // Place type (e.g., "cafe", "gym")
    val formattedAddress: String? = null,
    // Week 4: OSM Overpass API enhancement
    val osmType: String? = null,          // OSM type (e.g., "amenity", "shop", "leisure")
    val osmValue: String? = null,         // OSM value (e.g., "restaurant", "supermarket", "gym")
    val osmId: Long? = null,              // OSM element ID for caching
    val distance: Double? = null          // Distance from query point in meters
)
