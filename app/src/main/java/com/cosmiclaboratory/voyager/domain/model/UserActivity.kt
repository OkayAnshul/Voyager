package com.cosmiclaboratory.voyager.domain.model

/**
 * Represents the user's current activity state
 *
 * Phase 2: Hybrid Activity Recognition
 * Used to prevent false place detections while user is moving
 */
enum class UserActivity {
    /**
     * User is in a vehicle (car, bus, train, etc.)
     * Skip location saves when DRIVING with high confidence
     */
    DRIVING,

    /**
     * User is walking or running
     * Continue normal location tracking
     */
    WALKING,

    /**
     * User is stationary (standing, sitting)
     * Best state for place detection
     */
    STATIONARY,

    /**
     * User is on a bicycle
     * Skip location saves when cycling
     */
    CYCLING,

    /**
     * Unknown activity state
     * Use default behavior
     */
    UNKNOWN;

    companion object {
        /**
         * Activities that should skip location saves
         * to prevent false detections
         */
        val MOVING_ACTIVITIES = setOf(DRIVING, CYCLING)

        /**
         * Activities good for place detection
         */
        val STATIONARY_ACTIVITIES = setOf(STATIONARY, WALKING)
    }
}

/**
 * Activity detection result with confidence
 */
data class ActivityDetection(
    val activity: UserActivity,
    val confidence: Float, // 0.0 - 1.0
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if this detection is reliable enough to act on
     */
    fun isConfident(threshold: Float = 0.75f): Boolean {
        return confidence >= threshold
    }

    /**
     * Check if user is likely moving (not good for place detection)
     */
    fun isMoving(confidenceThreshold: Float = 0.75f): Boolean {
        return activity in UserActivity.MOVING_ACTIVITIES && confidence >= confidenceThreshold
    }

    /**
     * Check if user is likely stationary (good for place detection)
     */
    fun isStationary(confidenceThreshold: Float = 0.75f): Boolean {
        return activity in UserActivity.STATIONARY_ACTIVITIES && confidence >= confidenceThreshold
    }
}
