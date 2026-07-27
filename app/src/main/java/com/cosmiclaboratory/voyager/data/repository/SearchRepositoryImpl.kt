package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.domain.repository.SearchRepository
import com.cosmiclaboratory.voyager.domain.usecase.SearchQuery
import com.cosmiclaboratory.voyager.domain.usecase.SearchTimelineUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.SearchDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.SearchIndexEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SearchMetadataEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val geocodingRepository: GeocodingRepository,
    private val searchTimelineUseCase: SearchTimelineUseCase
) : SearchRepository {

    override fun search(query: String, filters: SearchFilters): Flow<SearchResults> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank() && filters.isEmpty()) {
            emit(SearchResults(query, emptyList(), emptyList(), emptyList(), 0))
            return@flow
        }
        emit(
            if (filters.isEmpty()) ftsSearch(trimmed)
            else structuredSearch(trimmed, filters)
        )
    }

    /**
     * Fast full-text path used when no filters are active. Matches place names (and their recent
     * visits) plus indexed day-keys, so a query like "2026-06" surfaces the matching days.
     * Results come back ranked by the FTS relevance boost.
     */
    private suspend fun ftsSearch(query: String): SearchResults {
        // Split on any non-alphanumeric run so the query mirrors the unicode61 tokenizer and we
        // never feed FTS operators (a stray "-" is the NOT operator). Each token gets a trailing
        // "*" for FTS4 prefix matching (bare `term*`, not the FTS5 `"term"*` form).
        val tokens = query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return SearchResults(query, emptyList(), emptyList(), emptyList(), 0)
        }
        val ftsQuery = tokens.joinToString(" ") { "$it*" }
        val hits = searchDao.searchHits(ftsQuery)

        val places = mutableListOf<PlaceSearchResult>()
        val visits = mutableListOf<VisitSearchResult>()
        val dayResults = linkedMapOf<String, DaySearchResult>()
        val seenPlaces = mutableSetOf<Long>()

        for (hit in hits) {
            when (hit.sourceTable) {
                "PLACE" -> {
                    val place = placeDao.getById(hit.sourceId) ?: continue
                    if (!seenPlaces.add(place.placeId)) continue
                    val placeName = geocodingRepository.resolveDisplayName(place.placeId)
                    places.add(
                        PlaceSearchResult(
                            placeId = place.placeId,
                            displayName = placeName,
                            category = place.category.toCategory(),
                            visitCount = visitDao.countByPlaceId(place.placeId),
                            relevanceScore = hit.relevanceBoost
                        )
                    )
                    // Surface the place's most recent visits (and the days they fall on).
                    for (v in visitDao.getByPlaceId(place.placeId).take(5)) {
                        visits.add(v.toResult(placeName, hit.relevanceBoost))
                    }
                }
                "DAY" -> {
                    val dayKey = hit.dayKey ?: continue
                    if (dayResults.containsKey(dayKey)) continue
                    dayResults[dayKey] = dayKey.toDayResult(hit.relevanceBoost)
                }
            }
        }

        // Fold visit-derived days in behind any directly-matched days.
        for (v in visits) dayResults.getOrPut(v.dayKey) { v.dayKey.toDayResult(1.0f) }

        val days = dayResults.values.toList()
        return SearchResults(
            query = query,
            places = places,
            visits = visits,
            days = days,
            totalCount = places.size + visits.size + days.size
        )
    }

    /**
     * Structured path used when the user applies any filter. Delegates category / date-range /
     * transport-mode / distance filtering to [SearchTimelineUseCase] (which scans the indexed
     * DAO surface) and maps its entity results into the UI's [SearchResults].
     */
    private suspend fun structuredSearch(query: String, filters: SearchFilters): SearchResults {
        val (startMs, endMs) = filters.dateRange?.let { dayRangeToMs(it) } ?: (null to null)
        val result = searchTimelineUseCase.search(
            SearchQuery(
                text = query.ifBlank { null },
                startMs = startMs,
                endMs = endMs,
                categories = filters.placeCategories,
                segmentTypes = filters.transportModes?.map { it.name }?.toSet(),
                minDistanceM = null,
                maxDistanceM = filters.maxDistanceM
            )
        )

        val nameCache = mutableMapOf<Long, String>()
        suspend fun nameOf(placeId: Long): String =
            nameCache.getOrPut(placeId) { geocodingRepository.resolveDisplayName(placeId) }

        val places = result.places.map { place ->
            PlaceSearchResult(
                placeId = place.placeId,
                displayName = nameOf(place.placeId),
                category = place.category.toCategory(),
                visitCount = visitDao.countByPlaceId(place.placeId),
                relevanceScore = 1.0f
            )
        }

        // Dwell filter has no analogue in the use-case query — apply it here.
        val minDwell = filters.minDwellMs
        val visits = result.visits
            .filter { v -> minDwell == null || (v.departureAt != null && v.departureAt - v.arrivalAt >= minDwell) }
            .map { it.toResult(nameOf(it.placeId), 1.0f) }

        val days = buildDays(visits, result.segments)
        return SearchResults(
            query = query,
            places = places,
            visits = visits,
            days = days,
            totalCount = places.size + visits.size + days.size
        )
    }

    /** Groups matching visits + segments into per-day result rows, most-recent first. */
    private fun buildDays(
        visits: List<VisitSearchResult>,
        segments: List<MovementSegmentEntity>
    ): List<DaySearchResult> {
        val placeCountByDay = visits.groupingBy { it.dayKey }.eachCount()
        val segmentCountByDay = segments.groupingBy { it.dayKey }.eachCount()
        val dayKeys = (placeCountByDay.keys + segmentCountByDay.keys).sortedDescending()
        return dayKeys.map { dayKey ->
            DaySearchResult(
                dayKey = dayKey,
                matchingSegmentCount = segmentCountByDay[dayKey] ?: 0,
                matchingPlaceCount = placeCountByDay[dayKey] ?: 0,
                relevanceScore = 1.0f
            )
        }
    }

    private suspend fun String.toDayResult(relevance: Float): DaySearchResult =
        DaySearchResult(
            dayKey = this,
            matchingSegmentCount = movementSegmentDao.countByDayKey(this),
            matchingPlaceCount = visitDao.getByDayKey(this).size,
            relevanceScore = relevance
        )

    private fun VisitEntity.toResult(placeName: String, relevance: Float): VisitSearchResult =
        VisitSearchResult(
            visitId = visitId,
            placeDisplayName = placeName,
            arrivalAt = arrivalAt,
            departureAt = departureAt,
            dayKey = dayKey,
            relevanceScore = relevance
        )

    private fun String.toCategory(): PlaceCategory =
        try { PlaceCategory.valueOf(this) } catch (_: Exception) { PlaceCategory.UNKNOWN }

    /** Converts a [DateRange] of YYYY-MM-DD day-keys into an inclusive epoch-millis window. */
    private fun dayRangeToMs(range: DateRange): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.parse(range.startDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.parse(range.endDay).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    override suspend fun rebuildSearchIndex(): Result<Unit> = runCatching {
        val places = placeDao.getAllActive()
        val entries = places.map { place ->
            Pair(
                SearchIndexEntity(
                    placeDisplayName = place.userDisplayName ?: place.bestProviderName,
                    placeCategory = place.category,
                    dayKey = null,
                    segmentType = null,
                    geocodeDisplayName = place.bestProviderName,
                    userNotes = null
                ),
                SearchMetadataEntity(
                    searchRowId = 0,
                    sourceTable = "PLACE",
                    sourceId = place.placeId,
                    relevanceBoost = if (place.userDisplayName != null) 2.0f else 1.0f
                )
            )
        }
        searchDao.rebuildIndex(entries)
    }
}
