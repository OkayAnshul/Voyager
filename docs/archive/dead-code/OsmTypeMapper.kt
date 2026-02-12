package com.cosmiclaboratory.voyager.utils

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory

/**
 * Week 4: Maps OSM (OpenStreetMap) types to Voyager PlaceCategory
 *
 * OSM uses a key-value tagging system:
 * - amenity=restaurant
 * - shop=supermarket
 * - leisure=gym
 *
 * This mapper converts these OSM tags to our internal PlaceCategory enum
 */
object OsmTypeMapper {

    /**
     * Map OSM type and value to PlaceCategory
     * @param osmType OSM key (e.g., "amenity", "shop", "leisure")
     * @param osmValue OSM value (e.g., "restaurant", "supermarket", "gym")
     * @return Matched PlaceCategory or null if no match
     */
    fun mapOsmToCategory(osmType: String?, osmValue: String?): PlaceCategory? {
        if (osmType == null || osmValue == null) return null

        return when (osmType.lowercase()) {
            "amenity" -> mapAmenityToCategory(osmValue)
            "shop" -> mapShopToCategory(osmValue)
            "leisure" -> mapLeisureToCategory(osmValue)
            "tourism" -> mapTourismToCategory(osmValue)
            "healthcare" -> PlaceCategory.HEALTHCARE
            "office" -> PlaceCategory.WORK
            "building" -> mapBuildingToCategory(osmValue)
            else -> null
        }
    }

    /**
     * Map OSM amenity values to categories
     */
    private fun mapAmenityToCategory(value: String): PlaceCategory? {
        return when (value.lowercase()) {
            // Food & Drink
            "restaurant", "fast_food", "food_court" -> PlaceCategory.RESTAURANT
            "cafe", "bar", "pub", "biergarten", "nightclub" -> PlaceCategory.RESTAURANT

            // Shopping
            "marketplace" -> PlaceCategory.SHOPPING

            // Healthcare
            "hospital", "clinic", "doctors", "dentist", "pharmacy",
            "veterinary" -> PlaceCategory.HEALTHCARE

            // Education
            "school", "university", "college", "kindergarten",
            "library" -> PlaceCategory.EDUCATION

            // Entertainment
            "cinema", "theatre", "arts_centre", "casino",
            "community_centre", "events_venue" -> PlaceCategory.ENTERTAINMENT

            // Transport
            "parking", "bus_station", "taxi", "fuel",
            "charging_station" -> PlaceCategory.TRANSPORT

            // Services
            "bank", "atm", "post_office", "police", "fire_station",
            "townhall", "courthouse", "embassy" -> PlaceCategory.SERVICES

            // Social
            "place_of_worship", "social_facility" -> PlaceCategory.SOCIAL

            else -> null
        }
    }

    /**
     * Map OSM shop values to categories
     */
    private fun mapShopToCategory(value: String): PlaceCategory? {
        return when (value.lowercase()) {
            // General shopping
            "supermarket", "convenience", "general", "department_store",
            "mall", "wholesale" -> PlaceCategory.SHOPPING

            // Specialized shops (all shopping)
            "clothes", "shoes", "jewelry", "books", "electronics",
            "furniture", "hardware", "florist", "gift", "toys",
            "sports", "bicycle", "car", "mobile_phone", "computer",
            "bakery", "butcher", "greengrocer", "alcohol", "beverages",
            "seafood", "beauty", "hairdresser", "chemist", "cosmetics",
            "pet", "stationery", "newsagent", "optician", "tattoo",
            "dry_cleaning", "laundry" -> PlaceCategory.SHOPPING

            else -> PlaceCategory.SHOPPING // Default all shops to shopping
        }
    }

    /**
     * Map OSM leisure values to categories
     */
    private fun mapLeisureToCategory(value: String): PlaceCategory? {
        return when (value.lowercase()) {
            // Fitness
            "sports_centre", "fitness_centre", "fitness_station",
            "swimming_pool", "stadium" -> PlaceCategory.GYM

            // Entertainment
            "amusement_arcade", "bowling_alley", "escape_game",
            "miniature_golf", "trampoline_park" -> PlaceCategory.ENTERTAINMENT

            // Outdoor
            "park", "garden", "playground", "dog_park", "pitch",
            "track", "golf_course", "marina", "beach_resort",
            "nature_reserve" -> PlaceCategory.OUTDOOR

            // Social
            "adult_gaming_centre" -> PlaceCategory.SOCIAL

            else -> PlaceCategory.OUTDOOR // Default leisure to outdoor
        }
    }

