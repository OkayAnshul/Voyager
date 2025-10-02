package com.cosmiclaboratory.voyager.utils

import android.os.Build
import java.time.LocalDateTime
import java.util.Date

/**
 * Utility class to handle API level compatibility issues throughout the application
 */
object ApiCompatibilityUtils {
    
    /**
     * Safely get current LocalDateTime across all API levels
     */
    fun getCurrentDateTime(): LocalDateTime {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime.now()
        } else {
            // For API < 26, create LocalDateTime from current time millis
            val currentTimeMillis = System.currentTimeMillis()
            val date = Date(currentTimeMillis)
            @Suppress("DEPRECATION")
            LocalDateTime.of(
                date.year + 1900,
                date.month + 1,
                date.date,
                date.hours,
                date.minutes,
                date.seconds
            )
        }
    }
    
    /**
     * Safely create LocalDateTime from millis across all API levels
     */
    fun localDateTimeFromMillis(millis: Long): LocalDateTime {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val instant = java.time.Instant.ofEpochMilli(millis)
            LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        } else {
            val date = Date(millis)
            @Suppress("DEPRECATION")
            LocalDateTime.of(
                date.year + 1900,
                date.month + 1,
                date.date,
                date.hours,
                date.minutes,
                date.seconds
            )
        }
    }
    
    /**
     * Safely convert LocalDateTime to millis across all API levels
     */
    fun localDateTimeToMillis(localDateTime: LocalDateTime): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            @Suppress("DEPRECATION")
            Date(
                localDateTime.year - 1900,
                localDateTime.monthValue - 1,
                localDateTime.dayOfMonth,
                localDateTime.hour,
                localDateTime.minute,
                localDateTime.second
            ).time
        }
    }
    
    /**
     * Calculate duration between two LocalDateTime objects in a compatible way
     */
    fun calculateDurationText(start: LocalDateTime, end: LocalDateTime): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val duration = java.time.Duration.between(start, end)
            formatDuration(duration.toMinutes())
        } else {
            // For API < 26, calculate manually
            val startMillis = localDateTimeToMillis(start)
            val endMillis = localDateTimeToMillis(end)
            val durationMillis = endMillis - startMillis
            val minutes = durationMillis / (1000 * 60)
            formatDuration(minutes)
        }
    }
    
    /**
     * Format duration in minutes to readable text
     */
    private fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }
    
    /**
     * Check if the current API level supports a specific feature
     */
    fun isApiLevelSupported(apiLevel: Int): Boolean {
        return Build.VERSION.SDK_INT >= apiLevel
    }
    
    /**
     * Check if Java 8 Time API is available
     */
    fun isJava8TimeApiAvailable(): Boolean {
        return isApiLevelSupported(Build.VERSION_CODES.O)
    }
    
    /**
     * Check if background location permission is required separately
     */
    fun requiresBackgroundLocationPermission(): Boolean {
        return isApiLevelSupported(Build.VERSION_CODES.Q)
    }
    
    /**
     * Check if foreground service types are required
     */
    fun requiresForegroundServiceTypes(): Boolean {
        return isApiLevelSupported(Build.VERSION_CODES.Q)
    }
    
    /**
     * Check if notification channels are required
     */
    fun requiresNotificationChannels(): Boolean {
        return isApiLevelSupported(Build.VERSION_CODES.O)
    }
}