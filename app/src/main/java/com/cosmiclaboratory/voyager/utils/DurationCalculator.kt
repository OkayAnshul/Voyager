package com.cosmiclaboratory.voyager.utils

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.Visit
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Duration Calculator
 * CRITICAL: Single source of truth for ALL duration calculations in the app
 * Eliminates inconsistencies between different components
 */
@Singleton
class DurationCalculator @Inject constructor() {
    
    companion object {
        private const val TAG = "DurationCalculator"
        
        // Duration calculation constants
        const val MILLISECONDS_PER_SECOND = 1000L
        const val MILLISECONDS_PER_MINUTE = 60L * MILLISECONDS_PER_SECOND
        const val MILLISECONDS_PER_HOUR = 60L * MILLISECONDS_PER_MINUTE
        const val MILLISECONDS_PER_DAY = 24L * MILLISECONDS_PER_HOUR
        
        // Minimum viable duration (to filter out GPS noise)
        const val MIN_VALID_DURATION_MS = 30L * MILLISECONDS_PER_SECOND // 30 seconds
    }
    
    /**
     * Calculate visit duration with unified logic
     * CRITICAL: This is the ONLY method that should be used for visit duration calculation
     */
    fun calculateVisitDuration(
        entryTime: LocalDateTime?,
        exitTime: LocalDateTime? = null,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): VisitDurationResult {
        
        if (entryTime == null) {
            Log.w(TAG, "Cannot calculate duration: entry time is null")
            return VisitDurationResult(
                durationMs = 0L,
                isComplete = false,
                isValid = false,
                errorMessage = "Entry time is null"
            )
        }
        
        val endTime = exitTime ?: currentTime
        
        // Validate time ordering
        if (endTime.isBefore(entryTime)) {
            Log.w(TAG, "Invalid time range: end time ($endTime) is before entry time ($entryTime)")
            return VisitDurationResult(
                durationMs = 0L,
                isComplete = exitTime != null,
                isValid = false,
                errorMessage = "End time is before entry time"
            )
        }
        
        val duration = Duration.between(entryTime, endTime)
        val durationMs = duration.toMillis()
        
        // Validate duration is reasonable
        val isValid = when {
            durationMs < 0 -> {
                Log.w(TAG, "Negative duration calculated: ${durationMs}ms")
                false
            }
            durationMs > MILLISECONDS_PER_DAY * 7 -> { // More than 7 days
                Log.w(TAG, "Unrealistic long duration: ${durationMs}ms (${durationMs / MILLISECONDS_PER_DAY} days)")
                false
            }
            else -> true
        }
        
        Log.d(TAG, "Calculated duration: ${durationMs}ms for entry=$entryTime, exit=$exitTime, isComplete=${exitTime != null}")
        
        return VisitDurationResult(
            durationMs = maxOf(0L, durationMs), // Ensure non-negative
            isComplete = exitTime != null,
            isValid = isValid,
            errorMessage = null
        )
    }
    
    /**
     * Calculate duration for Visit domain object
     */
    fun calculateVisitDuration(visit: Visit, currentTime: LocalDateTime = LocalDateTime.now()): VisitDurationResult {
        return calculateVisitDuration(
            entryTime = visit.entryTime,
            exitTime = visit.exitTime,
            currentTime = currentTime
        )
    }
    
    /**
     * Calculate current duration for active visits
     */
    fun calculateCurrentDuration(
        entryTime: LocalDateTime?,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): Long {
        val result = calculateVisitDuration(entryTime, null, currentTime)
        return if (result.isValid) result.durationMs else 0L
    }
    
    /**
     * Calculate total time from multiple visits
     * CRITICAL: Unified aggregation logic for analytics
     */
    fun calculateTotalTime(
        visits: List<Visit>,
        includeActiveVisits: Boolean = true,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): TotalTimeResult {
        
        if (visits.isEmpty()) {
            return TotalTimeResult(
                totalMs = 0L,
                completedVisitsTime = 0L,
                activeVisitsTime = 0L,
                visitCount = 0,
                activeVisitCount = 0,
                invalidVisitCount = 0
            )
        }
        
        var completedTime = 0L
        var activeTime = 0L
        var invalidCount = 0
        var activeCount = 0
        
        visits.forEach { visit ->
            val durationResult = calculateVisitDuration(visit, currentTime)
            
            if (!durationResult.isValid) {
                invalidCount++
                Log.w(TAG, "Invalid visit duration for visit ${visit.id}: ${durationResult.errorMessage}")
                return@forEach
            }
            
            if (durationResult.isComplete) {
                completedTime += durationResult.durationMs
            } else if (includeActiveVisits) {
                activeTime += durationResult.durationMs
                activeCount++
            }
        }
        
        val totalTime = completedTime + activeTime
        
        Log.d(TAG, "Calculated total time: ${totalTime}ms from ${visits.size} visits " +
              "(completed: ${completedTime}ms, active: ${activeTime}ms, invalid: $invalidCount)")
        
        return TotalTimeResult(
            totalMs = totalTime,
            completedVisitsTime = completedTime,
            activeVisitsTime = activeTime,
            visitCount = visits.size,
            activeVisitCount = activeCount,
            invalidVisitCount = invalidCount
        )
    }
    
