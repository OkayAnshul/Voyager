package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType

data class SearchFilters(
    val dateRange: DateRange? = null,
    val placeCategories: Set<PlaceCategory>? = null,
    val transportModes: Set<SegmentType>? = null,
    val minDwellMs: Long? = null,
    val maxDistanceM: Double? = null
)

data class SearchResults(
    val query: String,
    val places: List<PlaceSearchResult>,
    val visits: List<VisitSearchResult>,
    val days: List<DaySearchResult>,
    val totalCount: Int
)

data class PlaceSearchResult(
    val placeId: Long,
    val displayName: String,
    val category: PlaceCategory,
    val visitCount: Int,
    val relevanceScore: Float
)

data class VisitSearchResult(
    val visitId: Long,
    val placeDisplayName: String,
    val arrivalAt: Long,
    val departureAt: Long?,
    val dayKey: String,
    val relevanceScore: Float
)

data class DaySearchResult(
    val dayKey: String,
    val matchingSegmentCount: Int,
    val matchingPlaceCount: Int,
    val relevanceScore: Float
)
