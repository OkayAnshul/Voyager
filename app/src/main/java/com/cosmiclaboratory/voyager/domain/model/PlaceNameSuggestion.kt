package com.cosmiclaboratory.voyager.domain.model

/**
 * ISSUE #2: Represents a place name suggestion from various sources
 * Used to show user ALL available name options when renaming/reviewing a place
 */
data class PlaceNameSuggestion(
    val name: String,
    val source: SuggestionSource,
    val confidence: Float,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Sources for place name suggestions
 */
enum class SuggestionSource(val displayName: String) {
    OSM_OVERPASS("OpenStreetMap Business"),
    OSM_NOMINATIM("OpenStreetMap Place"),
    GEOCODING("Address"),
    NEARBY_POI("Nearby POI"),
    USER_HISTORY("Previous Correction"),
    CATEGORY_DEFAULT("Category Default");

    fun getBadgeColor(): String {
        return when (this) {
            OSM_OVERPASS -> "#4CAF50"      // Green
            OSM_NOMINATIM -> "#2196F3"     // Blue
            GEOCODING -> "#FF9800"         // Orange
            NEARBY_POI -> "#9C27B0"        // Purple
            USER_HISTORY -> "#F44336"      // Red
            CATEGORY_DEFAULT -> "#607D8B"  // Grey
        }
    }
}
