package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import javax.inject.Inject

/**
 * A surfaced "moment" worth seeing — first visit to a place, longest dwell ever,
 * record distance, etc. Pure read-side; no persistence in this cut. A daily
 * worker can call into this and write to a notable_events table once the UI
 * binds to it.
 */
sealed class NotableEvent {
    abstract val dayKey: String

    data class FirstVisitToPlace(
        val placeId: Long,
        val placeName: String?,
        val visitId: Long,
        override val dayKey: String
    ) : NotableEvent()

    data class LongestDwellAtPlace(
        val placeId: Long,
        val placeName: String?,
        val dwellMs: Long,
        override val dayKey: String
    ) : NotableEvent()

    data class LongestDistanceDay(
        val distanceM: Double,
        override val dayKey: String
    ) : NotableEvent()

    data class FirstActivityOfDay(
        val firstActivityAt: Long,
        override val dayKey: String
    ) : NotableEvent()
}

class DetectNotableEventsUseCase @Inject constructor(
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val dailyRollupDao: DailyRollupDao,
) {

    /**
     * Recent notable events across all data — first visits to places that appeared
     * in the last [lookbackDays] days, record-setting days within that same window.
     * Pure derived data; cheap to recompute on demand.
     */
    suspend fun forRecentDays(lookbackDays: Int = 14): List<NotableEvent> {
        val cutoffMs = System.currentTimeMillis() - lookbackDays.toLong() * 86_400_000L
        val recentDayKeys = dailyRollupDao.getRecent(lookbackDays.coerceAtMost(60))
            .map { it.dayKey }
            .toSet()

        val events = mutableListOf<NotableEvent>()
        events += firstVisitsToPlaces(cutoffMs)
        events += longestDistanceDay(recentDayKeys)
        events += longestDwellHighlights(cutoffMs)
        return events
    }

    private suspend fun firstVisitsToPlaces(cutoffMs: Long): List<NotableEvent> {
        val notable = mutableListOf<NotableEvent>()
        val places = placeDao.getAllActive()
        for (place in places) {
            val visits = visitDao.getByPlaceId(place.placeId)
                .sortedBy { it.arrivalAt }
            val firstVisit = visits.firstOrNull() ?: continue
            // Only counts if the first-ever visit happened recently.
            if (firstVisit.arrivalAt < cutoffMs) continue
            notable += NotableEvent.FirstVisitToPlace(
                placeId = place.placeId,
                placeName = effectiveName(place.userDisplayName, place.bestProviderName),
                visitId = firstVisit.visitId,
                dayKey = firstVisit.dayKey
            )
        }
        return notable
    }

    private suspend fun longestDistanceDay(recentDayKeys: Set<String>): List<NotableEvent> {
        if (recentDayKeys.isEmpty()) return emptyList()
        val all = dailyRollupDao.getRecent(365)
        val historicalMax = all
            .filter { it.dayKey !in recentDayKeys }
            .maxOfOrNull { it.totalDistanceM } ?: 0.0
        val recentBest = all.filter { it.dayKey in recentDayKeys }
            .maxByOrNull { it.totalDistanceM }
            ?: return emptyList()
        if (recentBest.totalDistanceM <= historicalMax) return emptyList()
        return listOf(
            NotableEvent.LongestDistanceDay(
                distanceM = recentBest.totalDistanceM,
                dayKey = recentBest.dayKey
            )
        )
    }

    private suspend fun longestDwellHighlights(cutoffMs: Long): List<NotableEvent> {
        val places = placeDao.getAllActive()
        val notable = mutableListOf<NotableEvent>()
        for (place in places) {
            val visits = visitDao.getByPlaceId(place.placeId)
                .filter { (it.dwellMs ?: 0L) > 0L }
            if (visits.size < 3) continue // need history to call out a record
            val sortedByDwell = visits.sortedByDescending { it.dwellMs!! }
            val record = sortedByDwell.first()
            // Only call out the record if it landed in the lookback window.
            if (record.arrivalAt < cutoffMs) continue
            val prior = sortedByDwell.drop(1).firstOrNull()
            // Must be at least 1h longer than the previous record so we don't
            // flag a 1-minute new best as worth surfacing.
            if (prior != null && (record.dwellMs!! - prior.dwellMs!!) < 3_600_000L) continue
            notable += NotableEvent.LongestDwellAtPlace(
                placeId = place.placeId,
                placeName = effectiveName(place.userDisplayName, place.bestProviderName),
                dwellMs = record.dwellMs!!,
                dayKey = record.dayKey
            )
        }
        return notable
    }

    private fun effectiveName(userName: String?, providerName: String?): String? =
        userName?.takeIf { it.isNotBlank() } ?: providerName?.takeIf { it.isNotBlank() }

    /** Pure compute — exposed for tests that pass in synthetic histories. */
    fun computeFirstVisits(
        places: List<PlaceForNotable>,
        visitsByPlace: Map<Long, List<VisitEntity>>,
        cutoffMs: Long
    ): List<NotableEvent.FirstVisitToPlace> {
        val notable = mutableListOf<NotableEvent.FirstVisitToPlace>()
        for (place in places) {
            val visits = visitsByPlace[place.placeId].orEmpty().sortedBy { it.arrivalAt }
            val first = visits.firstOrNull() ?: continue
            if (first.arrivalAt < cutoffMs) continue
            notable += NotableEvent.FirstVisitToPlace(
                placeId = place.placeId,
                placeName = place.name,
                visitId = first.visitId,
                dayKey = first.dayKey
            )
        }
        return notable
    }

    /** Test seam — a minimal projection that doesn't depend on PlaceEntity construction. */
    data class PlaceForNotable(val placeId: Long, val name: String?)

    /** Pure compute — exposed for tests. */
    fun computeLongestDistanceDay(rollups: List<DailyRollupEntity>, recentDayKeys: Set<String>): NotableEvent.LongestDistanceDay? {
        val historicalMax = rollups
            .filter { it.dayKey !in recentDayKeys }
            .maxOfOrNull { it.totalDistanceM } ?: 0.0
        val recentBest = rollups.filter { it.dayKey in recentDayKeys }
            .maxByOrNull { it.totalDistanceM } ?: return null
        if (recentBest.totalDistanceM <= historicalMax) return null
        return NotableEvent.LongestDistanceDay(
            distanceM = recentBest.totalDistanceM,
            dayKey = recentBest.dayKey
        )
    }
}
