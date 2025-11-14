package com.cosmiclaboratory.voyager.data.repository

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.FeedbackEvent
import com.cosmiclaboratory.voyager.domain.model.FeedbackEventType
import com.cosmiclaboratory.voyager.domain.repository.FeedbackEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of FeedbackEventRepository.
 * Events are held in memory and logged. A persistent DB-backed
 * implementation can replace this when ML training pipeline is ready.
 */
@Singleton
class FeedbackEventRepositoryImpl @Inject constructor() : FeedbackEventRepository {

    private val mutex = Mutex()
    private val events = mutableListOf<FeedbackEvent>()
    private val eventsFlow = MutableStateFlow<List<FeedbackEvent>>(emptyList())
    private var nextId = 1L

    override suspend fun recordEvent(event: FeedbackEvent) {
        mutex.withLock {
            val stored = event.copy(id = nextId++)
            events.add(stored)
            eventsFlow.value = events.toList()
            Log.d(TAG, "Feedback event: ${stored.eventType} place=${stored.placeId} visit=${stored.visitId}")
        }
    }

    override fun getEventsByType(eventType: FeedbackEventType): Flow<List<FeedbackEvent>> {
        return eventsFlow.map { list -> list.filter { it.eventType == eventType } }
    }

    override suspend fun getRecentEvents(limit: Int): List<FeedbackEvent> = mutex.withLock {
        events.takeLast(limit)
    }

    override suspend fun purgeOldEvents(daysToKeep: Int) = mutex.withLock {
        val cutoff = LocalDateTime.now().minusDays(daysToKeep.toLong())
        events.removeAll { it.timestamp.isBefore(cutoff) }
        eventsFlow.value = events.toList()
    }

    companion object {
        private const val TAG = "FeedbackEventRepository"
    }
}
