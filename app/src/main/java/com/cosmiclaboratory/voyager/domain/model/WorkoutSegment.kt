package com.cosmiclaboratory.voyager.domain.model

/** A saved favourite sub-route to race yourself on — the private analogue to a Strava segment. */
data class WorkoutSegment(
    val id: Long,
    val name: String,
    val encodedPolyline: String,
    val distanceMeters: Double,
    val createdAt: Long,
)

/** One traversal of a [WorkoutSegment] found within a recorded activity. */
data class SegmentEffort(
    val activityId: Long,
    val startedAt: Long,
    val timeMs: Long,
)

/** A segment with every effort found across the user's activities, fastest first. */
data class SegmentWithEfforts(
    val segment: WorkoutSegment,
    val efforts: List<SegmentEffort>,
) {
    val bestMs: Long? get() = efforts.minByOrNull { it.timeMs }?.timeMs
    val count: Int get() = efforts.size
}
