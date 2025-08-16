package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.FeedbackEvent
import com.cosmiclaboratory.voyager.domain.model.FeedbackEventType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for recording user feedback events.
 * Events are used for ML training and improving place/visit detection accuracy.
 */
interface FeedbackEventRepository {

    /** Emit a new feedback event */
    suspend fun recordEvent(event: FeedbackEvent)

    /** Get all events of a specific type */
    fun getEventsByType(eventType: FeedbackEventType): Flow<List<FeedbackEvent>>

    /** Get recent events (for diagnostics/ML training pipeline) */
    suspend fun getRecentEvents(limit: Int = 100): List<FeedbackEvent>

    /** Clear events older than N days */
    suspend fun purgeOldEvents(daysToKeep: Int = 90)
}
