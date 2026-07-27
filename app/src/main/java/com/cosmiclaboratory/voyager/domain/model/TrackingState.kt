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
    val lastDepartedVisitId: Long? = null,
    /**
     * True when the user manually paused an open session: the foreground service is
     * torn down but the session stays open so Resume can restart capture. Held in
     * memory by [com.cosmiclaboratory.voyager.storage.TimelineStateStore] (not persisted),
     * so a pause does not survive process death.
     */
    val paused: Boolean = false
) {
    /** Actively capturing right now — an open session that is NOT paused. */
    val isTracking: Boolean get() = activeSessionId != null && !paused

    /** Session open but manually paused (capture stopped, resumable). */
    val isPaused: Boolean get() = activeSessionId != null && paused
}

@Serializable
data class PendingVisitCandidate(
    val centroidLat: Double,
    val centroidLng: Double,
    val accumulationStartAt: Long,
    val sampleCount: Int,
    val maxDistanceFromCentroidM: Double,
    val matchedPlaceId: Long?,
    /** Timestamp of the first sample after the candidate's centroid converged
     *  inside half its expected radius — a better proxy for the actual arrival
     *  than [accumulationStartAt], which may be a passing sample en route to
     *  the place. Null until convergence; falls back to [accumulationStartAt]. */
    val firstStableSampleAt: Long? = null,
    /** Timestamp of the most recent sample that was inside the candidate radius.
     *  Used as the departure time so dwell isn't inflated by the exit-hysteresis
     *  samples spent walking away (T10). Null until the first inside sample. */
    val lastInsideSampleAt: Long? = null
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
