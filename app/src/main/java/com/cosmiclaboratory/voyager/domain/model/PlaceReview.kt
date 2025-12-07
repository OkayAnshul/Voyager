package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Represents a place detection that needs user review/confirmation
 * Part of Week 2: User-centric place detection system
 */
data class PlaceReview(
    val id: Long = 0L,
    val placeId: Long,  // Reference to the detected place
    val detectedName: String,  // Name suggested by system
    val detectedCategory: PlaceCategory,  // Category suggested by system
    val confidence: Float,  // Detection confidence (0.0 - 1.0)
    val latitude: Double,
    val longitude: Double,
    val detectionTime: LocalDateTime,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val priority: ReviewPriority = ReviewPriority.NORMAL,
    val reviewType: ReviewType,

    // OSM data if available
    val osmSuggestedName: String? = null,
    val osmSuggestedCategory: PlaceCategory? = null,
    val osmPlaceType: String? = null,

    // User's decision (filled after review)
    val userApprovedName: String? = null,
    val userApprovedCategory: PlaceCategory? = null,
    val reviewedAt: LocalDateTime? = null,

    // Metadata
    val locationCount: Int = 0,  // Number of locations used for detection
    val visitCount: Int = 0,  // Number of visits when detected
    val notes: String? = null,

    // Phase 1 UX: Confidence transparency - breakdown of factors
    val confidenceBreakdown: Map<String, Float>? = null
)

/**
 * Status of a place review
 */
enum class ReviewStatus {
    PENDING,        // Awaiting user review
    APPROVED,       // User approved as-is
    MODIFIED,       // User approved with changes
    REJECTED,       // User rejected the detection
    AUTO_ACCEPTED,  // Automatically accepted based on settings
    EXPIRED         // Review expired (optional cleanup)
}

/**
 * Priority for review prompts
 */
enum class ReviewPriority {
    HIGH,      // Multiple visits, high confidence - prompt immediately
    NORMAL,    // Standard detection - prompt in batch
    LOW,       // Uncertain detection - prompt when convenient
    BATCH_ONLY // Only show in review screen, never prompt
}

/**
 * Type of review based on what triggered it
 */
enum class ReviewType {
    NEW_PLACE,           // Newly detected place
    CATEGORY_UNCERTAIN,  // Place detected but category uncertain
    LOW_CONFIDENCE,      // Low confidence detection
    CONFLICTING_DATA,    // OSM vs heuristic mismatch
    MULTIPLE_VISITS,     // Multiple visits - time to confirm
    USER_REQUESTED       // User manually requested review
}
