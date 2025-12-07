package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

data class Place(
    val id: Long = 0L,
    val name: String,
    val category: PlaceCategory,
    val customCategoryName: String? = null,   // ISSUE #3: For CUSTOM category user names
    val latitude: Double,
    val longitude: Double,

    // Geocoding fields (from free APIs: Android Geocoder + OSM Nominatim)
    val address: String? = null,              // Full formatted address
    val streetName: String? = null,           // Street/road name
    val locality: String? = null,             // City/town name
    val subLocality: String? = null,          // Neighborhood/area name
    val postalCode: String? = null,           // Postal/ZIP code
    val countryCode: String? = null,          // ISO country code (e.g., "US", "IN")
    val isUserRenamed: Boolean = false,       // True if user manually renamed place
    val needsUserNaming: Boolean = false,     // True if geocoding failed and place needs manual naming

    // OSM enrichment fields (Week 3: Auto-accept decision support)
    val osmSuggestedName: String? = null,     // Name from OSM (e.g., "Starbucks Coffee")
    val osmSuggestedCategory: PlaceCategory? = null,  // Category mapped from OSM type
    val osmPlaceType: String? = null,         // Raw OSM place type (e.g., "cafe", "restaurant")

    // Phase 2: Activity-based insights
    val dominantActivity: UserActivity? = null,           // Most common activity at this place
    val activityDistribution: Map<UserActivity, Float> = emptyMap(), // % time per activity
    val dominantSemanticContext: SemanticContext? = null, // What you usually do here
    val contextDistribution: Map<SemanticContext, Float> = emptyMap(), // % time per context

    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0L, // in milliseconds
    val lastVisit: LocalDateTime? = null,
    val isCustom: Boolean = false,
    val radius: Double = 100.0, // in meters
    val placeId: String? = null, // Google Places ID (for future premium features)
    val confidence: Float = 1.0f // Confidence level for automatic categorization (0.0 - 1.0)
) {
    /**
     * Phase 2: Get the best available name for this place
     * Priority: User custom name > OSM name > Geocoded address > Category
     */
    fun getBestName(): String {
        return when {
            isUserRenamed && name.isNotBlank() -> name
            !osmSuggestedName.isNullOrBlank() -> osmSuggestedName
            !address.isNullOrBlank() -> {
                // Extract business name from address if present
                address.split(",").firstOrNull()?.trim() ?: address
            }
            else -> category.displayName
        }
    }

    /**
     * Check if this place needs user to provide a name
     */
    fun requiresNaming(): Boolean {
        return needsUserNaming ||
               (osmSuggestedName.isNullOrBlank() && address.isNullOrBlank() && !isUserRenamed)
    }

    /**
     * ISSUE #3: Get the display name for the category
     * For CUSTOM category, returns the customCategoryName if provided
     */
    fun getDisplayCategory(): String {
        return when (category) {
            PlaceCategory.CUSTOM -> customCategoryName ?: "Custom Place"
            else -> category.displayName
        }
    }
}

enum class PlaceCategory(val displayName: String) {
    HOME("Home"),
    WORK("Work"),
    GYM("Gym"),
    RESTAURANT("Restaurant"),
    SHOPPING("Shopping"),
    ENTERTAINMENT("Entertainment"),
    HEALTHCARE("Healthcare"),
    EDUCATION("Education"),
    TRANSPORT("Transport"),
    TRAVEL("Travel"),
    OUTDOOR("Outdoor"),
    SOCIAL("Social"),
    SERVICES("Services"),
    UNKNOWN("Unknown Place"),
    CUSTOM("Custom Place")
}

data class PlaceWithVisits(
    val place: Place,
    val visits: List<Visit>
)