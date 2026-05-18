package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DayStory
import com.cosmiclaboratory.voyager.domain.model.DayStoryPlace
import com.cosmiclaboratory.voyager.domain.model.DevicePhoto
import com.cosmiclaboratory.voyager.domain.photo.PhotoLibrary
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.displayName
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Builds the Photo Day Story — the device's photos for one day, correlated to the
 * places the user visited that day.
 *
 * Correlation is purely on-device and computed on demand; nothing is persisted. The
 * primary signal is the capture timestamp falling inside a visit's `[arrival, departure]`
 * window. When a photo carries EXIF coordinates and several visits overlap, the
 * geographically nearest place breaks the tie.
 */
class BuildDayStoryUseCase @Inject constructor(
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val photoLibrary: PhotoLibrary
) {

    /** Whether the photo-library permission is currently granted. */
    fun hasPhotoPermission(): Boolean = photoLibrary.hasPermission()

    /**
     * Correlates the photos taken on [dayKey] (an ISO `yyyy-MM-dd` date) to that day's
     * visits. Returns an empty story when there are no photos or no access.
     */
    suspend fun build(dayKey: String): DayStory {
        val date = LocalDate.parse(dayKey)
        val zone = ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val photos = photoLibrary.photosBetween(startMs, endMs)
        if (photos.isEmpty()) return DayStory.empty(dayKey)

        val visits = visitDao.getByDayKey(dayKey).filter { it.deletedAt == null }
        val places = visits.map { it.placeId }.distinct()
            .mapNotNull { placeDao.getById(it) }
            .associateBy { it.placeId }

        // Bucket every photo into the visit whose window contains it.
        val photosByVisit = LinkedHashMap<Long, MutableList<DevicePhoto>>()
        val unplaced = mutableListOf<DevicePhoto>()
        for (photo in photos) {
            val visit = chooseVisit(photo, visits, endMs)
            if (visit == null) {
                unplaced += photo
            } else {
                photosByVisit.getOrPut(visit.visitId) { mutableListOf() } += photo
            }
        }

        val storyPlaces = visits
            .filter { photosByVisit.containsKey(it.visitId) }
            .sortedBy { it.arrivalAt }
            .map { visit ->
                val place = places[visit.placeId]
                DayStoryPlace(
                    placeId = visit.placeId,
                    displayName = place?.displayName() ?: "Unknown place",
                    emoji = place?.emoji,
                    arrivalAt = visit.arrivalAt,
                    departureAt = visit.departureAt,
                    photos = photosByVisit.getValue(visit.visitId).sortedBy { it.takenAt }
                )
            }

        return DayStory(
            dayKey = dayKey,
            places = storyPlaces,
            unplacedPhotos = unplaced.sortedBy { it.takenAt },
            totalPhotoCount = photos.size
        )
    }

    /**
     * Picks the visit a [photo] belongs to. A photo belongs to a visit when its capture
     * time falls within `[arrivalAt, departureAt]` (an open visit runs to end-of-day).
     * Among multiple overlapping visits the shortest window wins, unless the photo is
     * geotagged — then the nearest place does.
     */
    private fun chooseVisit(
        photo: DevicePhoto,
        visits: List<VisitEntity>,
        dayEndMs: Long
    ): VisitEntity? {
        val containing = visits.filter { visit ->
            val departure = visit.departureAt ?: dayEndMs
            photo.takenAt in visit.arrivalAt..departure
        }
        if (containing.isEmpty()) return null
        if (containing.size == 1 || !photo.hasLocation) {
            return containing.minByOrNull { (it.departureAt ?: dayEndMs) - it.arrivalAt }
        }
        return containing.minByOrNull { visit ->
            val lat = visit.centroidLat
            val lng = visit.centroidLng
            if (lat == null || lng == null) {
                Double.MAX_VALUE
            } else {
                LocationUtils.calculateDistance(photo.lat!!, photo.lng!!, lat, lng)
            }
        }
    }
}
