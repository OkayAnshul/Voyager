package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user preferences
 */
interface PreferencesRepository {
    
    /**
     * Get user preferences as a Flow for reactive updates
     */
    fun getUserPreferences(): Flow<UserPreferences>
    
    /**
     * Update user preferences
     */
    suspend fun updateUserPreferences(preferences: UserPreferences)
    
    /**
     * Update a specific preference field
     */
    suspend fun updateLocationUpdateInterval(intervalMs: Long)
    suspend fun updateMinDistanceChange(distanceMeters: Float)
    suspend fun updateTrackingAccuracyMode(mode: com.cosmiclaboratory.voyager.domain.model.TrackingAccuracyMode)
    suspend fun updateLocationTrackingEnabled(enabled: Boolean)
    suspend fun updateClusteringDistance(distanceMeters: Double)
    suspend fun updateMinPointsForCluster(points: Int)
    suspend fun updatePlaceDetectionRadius(radiusMeters: Double)
    suspend fun updateSessionBreakTime(minutes: Int)
    suspend fun updateAutoConfidenceThreshold(threshold: Float)
    suspend fun updateDataRetentionDays(days: Int)
    suspend fun updateNotificationSettings(
        enableArrival: Boolean,
        enableDeparture: Boolean,
        enablePattern: Boolean,
        enableWeeklySummary: Boolean
    )
    
    // New user-configurable settings methods
    suspend fun updateMaxGpsAccuracy(accuracyMeters: Float)
    suspend fun updateMaxSpeedKmh(speedKmh: Double)
    suspend fun updateMinTimeBetweenUpdates(seconds: Int)
    suspend fun updatePlaceDetectionFrequency(hours: Int)
    suspend fun updateAutoDetectTriggerCount(count: Int)
    suspend fun updateBatteryRequirement(requirement: com.cosmiclaboratory.voyager.domain.model.BatteryRequirement)
    suspend fun updateActivityTimeRange(startHour: Int, endHour: Int)
    suspend fun updateDataProcessingBatchSize(size: Int)
    suspend fun updateDataMigrationVersion(version: Int)
    
    /**
     * Reset preferences to defaults
     */
    suspend fun resetToDefaults()
    
    /**
     * Get current preferences synchronously (for backward compatibility)
     */
    suspend fun getCurrentPreferences(): UserPreferences
}