    /**
     * Calculate time spent per category
     * CRITICAL: Unified category aggregation for analytics
     */
    fun calculateTimeByCategory(
        visits: List<Visit>,
        placeProvider: (Long) -> com.cosmiclaboratory.voyager.domain.model.Place?,
        includeActiveVisits: Boolean = true,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): Map<com.cosmiclaboratory.voyager.domain.model.PlaceCategory, Long> {
        
        val timeByCategory = mutableMapOf<com.cosmiclaboratory.voyager.domain.model.PlaceCategory, Long>()
        
        visits.forEach { visit ->
            val place = placeProvider(visit.placeId)
            if (place == null) {
                Log.w(TAG, "Place not found for visit ${visit.id}, placeId=${visit.placeId}")
                return@forEach
            }
            
            val durationResult = calculateVisitDuration(visit, currentTime)
            if (!durationResult.isValid) {
                Log.w(TAG, "Invalid duration for visit ${visit.id}: ${durationResult.errorMessage}")
                return@forEach
            }
            
            if (durationResult.isComplete || includeActiveVisits) {
                val currentTime = timeByCategory[place.category] ?: 0L
                timeByCategory[place.category] = currentTime + durationResult.durationMs
            }
        }
        
        Log.d(TAG, "Calculated time by category: ${timeByCategory.mapValues { "${it.value}ms" }}")
        
        return timeByCategory
    }
    
    /**
     * Format duration for display
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0m"
        
        val hours = durationMs / MILLISECONDS_PER_HOUR
        val minutes = (durationMs % MILLISECONDS_PER_HOUR) / MILLISECONDS_PER_MINUTE
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    /**
     * Format duration with seconds for precise display
     */
    fun formatDurationPrecise(durationMs: Long): String {
        if (durationMs <= 0) return "0s"
        
        val hours = durationMs / MILLISECONDS_PER_HOUR
        val minutes = (durationMs % MILLISECONDS_PER_HOUR) / MILLISECONDS_PER_MINUTE
        val seconds = (durationMs % MILLISECONDS_PER_MINUTE) / MILLISECONDS_PER_SECOND
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    /**
     * Check if duration is considered valid for analytics
     */
    fun isValidDuration(durationMs: Long): Boolean {
        return durationMs >= MIN_VALID_DURATION_MS && durationMs <= MILLISECONDS_PER_DAY * 7
    }
    
    /**
     * Calculate average duration from multiple visits
     */
    fun calculateAverageDuration(
        visits: List<Visit>,
        includeActiveVisits: Boolean = false,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): Long {
        val validDurations = visits.mapNotNull { visit ->
            val result = calculateVisitDuration(visit, currentTime)
            if (result.isValid && (result.isComplete || includeActiveVisits)) {
                result.durationMs
            } else {
                null
            }
        }
        
        return if (validDurations.isNotEmpty()) {
            validDurations.average().toLong()
        } else {
            0L
        }
    }
}

// Data classes for duration calculation results

data class VisitDurationResult(
    val durationMs: Long,
    val isComplete: Boolean, // true if visit has exit time
    val isValid: Boolean,    // true if calculation is valid
    val errorMessage: String? = null
)

data class TotalTimeResult(
    val totalMs: Long,
    val completedVisitsTime: Long,
    val activeVisitsTime: Long,
    val visitCount: Int,
    val activeVisitCount: Int,
    val invalidVisitCount: Int
) {
    val completedVisitCount: Int
        get() = visitCount - activeVisitCount - invalidVisitCount
        
    fun getFormattedTotal(): String {
        return DurationCalculator().formatDuration(totalMs)
    }
    
    fun getFormattedBreakdown(): String {
        return "Total: ${getFormattedTotal()} (${completedVisitCount} completed, $activeVisitCount active)"
    }
}

// Extension functions for easy access

fun Visit.calculateDuration(currentTime: LocalDateTime = LocalDateTime.now()): VisitDurationResult {
    return DurationCalculator().calculateVisitDuration(this, currentTime)
}

fun Visit.getCurrentDuration(currentTime: LocalDateTime = LocalDateTime.now()): Long {
    return DurationCalculator().calculateCurrentDuration(this.entryTime, currentTime)
}

fun List<Visit>.calculateTotalTime(
    includeActive: Boolean = true,
    currentTime: LocalDateTime = LocalDateTime.now()
): TotalTimeResult {
    return DurationCalculator().calculateTotalTime(this, includeActive, currentTime)
}