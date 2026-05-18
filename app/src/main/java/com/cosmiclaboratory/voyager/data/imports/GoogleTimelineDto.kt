package com.cosmiclaboratory.voyager.data.imports

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for Google Timeline / Location History exports.
 *
 * Two schemas are supported, auto-detected at import time:
 *  - **Legacy Semantic Location History** — Takeout's per-month files, top-level
 *    `timelineObjects` of `activitySegment` / `placeVisit`.
 *  - **New on-device Timeline** — `Timeline.json` exported from the phone since 2024,
 *    top-level `semanticSegments` of `visit` / `activity`.
 *
 * Every field is nullable with a default: Google's exports vary by year and locale,
 * and the parser is lenient + ignores unknown keys so a partial/old file still imports.
 */
@Serializable
internal data class GoogleExportRoot(
    val timelineObjects: List<LegacyTimelineObject>? = null,
    val semanticSegments: List<NewSemanticSegment>? = null
)

// ── Legacy: Semantic Location History ────────────────────────────────────────

@Serializable
internal data class LegacyTimelineObject(
    val activitySegment: LegacyActivitySegment? = null,
    val placeVisit: LegacyPlaceVisit? = null
)

@Serializable
internal data class LegacyActivitySegment(
    val startLocation: LegacyLatLngE7? = null,
    val endLocation: LegacyLatLngE7? = null,
    val duration: LegacyDuration? = null,
    val distance: Long? = null,
    val activityType: String? = null
)

@Serializable
internal data class LegacyPlaceVisit(
    val location: LegacyPlaceLocation? = null,
    val duration: LegacyDuration? = null
)

@Serializable
internal data class LegacyLatLngE7(
    val latitudeE7: Long? = null,
    val longitudeE7: Long? = null
)

@Serializable
internal data class LegacyPlaceLocation(
    val latitudeE7: Long? = null,
    val longitudeE7: Long? = null,
    val placeId: String? = null,
    val name: String? = null,
    val address: String? = null,
    val semanticType: String? = null
)

@Serializable
internal data class LegacyDuration(
    val startTimestamp: String? = null,
    val endTimestamp: String? = null
)

// ── New: on-device Timeline.json ─────────────────────────────────────────────

@Serializable
internal data class NewSemanticSegment(
    val startTime: String? = null,
    val endTime: String? = null,
    val visit: NewVisit? = null,
    val activity: NewActivity? = null
)

@Serializable
internal data class NewVisit(
    val topCandidate: NewVisitCandidate? = null
)

@Serializable
internal data class NewVisitCandidate(
    val placeId: String? = null,
    val semanticType: String? = null,
    val placeLocation: NewLatLng? = null
)

@Serializable
internal data class NewActivity(
    val start: NewLatLng? = null,
    val end: NewLatLng? = null,
    val distanceMeters: Double? = null,
    val topCandidate: NewActivityCandidate? = null
)

@Serializable
internal data class NewActivityCandidate(
    val type: String? = null
)

/** A `latLng` string such as `"12.3456789°, 98.7654321°"`. */
@Serializable
internal data class NewLatLng(
    val latLng: String? = null
)
