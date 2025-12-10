package com.cosmiclaboratory.voyager.domain.model

data class MapRoute(
    val routeId: Long,
    val segmentId: Long = 0L,
    val polylinePoints: List<Pair<Double, Double>>,
    val color: Int,
    val transportMode: String
)

data class VisitMarker(
    val visitId: Long,
    val placeId: Long,
    val lat: Double,
    val lng: Double,
    val displayName: String,
    val arrivalAt: Long,
    val departureAt: Long?,
    val sequenceNumber: Int
)

data class LatLngBounds(
    val northEastLat: Double,
    val northEastLng: Double,
    val southWestLat: Double,
    val southWestLng: Double
)
