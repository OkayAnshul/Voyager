package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.TripDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.TripEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Detects multi-day trips away from home from the visit history.
 *
 * A "trip" is a maximal run of visit-days on which the user never went home, bounded by
 * days they did. Days with no data inside a run are absorbed (a phone-off day mid-trip
 * shouldn't split it). A run becomes a trip only if it spans ≥2 calendar days.
 *
 * Detection needs a Home place as the anchor — until one exists [detect] returns empty
 * and the Trips screen shows an explanatory state. Trips are pure derived data with no
 * user-authored fields, so each run rebuilds the whole table via [TripDao.replaceAll].
 */
class DetectTripsUseCase @Inject constructor(
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val tripDao: TripDao
) {

    companion object {
        /** Minimum start→end day difference for a run to count as a multi-day trip. */
        const val MIN_TRIP_SPAN_DAYS = 1L
    }

    /** True when trip detection can run — it needs a Home place as the anchor. */
    suspend fun hasHomeAnchor(): Boolean = placeDao.getHomePlace() != null

    /** Recomputes every trip from visit history and atomically replaces the trips table. */
    suspend fun detectAndStore() {
        tripDao.replaceAll(detect())
    }

    /** A day's visits, grouped during the run scan. */
    private data class AwayDay(val dayKey: String, val visits: List<VisitEntity>)

    suspend fun detect(): List<TripEntity> {
        val home = placeDao.getHomePlace() ?: return emptyList()
        val dayKeys = visitDao.getAllDayKeys()
        if (dayKeys.isEmpty()) return emptyList()

        // Walk days oldest→newest; an away-run breaks only on a day the user went home.
        val runs = mutableListOf<List<AwayDay>>()
        var current = mutableListOf<AwayDay>()
        for (dayKey in dayKeys) {
            val visits = visitDao.getByDayKey(dayKey).filter { it.deletedAt == null }
            if (visits.isEmpty()) continue
            val wentHome = visits.any { it.placeId == home.placeId }
            if (wentHome) {
                if (current.isNotEmpty()) {
                    runs.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(AwayDay(dayKey, visits))
            }
        }
        if (current.isNotEmpty()) runs.add(current)

        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        return runs.mapNotNull { run -> buildTrip(run, today, now) }
    }

    private suspend fun buildTrip(
        run: List<AwayDay>,
        today: LocalDate,
        now: Long
    ): TripEntity? {
        val startDayKey = run.first().dayKey
        val endDayKey = run.last().dayKey
        val start = LocalDate.parse(startDayKey)
        val end = LocalDate.parse(endDayKey)
        if (ChronoUnit.DAYS.between(start, end) < MIN_TRIP_SPAN_DAYS) return null

        val allVisits = run.flatMap { it.visits }
        val distinctPlaceIds = allVisits.map { it.placeId }.filter { it != 0L }.distinct()

        return TripEntity(
            startDayKey = startDayKey,
            endDayKey = endDayKey,
            title = buildTitle(allVisits, start, end),
            placeCount = distinctPlaceIds.size,
            visitCount = allVisits.size,
            distanceMeters = sumDistance(start, end),
            // Still "ongoing" if the away-run reaches today or yesterday.
            isOngoing = !end.isBefore(today.minusDays(1)),
            detectedAt = now
        )
    }

    /** Names the trip after the place where the most time was spent, if it has a name. */
    private suspend fun buildTitle(
        visits: List<VisitEntity>,
        start: LocalDate,
        end: LocalDate
    ): String {
        val dwellByPlace = visits
            .filter { it.placeId != 0L }
            .groupBy { it.placeId }
            .mapValues { (_, vs) -> vs.sumOf { it.dwellMs ?: 0L } }
        val topPlaceId = dwellByPlace.maxByOrNull { it.value }?.key
        val place = topPlaceId?.let { placeDao.getById(it) }
        val name = place?.userDisplayName ?: place?.bestProviderName
        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        return if (!name.isNullOrBlank()) "Trip to $name" else "$days-day trip"
    }

    /** Total movement distance across the inclusive trip day range. */
    private suspend fun sumDistance(start: LocalDate, end: LocalDate): Double {
        var total = 0.0
        var day = start
        while (!day.isAfter(end)) {
            total += movementSegmentDao.getByDayKey(day.toString()).sumOf { it.distanceM }
            day = day.plusDays(1)
        }
        return total
    }
}
