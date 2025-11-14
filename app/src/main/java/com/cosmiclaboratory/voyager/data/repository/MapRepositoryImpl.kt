package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.LatLngBounds
import com.cosmiclaboratory.voyager.domain.model.MapRoute
import com.cosmiclaboratory.voyager.domain.model.VisitMarker
import com.cosmiclaboratory.voyager.domain.repository.MapRepository
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.storage.database.entity.displayName
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapRepositoryImpl @Inject constructor(
    private val routeDao: RouteDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao
) : MapRepository {

    override suspend fun getRouteForSegment(segmentId: Long): MapRoute? {
        val route = routeDao.getBySegmentId(segmentId) ?: return null
        val points = PolylineEncoder.decode(route.simplifiedPolyline ?: route.encodedPolyline)
        return MapRoute(
            routeId = route.routeId,
            segmentId = segmentId,
            polylinePoints = points,
            color = getTransportColor(route.transportMode),
            transportMode = route.transportMode
        )
    }

    override suspend fun getVisitMarkers(dayKey: String): List<VisitMarker> {
        val visits = visitDao.getByDayKey(dayKey)
        return visits.mapIndexedNotNull { index, visit ->
            val place = if (visit.placeId != 0L) placeDao.getById(visit.placeId) else null
            // Fall back to visit centroid when place is not yet matched (placeId=0)
            val lat = place?.centroidLat ?: visit.centroidLat ?: return@mapIndexedNotNull null
            val lng = place?.centroidLng ?: visit.centroidLng ?: return@mapIndexedNotNull null
            VisitMarker(
                visitId = visit.visitId,
                placeId = visit.placeId,
                lat = lat,
                lng = lng,
                displayName = place?.displayName() ?: "%.4f, %.4f".format(lat, lng),
                arrivalAt = visit.arrivalAt,
                departureAt = visit.departureAt,
                sequenceNumber = index + 1
            )
        }
    }

    override suspend fun getVisitMarkerForSegment(segmentId: Long): VisitMarker? {
        val segment = movementSegmentDao.getById(segmentId) ?: return null
        val placeId = segment.placeId ?: return null
        val place = placeDao.getById(placeId) ?: return null
        // Find the corresponding visit to get arrival/departure times
        val visit = visitDao.getVisitOverlappingWindow(segment.startAt, segment.endAt)
        return VisitMarker(
            visitId = visit?.visitId ?: 0,
            placeId = placeId,
            lat = place.centroidLat,
            lng = place.centroidLng,
            displayName = place.displayName(),
            arrivalAt = visit?.arrivalAt ?: segment.startAt,
            departureAt = visit?.departureAt ?: segment.endAt,
            sequenceNumber = 0
        )
    }

    override suspend fun getDayBoundingBox(dayKey: String): LatLngBounds? {
        val segments = movementSegmentDao.getByDayKey(dayKey)
        if (segments.isEmpty()) return null

        // Get all route points for the day
        val allPoints = mutableListOf<Pair<Double, Double>>()
        for (segment in segments) {
            segment.routeId?.let { routeId ->
                routeDao.getById(routeId)?.let { route ->
                    allPoints.addAll(PolylineEncoder.decode(route.encodedPolyline))
                }
            }
            segment.placeId?.let { placeId ->
                placeDao.getById(placeId)?.let { place ->
                    allPoints.add(Pair(place.centroidLat, place.centroidLng))
                }
            }
        }

        // Also include visit centroids so unmatched visits contribute to bounds
        val visits = visitDao.getByDayKey(dayKey)
        for (visit in visits) {
            val lat = visit.centroidLat
            val lng = visit.centroidLng
            if (lat != null && lng != null) {
                allPoints.add(Pair(lat, lng))
            }
        }

        if (allPoints.isEmpty()) return null

        return LatLngBounds(
            northEastLat = allPoints.maxOf { it.first },
            northEastLng = allPoints.maxOf { it.second },
            southWestLat = allPoints.minOf { it.first },
            southWestLng = allPoints.minOf { it.second }
        )
    }

    private fun getTransportColor(transportMode: String): Int = when (transportMode) {
        "WALK" -> 0xFF4CAF50.toInt()
        "RUN" -> 0xFFFF9800.toInt()
        "CYCLE" -> 0xFF2196F3.toInt()
        "DRIVE" -> 0xFF9C27B0.toInt()
        "TRANSIT" -> 0xFFFF5722.toInt()
        else -> 0xFF757575.toInt()
    }
}
