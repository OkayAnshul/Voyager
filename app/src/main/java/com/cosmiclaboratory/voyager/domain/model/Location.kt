package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Location data point with activity context
 * Phase 1: Activity-First Implementation - Added activity tracking
 */
data class Location(
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val timestamp: LocalDateTime,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,

    // Phase 1: Activity context - what the user was doing at this location
    val userActivity: UserActivity = UserActivity.UNKNOWN,
    val activityConfidence: Float = 0f, // 0.0 - 1.0

    // Phase 1: Semantic context - inferred from activity + time + patterns
    val semanticContext: SemanticContext? = null
)