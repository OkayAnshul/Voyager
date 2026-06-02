package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import javax.inject.Inject

/**
 * Query parameters for a single search. Every field is optional — an empty
 * query string with no filters matches nothing (we don't want to return the
 * entire DB by accident from a misconfigured caller).
 */
data class SearchQuery(
    val text: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val categories: Set<PlaceCategory>? = null,
    val segmentTypes: Set<String>? = null,
    val minDistanceM: Double? = null,
    val maxDistanceM: Double? = null
)

data class SearchResult(
    val places: List<PlaceEntity>,
    val visits: List<VisitEntity>,
    val segments: List<MovementSegmentEntity>
)

/**
 * Lightweight cross-data search across places, visits, and segments.
 *
 * Powers queries like "when was I last in Tokyo?", "days I cycled >50 km",
 * "every restaurant I visited in March." Reads only — Room indices already
 * make the filtered scans cheap on phone-sized history.
 */
class SearchTimelineUseCase @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao,
) {

    suspend fun search(query: SearchQuery): SearchResult {
        if (query.isEmpty()) return SearchResult(emptyList(), emptyList(), emptyList())

        // Places first — text + category filter.
        val places = filterPlaces(placeDao.getAllActive(), query)
        val placeIds = places.map { it.placeId }.toSet()

        // Visits — date range + place link (when categories or text matched places).
        val visits = filterVisits(allVisitsInRange(query), query, placeIds)

        // Segments — date range + type + distance band.
        val segments = filterSegments(allSegmentsInRange(query), query, placeIds)

        return SearchResult(places, visits, segments)
    }

    private suspend fun allVisitsInRange(query: SearchQuery): List<VisitEntity> {
        // Range-scan optimisation: when the caller bounded a date range, use day-key
        // queries for visits/segments via the existing DAO surface; otherwise fall
        // back to scanning all known day-keys (the DAO already orders + de-dupes).
        val (startDay, endDay) = query.dayKeyRange()
        return if (startDay != null && endDay != null) {
            val dayKeys = visitDao.getAllDayKeys().filter { it in startDay..endDay }
            dayKeys.flatMap { visitDao.getByDayKey(it) }
        } else {
            visitDao.getAllDayKeys().flatMap { visitDao.getByDayKey(it) }
        }
    }

    private suspend fun allSegmentsInRange(query: SearchQuery): List<MovementSegmentEntity> {
        val (startDay, endDay) = query.dayKeyRange()
        return if (startDay != null && endDay != null) {
            val types = query.segmentTypes?.toList() ?: ALL_SEGMENT_TYPES
            movementSegmentDao.getByTypesBetween(types, startDay, endDay)
        } else {
            // No bound — fall back to recent-only scan via getLatest history isn't
            // exposed, so we walk known day-keys and pull each. Bounded by the
            // user's typical history size on phone.
            val dayKeys = movementSegmentDao.getAllDayKeys()
            dayKeys.flatMap { movementSegmentDao.getByDayKey(it) }
        }
    }

    private fun filterPlaces(places: List<PlaceEntity>, query: SearchQuery): List<PlaceEntity> {
        val text = query.text?.takeIf { it.isNotBlank() }?.lowercase()
        return places.filter { place ->
            (query.categories == null || PlaceCategory.values().firstOrNull { it.name == place.category } in query.categories) &&
                (text == null || place.matchesText(text))
        }
    }

    private fun filterVisits(
        visits: List<VisitEntity>,
        query: SearchQuery,
        placeIds: Set<Long>
    ): List<VisitEntity> {
        val text = query.text?.takeIf { it.isNotBlank() }?.lowercase()
        val startMs = query.startMs
        val endMs = query.endMs
        return visits.filter { v ->
            (startMs == null || v.arrivalAt >= startMs) &&
                (endMs == null || v.arrivalAt <= endMs) &&
                (text == null || placeIds.contains(v.placeId)) &&
                (query.categories == null || placeIds.contains(v.placeId))
        }
    }

    private fun filterSegments(
        segments: List<MovementSegmentEntity>,
        query: SearchQuery,
        placeIds: Set<Long>
    ): List<MovementSegmentEntity> {
        val startMs = query.startMs
        val endMs = query.endMs
        return segments.filter { s ->
            (startMs == null || s.startAt >= startMs) &&
                (endMs == null || s.endAt <= endMs) &&
                (query.segmentTypes == null || s.segmentType in query.segmentTypes) &&
                (query.minDistanceM == null || s.distanceM >= query.minDistanceM) &&
                (query.maxDistanceM == null || s.distanceM <= query.maxDistanceM) &&
                (query.text == null || (s.placeId != null && placeIds.contains(s.placeId)))
        }
    }

    private fun PlaceEntity.matchesText(text: String): Boolean {
        val name = (userDisplayName ?: bestProviderName)?.lowercase() ?: return false
        return text in name
    }

    private fun SearchQuery.isEmpty(): Boolean =
        text.isNullOrBlank() && startMs == null && endMs == null &&
            categories == null && segmentTypes == null &&
            minDistanceM == null && maxDistanceM == null

    private fun SearchQuery.dayKeyRange(): Pair<String?, String?> {
        fun msToDayKey(ms: Long): String {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
            return "%04d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }
        return (startMs?.let(::msToDayKey)) to (endMs?.let(::msToDayKey))
    }

    companion object {
        private val ALL_SEGMENT_TYPES = listOf(
            "WALK", "RUN", "CYCLE", "DRIVE", "FLIGHT", "VISIT", "DWELL", "GAP", "UNKNOWN_MOTION"
        )
    }
}
