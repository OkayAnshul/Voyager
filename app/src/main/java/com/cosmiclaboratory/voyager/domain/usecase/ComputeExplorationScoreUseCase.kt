package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** How wide-ranging the period's travel was. */
data class ExplorationScore(
    val uniquePlaces: Int,
    /** Places visited this period that had never been visited before. */
    val newPlaces: Int,
    /** 0..100 blend — variety plus a bonus for genuinely new discoveries. */
    val score: Int
)

/**
 * A first-class "exploration" measure for the Movement lens: how many distinct
 * places the period touched, and how many were first-ever visits. New places are
 * weighted more heavily — discovery is what "exploration" means. Reuses the
 * first-visit idea from [DetectNotableEventsUseCase] (a place is new when its
 * earliest-ever visit falls inside the period).
 */
class ComputeExplorationScoreUseCase @Inject constructor(
    private val visitDao: VisitDao
) {
    suspend fun compute(range: DateRange, zone: ZoneId = ZoneId.systemDefault()): ExplorationScore {
        val start = LocalDate.parse(range.startDay)
        val end = LocalDate.parse(range.endDay)
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()

        val placeIds = mutableSetOf<Long>()
        var day = start
        while (!day.isAfter(end)) {
            visitDao.getByDayKey(day.toString())
                .filter { it.placeId != 0L }
                .forEach { placeIds.add(it.placeId) }
            day = day.plusDays(1)
        }

        var newPlaces = 0
        for (placeId in placeIds) {
            val firstVisitMs = visitDao.getByPlaceId(placeId).minOfOrNull { it.arrivalAt } ?: continue
            if (firstVisitMs >= startMs) newPlaces++
        }

        return ExplorationScore(
            uniquePlaces = placeIds.size,
            newPlaces = newPlaces,
            score = score(placeIds.size, newPlaces)
        )
    }

    /** Pure scoring blend. Testable. */
    fun score(uniquePlaces: Int, newPlaces: Int): Int =
        (uniquePlaces * 6 + newPlaces * 12).coerceIn(0, 100)
}
