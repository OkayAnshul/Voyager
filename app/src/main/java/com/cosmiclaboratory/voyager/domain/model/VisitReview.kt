package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Represents a visit that needs user review/confirmation
 * Used for uncertain visits or overlapping place detections
 */
data class VisitReview(
    val id: Long = 0L,
    val visitId: Long,  // Reference to the visit
    val placeId: Long,  // Place where visit occurred
    val placeName: String,
    val entryTime: LocalDateTime,
    val exitTime: LocalDateTime?,
    val duration: Long,  // in milliseconds
    val confidence: Float,  // Visit confidence (0.0 - 1.0)
    val reviewReason: VisitReviewReason,
    val status: ReviewStatus = ReviewStatus.PENDING,

    // Alternative place suggestions
    val alternativePlaceId: Long? = null,
    val alternativePlaceName: String? = null,

    // User's decision
    val userConfirmedPlaceId: Long? = null,
    val reviewedAt: LocalDateTime? = null,
    val notes: String? = null
)

/**
 * Reason why a visit needs review
 */
enum class VisitReviewReason {
    SHORT_DURATION,       // Visit too short to be confident
    OVERLAPPING_PLACES,   // Multiple places within proximity
    JUST_PASSING_BY,      // Suspected false positive
    ENTRY_EXIT_MISMATCH,  // Entry/exit location mismatch
    LOW_GPS_ACCURACY,     // Poor GPS accuracy during visit
    MANUAL_REVIEW         // User requested review
}
