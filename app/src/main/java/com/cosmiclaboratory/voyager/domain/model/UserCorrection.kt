package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Records user corrections to learn from and improve future detections
 * Part of the learning system for personalized place detection
 */
data class UserCorrection(
    val id: Long = 0L,
    val placeId: Long,
    val correctionTime: LocalDateTime,
    val correctionType: CorrectionType,

    // What was changed
    val oldValue: String,
    val newValue: String,

    // Context
    val confidence: Float,  // Confidence of original detection
    val locationCount: Int,  // Number of locations when corrected
    val visitCount: Int,  // Number of visits when corrected

    // Learning metadata
    val wasAppliedToLearning: Boolean = false,  // Whether this correction influenced future detections
    val similarCorrectionCount: Int = 0  // How many similar corrections user has made
)

/**
 * Type of correction made by user
 */
enum class CorrectionType {
    NAME_CHANGE,         // User renamed the place
    CATEGORY_CHANGE,     // User recategorized the place
    LOCATION_ADJUSTMENT, // User adjusted place location
    MERGE_PLACES,        // User merged duplicate places
    SPLIT_PLACE,         // User split a place into multiple
    DELETE_PLACE,        // User deleted a false detection
    VISIT_REASSIGNMENT   // User moved visit to different place
}
