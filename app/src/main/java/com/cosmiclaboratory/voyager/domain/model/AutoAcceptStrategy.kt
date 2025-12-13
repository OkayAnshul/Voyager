package com.cosmiclaboratory.voyager.domain.model

/**
 * Strategy for automatically accepting place detections
 * Part of the user-centric detection system
 */
enum class AutoAcceptStrategy {
    /**
     * Never auto-accept - always ask user
     * Best for users who want full control
     */
    NEVER,

    /**
     * Auto-accept only high confidence detections (>= threshold)
     * Default and recommended for most users
     */
    HIGH_CONFIDENCE_ONLY,

    /**
     * Auto-accept after N visits to same location
     * Good for users who want system to learn from repeated visits
     */
    AFTER_N_VISITS,

    /**
     * Auto-accept everything, user can review/correct later
     * For power users who prefer batch review
     */
    ALWAYS
}

/**
 * When to prompt user for reviews
 */
enum class ReviewPromptMode {
    /**
     * Prompt immediately when uncertain detection occurs
     * Most intrusive but most accurate
     */
    IMMEDIATE,

    /**
     * Show notification but don't interrupt
     * User can review when convenient
     */
    NOTIFICATION_ONLY,

    /**
     * Batch review at end of day (e.g., 7 PM)
     * Least intrusive
     */
    DAILY_SUMMARY,

    /**
     * Never prompt - user must manually open review screen
     * For users who don't want any interruptions
     */
    MANUAL_ONLY
}

/**
 * Configuration for auto-accept behavior
 */
data class AutoAcceptConfig(
    val strategy: AutoAcceptStrategy = AutoAcceptStrategy.HIGH_CONFIDENCE_ONLY,
    val confidenceThreshold: Float = 0.75f,  // For HIGH_CONFIDENCE_ONLY
    val requiredVisits: Int = 3,  // For AFTER_N_VISITS
    val reviewPromptMode: ReviewPromptMode = ReviewPromptMode.NOTIFICATION_ONLY,
    val disabledCategories: Set<PlaceCategory> = emptySet(),  // Never detect these categories
    val alwaysReviewCategories: Set<PlaceCategory> = emptySet()  // Always review these, even if high confidence
)
