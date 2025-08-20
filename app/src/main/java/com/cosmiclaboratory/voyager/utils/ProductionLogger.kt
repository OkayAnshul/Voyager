package com.cosmiclaboratory.voyager.utils

import android.util.Log
import com.cosmiclaboratory.voyager.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-ready logging system with configurable levels
 * Replaces scattered debug code with centralized, controlled logging
 */
@Singleton
class ProductionLogger @Inject constructor() {
    
    companion object {
        // Production logging levels
        const val LEVEL_NONE = 0
        const val LEVEL_ERROR = 1
        const val LEVEL_WARN = 2
        const val LEVEL_INFO = 3
        const val LEVEL_DEBUG = 4
        const val LEVEL_VERBOSE = 5
        
        // Default to INFO level in production, DEBUG in debug builds
        private val DEFAULT_LEVEL = if (BuildConfig.DEBUG) LEVEL_DEBUG else LEVEL_INFO
    }
    
    private var currentLevel = DEFAULT_LEVEL
    private val tagPrefix = "Voyager"
    
    /**
     * Set logging level programmatically
     */
    fun setLogLevel(level: Int) {
        currentLevel = level
    }
    
    /**
     * Check if level is enabled
     */
    fun isLoggable(level: Int): Boolean = currentLevel >= level
    
    /**
     * Error logging - always enabled in production
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (currentLevel >= LEVEL_ERROR) {
            val fullTag = "$tagPrefix:$tag"
            if (throwable != null) {
                Log.e(fullTag, message, throwable)
            } else {
                Log.e(fullTag, message)
            }
        }
    }
    
    /**
     * Warning logging
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (currentLevel >= LEVEL_WARN) {
            val fullTag = "$tagPrefix:$tag"
            if (throwable != null) {
                Log.w(fullTag, message, throwable)
            } else {
                Log.w(fullTag, message)
            }
        }
    }
    
    /**
     * Info logging - for important production events
     */
    fun i(tag: String, message: String) {
        if (currentLevel >= LEVEL_INFO) {
            Log.i("$tagPrefix:$tag", message)
        }
    }
    
    /**
     * Debug logging - disabled in production builds
     */
    fun d(tag: String, message: String) {
        if (currentLevel >= LEVEL_DEBUG) {
            Log.d("$tagPrefix:$tag", message)
        }
    }
    
    /**
     * Verbose logging - only for development
     */
    fun v(tag: String, message: String) {
        if (currentLevel >= LEVEL_VERBOSE) {
            Log.v("$tagPrefix:$tag", message)
        }
    }
    
    /**
     * Performance logging for critical operations
     */
    fun perf(tag: String, operation: String, durationMs: Long) {
        if (currentLevel >= LEVEL_INFO) {
            i(tag, "PERF: $operation completed in ${durationMs}ms")
        }
    }
    
    /**
     * Data flow logging for debugging data consistency issues
     */
    fun dataFlow(tag: String, operation: String, details: String) {
        if (currentLevel >= LEVEL_DEBUG) {
            d(tag, "DATA_FLOW: $operation - $details")
        }
    }
    
    /**
     * State change logging for debugging state management
     */
    fun stateChange(tag: String, from: String, to: String, source: String) {
        if (currentLevel >= LEVEL_DEBUG) {
            d(tag, "STATE_CHANGE: $from → $to (source: $source)")
        }
    }
    
    /**
     * Analytics logging for tracking important metrics
     */
    fun analytics(tag: String, event: String, data: Map<String, Any>) {
        if (currentLevel >= LEVEL_INFO) {
            val dataStr = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
            i(tag, "ANALYTICS: $event - $dataStr")
        }
    }
    
    /**
     * Error recovery logging
     */
    fun recovery(tag: String, error: String, action: String, success: Boolean) {
        if (success) {
            i(tag, "RECOVERY_SUCCESS: $error → $action")
        } else {
            w(tag, "RECOVERY_FAILED: $error → $action")
        }
    }
}

/**
 * Extension functions for easy logging
 */
fun ProductionLogger.logLocationProcessing(tag: String, lat: Double, lng: Double, accuracy: Float) {
    dataFlow(tag, "LocationProcessing", "lat=$lat, lng=$lng, accuracy=${accuracy}m")
}

fun ProductionLogger.logVisitStateChange(tag: String, action: String, placeId: Long?, visitId: Long?) {
    stateChange(tag, "Visit", action, "placeId=$placeId, visitId=$visitId")
}

fun ProductionLogger.logTrackingStateChange(tag: String, isActive: Boolean, source: String) {
    stateChange(tag, "Tracking", if (isActive) "ACTIVE" else "INACTIVE", source)
}

fun ProductionLogger.logDailyStats(tag: String, locations: Int, places: Int, timeMs: Long) {
    analytics(tag, "DailyStats", mapOf(
        "locations" to locations,
        "places" to places,
        "timeTracked" to "${timeMs}ms"
    ))
}