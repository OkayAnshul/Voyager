package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

data class Visit(
    val id: Long = 0L,
    val placeId: Long,
    val entryTime: LocalDateTime,
    val exitTime: LocalDateTime? = null,
    val _duration: Long = 0L, // stored duration - made public for mapper access
    val confidence: Float = 1.0f
) {
    // CRITICAL FIX: Simplified duration calculation prioritizing stored value
    val duration: Long 
        get() = when {
            _duration > 0L -> _duration // Use stored duration if available
            exitTime != null -> java.time.Duration.between(entryTime, exitTime!!).toMillis() // Calculate if completed
            else -> 0L // Active visit, no duration yet
        }
    
    companion object {
        fun calculateDuration(entryTime: LocalDateTime, exitTime: LocalDateTime?): Long {
            return if (exitTime != null) {
                java.time.Duration.between(entryTime, exitTime).toMillis()
            } else {
                0L // Active visit, no duration yet
            }
        }
        
        // CRITICAL FIX: Factory method for creating visits with calculated duration
        fun createWithDuration(
            id: Long = 0L,
            placeId: Long,
            entryTime: LocalDateTime,
            exitTime: LocalDateTime?,
            confidence: Float = 1.0f
        ): Visit {
            val calculatedDuration = if (exitTime != null) {
                calculateDuration(entryTime, exitTime)
            } else {
                0L // Active visit, no duration yet
            }
            return Visit(
                id = id,
                placeId = placeId,
                entryTime = entryTime,
                exitTime = exitTime,
                _duration = calculatedDuration,
                confidence = confidence
            )
        }
    }
    
    // CRITICAL FIX: Helper function to create a completed visit with proper duration
    fun complete(exitTime: LocalDateTime): Visit {
        val calculatedDuration = java.time.Duration.between(entryTime, exitTime).toMillis()
        return Visit(
            id = id,
            placeId = placeId,
            entryTime = entryTime,
            exitTime = exitTime,
            _duration = calculatedDuration,
            confidence = confidence
        )
    }
    
    // Helper function to check if visit is active (not completed)
    val isActive: Boolean get() = exitTime == null
    
    // CRITICAL FIX: Helper function to get current duration for active visits
    fun getCurrentDuration(currentTime: LocalDateTime = java.time.LocalDateTime.now()): Long {
        return when {
            !isActive -> duration // Completed visit, use stored duration
            else -> java.time.Duration.between(entryTime, currentTime).toMillis() // Active visit, calculate current
        }
    }
    
    // CRITICAL FIX: Helper function to update visit with proper duration storage
    fun withStoredDuration(): Visit {
        return if (exitTime != null && _duration == 0L) {
            val calculatedDuration = java.time.Duration.between(entryTime, exitTime!!).toMillis()
            Visit(
                id = id,
                placeId = placeId,
                entryTime = entryTime,
                exitTime = exitTime,
                _duration = calculatedDuration,
                confidence = confidence
            )
        } else {
            this
        }
    }
}

data class VisitSummary(
    val place: Place,
    val totalDuration: Long,
    val visitCount: Int,
    val averageDuration: Long,
    val lastVisit: LocalDateTime?
)