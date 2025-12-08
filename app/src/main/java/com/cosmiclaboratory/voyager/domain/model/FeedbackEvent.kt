package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Records user feedback actions for learning and ML training.
 * Emitted by review use cases when users confirm, rename, or reject detections.
 */
data class FeedbackEvent(
    val id: Long = 0,
    val eventType: FeedbackEventType,
    val placeId: Long? = null,
    val visitId: Long? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

enum class FeedbackEventType {
    NAME_ACCEPTED,
    NAME_REJECTED,
    PLACE_RENAMED,
    CATEGORY_CORRECTED,
    VISIT_CONFIRMED,
    VISIT_DELETED
}
