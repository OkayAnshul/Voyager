package com.cosmiclaboratory.voyager.domain.model

import kotlinx.serialization.Serializable

data class TrackingRuntimeState(
    val activeSessionId: Long?,
    val currentSegmentId: Long?,
    val pendingVisitCandidate: PendingVisitCandidate?,
    val lastConfirmedVisitId: Long? = null,
    val lastAcceptedSampleId: Long?,
    val lastAcceptedAt: Long?,
    val stateVersion: Long,
    val lastPipelineLatencyMs: Long?,
    val lastMotionState: String? = null,
    val lastDepartedCentroidLat: Double? = null,
    val lastDepartedCentroidLng: Double? = null,
    val lastDepartureTime: Long? = null,
    val lastDepartedVisitId: Long? = null
) {
    val isTracking: Boolean get() = activeSessionId != null
}

@Serializable
data class PendingVisitCandidate(
    val centroidLat: Double,
    val centroidLng: Double,
    val accumulationStartAt: Long,
    val sampleCount: Int,
    val maxDistanceFromCentroidM: Double,
    val matchedPlaceId: Long?
)

data class TrackingHealth(
    val isServiceRunning: Boolean,
    val lastSampleAt: Long?,
    val permissionState: String,
    val batteryPct: Int?,
    val isCharging: Boolean,
    val workerHeartbeats: Map<String, Long>
)

data class ActiveVisitInfo(
    val visitId: Long,
    val placeName: String,
    val category: PlaceCategory,
    val arrivalAt: Long,
    val centroidLat: Double,
    val centroidLng: Double
)

/** Transient snapshot of the segment being accumulated in memory by the Segmenter. */
data class InProgressSegmentSnapshot(
    val segmentType: String,
    val startAt: Long,
    val endAt: Long,
    val distanceM: Double,
    val sampleCount: Int
)

data class LiveTimelineState(
    val currentDay: TimelineDay?,
    val inProgressSegment: TimelineSegment?,
    val isTracking: Boolean,
    val activeVisit: ActiveVisitInfo? = null,
    val pendingCandidate: PendingVisitCandidate? = null
)
