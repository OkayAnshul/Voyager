package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Trip
import com.cosmiclaboratory.voyager.domain.model.TripDay
import com.cosmiclaboratory.voyager.domain.model.TripDetail
import com.cosmiclaboratory.voyager.domain.model.TripPlaceVisit
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.TripDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TripEntity
import com.cosmiclaboratory.voyager.storage.database.entity.displayName
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds the day-by-day [TripDetail] for one trip, re-deriving it from visits each time
 * the detail screen opens (the `trips` table only stores the summary).
 */
class BuildTripDetailUseCase @Inject constructor(
    private val tripDao: TripDao,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val movementSegmentDao: MovementSegmentDao
) {

    suspend fun build(tripId: Long): TripDetail? {
        val entity = tripDao.getById(tripId) ?: return null

        val placeCache = mutableMapOf<Long, PlaceEntity?>()
        suspend fun place(id: Long): PlaceEntity? =
            placeCache.getOrPut(id) { placeDao.getById(id) }

        val days = mutableListOf<TripDay>()
        var day = LocalDate.parse(entity.startDayKey)
        val end = LocalDate.parse(entity.endDayKey)
        while (!day.isAfter(end)) {
            val dayKey = day.toString()
            val visits = visitDao.getByDayKey(dayKey)
                .filter { it.deletedAt == null }
                .sortedBy { it.arrivalAt }
            if (visits.isNotEmpty()) {
                val places = visits.map { visit ->
                    val p = place(visit.placeId)
                    TripPlaceVisit(
                        placeId = visit.placeId,
                        displayName = p?.displayName() ?: "Unknown place",
                        emoji = p?.emoji,
                        arrivalAt = visit.arrivalAt,
                        departureAt = visit.departureAt,
                        dwellMs = visit.dwellMs ?: 0L
                    )
                }
                val distance = movementSegmentDao.getByDayKey(dayKey).sumOf { it.distanceM }
                days.add(TripDay(dayKey, places, distance))
            }
            day = day.plusDays(1)
        }

        return TripDetail(trip = entity.toDomain(), days = days)
    }
}

/** Maps the persisted summary row to its domain model. */
fun TripEntity.toDomain(): Trip = Trip(
    id = tripId,
    startDayKey = startDayKey,
    endDayKey = endDayKey,
    title = title,
    placeCount = placeCount,
    visitCount = visitCount,
    distanceMeters = distanceMeters,
    isOngoing = isOngoing,
    detectedAt = detectedAt,
    userTitle = userTitle,
    notes = notes,
    coverPhotoUri = coverPhotoUri
)
