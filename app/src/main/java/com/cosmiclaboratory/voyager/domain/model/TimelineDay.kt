package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory

data class TimelineDay(
    val dayKey: String,
    val segments: List<TimelineSegment>,
    val totalDistanceM: Double,
    val totalSteps: Int,
    val firstActivityAt: Long?,
    val lastActivityAt: Long?
)

data class TimelineSegment(
    val segmentId: Long,
    val type: SegmentType,
    val startAt: Long,
    val endAt: Long,
    val durationMs: Long,
    val distanceM: Double,
    val confidence: Float,
    val evidence: EvidenceBlock?,
    val place: TimelinePlace?,
    val route: TimelineRoute?,
    val gapReason: String?,
    val isUserCorrected: Boolean,
    /** 1-indexed visit sequence number for the day, null for non-visit segments. */
    val sequenceNumber: Int? = null,
    /** Non-null when this is a unified travel segment combining different transport modes. */
    val subSegments: List<TimelineSegment>? = null
) {
    /** True when this segment was created by unifying multiple transport modes. */
    val isUnifiedTravel: Boolean get() = subSegments != null && subSegments.size > 1
}

data class TimelinePlace(
    val placeId: Long,
    val displayName: String,
    val nameSource: String, // "Custom name", "via Photon", "Inferred", "Coordinates"
    val category: PlaceCategory,
    val confidence: Float,
    val lat: Double,
    val lng: Double,
    val geocodeHints: List<GeocodeHint> = emptyList(),
    val emoji: String? = null,
    val visitCount: Int = 0
)

/**
 * An alternative geocode candidate shown as a hint in the timeline.
 * Helps users identify places when the primary name is ambiguous.
 */
data class GeocodeHint(
    val name: String,
    val provider: String // e.g., "Android Geocoder", "Photon", "Nominatim"
)

data class TimelineRoute(
    val routeId: Long,
    val encodedPolyline: String,
    val simplifiedPolyline: String?,
    val totalDistanceM: Double,
    val avgSpeedMps: Float,
    val transportMode: String,
    val boundingBoxJson: String?
)
