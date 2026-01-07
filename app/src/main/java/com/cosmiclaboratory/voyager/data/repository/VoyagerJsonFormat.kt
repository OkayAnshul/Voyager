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
    val visits: List<VisitWire>
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

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
