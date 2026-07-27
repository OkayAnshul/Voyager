package com.cosmiclaboratory.voyager.domain.model

/**
 * A passively-detected movement segment (a run/ride/walk Voyager already tracked into the timeline)
 * that looks like an intentional workout — offered as "save this as an activity?". Materialising it
 * bridges the passive-detection and recorded-workout subsystems using data already captured.
 */
data class WorkoutSuggestion(
    val segmentId: Long,
    val type: WorkoutType,
    val distanceMeters: Double,
    val durationMs: Long,
    val startedAt: Long,
    val dayKey: String,
    val encodedPolyline: String,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
)
