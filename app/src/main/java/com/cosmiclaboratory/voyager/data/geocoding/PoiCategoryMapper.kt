package com.cosmiclaboratory.voyager.data.geocoding

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory

/**
 * Maps an OSM POI tag (key=value form) to a [PlaceCategory] when the tag is
 * an unambiguous signal of place type.
 *
 * Ambiguous tags (e.g. `office=*`, `building=*`) return null — we won't
 * silently set a category we can't justify. The caller treats null as
 * "leave the category untouched."
 *
 * Input string is the same `type` string the Overpass API service returns
 * ("amenity=cafe", "shop=supermarket", etc.). Comparisons are case-insensitive
 * on the value side because OSM data is occasionally inconsistent.
 */
object PoiCategoryMapper {

    fun fromOsmType(type: String?): PlaceCategory? {
        if (type.isNullOrBlank()) return null
        val (key, value) = type.split('=', limit = 2).let {
            if (it.size != 2) return null
            it[0].lowercase() to it[1].lowercase()
        }
        return when (key) {
            "amenity" -> mapAmenity(value)
            "shop" -> PlaceCategory.SHOPPING // all shops → SHOPPING, regardless of specific value
            "leisure" -> mapLeisure(value)
            "tourism" -> mapTourism(value)
            "aeroway", "railway" -> if (value in transitHubValues) PlaceCategory.TRANSIT_HUB else null
            "public_transport" -> if (value in transitHubValues) PlaceCategory.TRANSIT_HUB else null
            // office=* is ambiguous (coworking, NGO, government, lawyer) — never WORK by tag.
            // building=* describes structure, not function — also ambiguous.
            else -> null
        }
    }

    private fun mapAmenity(value: String): PlaceCategory? = when (value) {
        "restaurant", "cafe", "fast_food", "bar", "pub", "food_court", "ice_cream", "biergarten" ->
            PlaceCategory.RESTAURANT
        "gym", "sports_centre", "fitness_centre", "fitness_station" ->
            PlaceCategory.GYM
        "hospital", "clinic", "pharmacy", "doctors", "dentist", "veterinary" ->
            PlaceCategory.HEALTHCARE
        "school", "university", "college", "library", "kindergarten", "language_school", "music_school", "driving_school" ->
            PlaceCategory.EDUCATION
        "cinema", "theatre", "nightclub", "arts_centre", "casino", "events_venue" ->
            PlaceCategory.ENTERTAINMENT
        "bus_station", "ferry_terminal", "taxi" ->
            PlaceCategory.TRANSIT_HUB
        "bank", "post_office", "atm" ->
            PlaceCategory.SERVICES
        "place_of_worship", "community_centre", "social_centre" ->
            PlaceCategory.SOCIAL
        else -> null
    }

    private fun mapLeisure(value: String): PlaceCategory? = when (value) {
        "park", "playground", "garden", "nature_reserve" -> PlaceCategory.OUTDOOR
        "sports_centre", "fitness_centre", "swimming_pool", "stadium", "track", "pitch" ->
            PlaceCategory.GYM
        "cinema", "amusement_arcade", "bowling_alley" -> PlaceCategory.ENTERTAINMENT
        else -> null
    }

    private fun mapTourism(value: String): PlaceCategory? = when (value) {
        "hotel", "motel", "hostel", "guest_house", "apartment", "chalet" ->
            PlaceCategory.TRAVEL
        "museum", "gallery", "attraction", "theme_park", "zoo", "aquarium" ->
            PlaceCategory.ENTERTAINMENT
        else -> null
    }

    private val transitHubValues = setOf(
        "station", "halt", "stop_position", "platform", "subway_entrance",
        "tram_stop", "aerodrome", "terminal"
    )
}