    /**
     * Map OSM tourism values to categories
     */
    private fun mapTourismToCategory(value: String): PlaceCategory? {
        return when (value.lowercase()) {
            // Travel/Accommodation
            "hotel", "motel", "hostel", "guest_house",
            "apartment", "camp_site", "caravan_site" -> PlaceCategory.TRAVEL

            // Entertainment/Attractions
            "attraction", "museum", "gallery", "zoo",
            "aquarium", "theme_park", "viewpoint" -> PlaceCategory.ENTERTAINMENT

            // Services
            "information", "tourist_info" -> PlaceCategory.SERVICES

            else -> PlaceCategory.TRAVEL
        }
    }

    /**
     * Map OSM building values to categories
     */
    private fun mapBuildingToCategory(value: String): PlaceCategory? {
        return when (value.lowercase()) {
            // Residential
            "house", "residential", "apartments", "detached",
            "semidetached_house", "terrace" -> PlaceCategory.HOME

            // Commercial/Office
            "office", "commercial", "retail" -> PlaceCategory.WORK

            // Healthcare
            "hospital", "clinic" -> PlaceCategory.HEALTHCARE

            // Education
            "school", "university", "college" -> PlaceCategory.EDUCATION

            // Transport
            "train_station", "transportation" -> PlaceCategory.TRANSPORT

            // Entertainment
            "stadium", "sports_hall" -> PlaceCategory.ENTERTAINMENT

            else -> null
        }
    }

    /**
     * Get confidence boost based on OSM match quality
     * @param detectedCategory Our ML-detected category
     * @param osmCategory Category mapped from OSM
     * @return Confidence boost (0.0 to 0.20)
     */
    fun getOsmConfidenceBoost(
        detectedCategory: PlaceCategory,
        osmCategory: PlaceCategory?
    ): Float {
        if (osmCategory == null) return 0f

        return when {
            detectedCategory == osmCategory -> 0.15f  // Exact match: high boost
            areSimilarCategories(detectedCategory, osmCategory) -> 0.08f  // Similar: medium boost
            else -> 0f  // Different: no boost
        }
    }

    /**
     * Check if two categories are similar enough to boost confidence
     */
    private fun areSimilarCategories(cat1: PlaceCategory, cat2: PlaceCategory): Boolean {
        val similarGroups = listOf(
            setOf(PlaceCategory.RESTAURANT, PlaceCategory.SHOPPING),  // Food retail overlap
            setOf(PlaceCategory.GYM, PlaceCategory.OUTDOOR),  // Fitness overlap
            setOf(PlaceCategory.ENTERTAINMENT, PlaceCategory.SOCIAL),  // Social overlap
            setOf(PlaceCategory.SERVICES, PlaceCategory.WORK)  // Professional overlap
        )

        return similarGroups.any { group -> cat1 in group && cat2 in group }
    }

    /**
     * Get human-readable OSM type description
     */
    fun getOsmTypeDescription(osmType: String?, osmValue: String?): String? {
        if (osmType == null || osmValue == null) return null

        return when (osmType.lowercase()) {
            "amenity" -> "Amenity: ${osmValue.replace('_', ' ').capitalize()}"
            "shop" -> "Shop: ${osmValue.replace('_', ' ').capitalize()}"
            "leisure" -> "Leisure: ${osmValue.replace('_', ' ').capitalize()}"
            "tourism" -> "Tourism: ${osmValue.replace('_', ' ').capitalize()}"
            "healthcare" -> "Healthcare: ${osmValue.replace('_', ' ').capitalize()}"
            "office" -> "Office: ${osmValue.replace('_', ' ').capitalize()}"
            "building" -> "Building: ${osmValue.replace('_', ' ').capitalize()}"
            else -> "${osmType.capitalize()}: ${osmValue.replace('_', ' ').capitalize()}"
        }
    }
}

/**
 * Extension function to capitalize first letter
 */
private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
