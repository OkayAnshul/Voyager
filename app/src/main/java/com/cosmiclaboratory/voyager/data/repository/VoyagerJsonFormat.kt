package com.cosmiclaboratory.voyager.data.repository

import kotlinx.serialization.Serializable

/**
 * On-disk wire format for VoyagerJSON export/import.
 *
 * The schema is intentionally explicit (no entity reuse) so the file format can
 * outlive internal refactors. Bump [version] whenever a breaking change is made
 * and update the import path to handle older versions.
 *
 * Reference IDs (placeId, segmentId, routeId, visitId) are NOT preserved across
 * an export/import round trip — the importer rewires references by mapping old
 * IDs to newly inserted row IDs.
 */
@Serializable
internal data class VoyagerJsonExport(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val coordsStripped: Boolean = false,
    val places: List<PlaceWire>,
    val segments: List<SegmentWire>,
    val visits: List<VisitWire>,
    /**
     * Raw location samples — present only when the user opted into
     * `exportIncludeRawSamples`. Export-only: the importer restores the derived
     * graph (places/segments/visits) but not raw samples, which are large and
     * tied to tracking-session foreign keys that do not survive a round trip.
     * Defaults to empty so v1 files (which lack the field) still parse.
     */
    val rawSamples: List<RawSampleWire> = emptyList()
) {
    companion object {
        /** v2 added [rawSamples]. The field is additive — v1 files import unchanged. */
        const val CURRENT_VERSION = 2
    }
}

@Serializable
internal data class RawSampleWire(
    val capturedAt: Long,
    val receivedAt: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val verticalAccuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val altitudeM: Double? = null,
    val provider: String,
    val isMock: Boolean = false,
    val batteryPct: Int? = null,
    val isCharging: Boolean = false,
    val deviceIdleMode: Boolean = false,
    val permissionSnapshot: String,
    val localTimeZone: String,
    val geohash: String
)

@Serializable
internal data class PlaceWire(
    val placeId: Long,
    val centroidLat: Double,
    val centroidLng: Double,
    val radiusM: Float,
    val geohash: String,
    val s2CellId: Long? = null,
    val confidence: Float,
    val lifecycleStatus: String,
    val userDisplayName: String? = null,
    val bestProviderName: String? = null,
    val bestProviderSource: String? = null,
    val category: String,
    val categoryConfidence: Float,
    val userCategory: String? = null,
    val createdAt: Long,
    val lastVisitedAt: Long? = null
)

@Serializable
internal data class SegmentWire(
    val segmentId: Long,
    val segmentType: String,
    val startAt: Long,
    val endAt: Long,
    val distanceM: Double,
    val confidence: Float,
    val placeId: Long? = null,
    val gapReason: String? = null,
    val dayKey: String,
    val isUserCorrected: Boolean,
    val route: RouteWire? = null
)

@Serializable
internal data class RouteWire(
    val encodedPolyline: String,
    val simplifiedPolyline: String? = null,
    val totalDistanceM: Double,
    val totalDurationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val transportMode: String,
    val sampleCount: Int,
    val boundingBoxJson: String? = null
)

@Serializable
internal data class VisitWire(
    val visitId: Long,
    val placeId: Long,
    val arrivalAt: Long,
    val departureAt: Long? = null,
    val dwellMs: Long? = null,
    val source: String,
    val confidence: Float,
    val isUserCorrected: Boolean,
    val dayKey: String,
    val centroidLat: Double? = null,
    val centroidLng: Double? = null
)
