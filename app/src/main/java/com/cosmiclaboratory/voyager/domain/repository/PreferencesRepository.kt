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

    // Sleep schedule settings (Phase 8.1)
    suspend fun updateSleepModeEnabled(enabled: Boolean)
    suspend fun updateSleepStartHour(hour: Int)
    suspend fun updateSleepEndHour(hour: Int)
    suspend fun updateSleepModeStrictness(strictness: com.cosmiclaboratory.voyager.domain.model.SleepModeStrictness)

    // Motion detection settings (Phase 8.4)
    suspend fun updateMotionDetectionEnabled(enabled: Boolean)
    suspend fun updateMotionSensitivityThreshold(threshold: Float)

    // Phase 2: Activity Recognition
    suspend fun updateActivityRecognition(enabled: Boolean)

    // Advanced place detection parameters
    suspend fun updateMinimumDistanceBetweenPlaces(meters: Float)
    suspend fun updateStationaryThreshold(minutes: Int)
    suspend fun updateStationaryMovementThreshold(meters: Float)

    // Geocoding parameters
    suspend fun updateGeocodingCacheDuration(days: Int)
    suspend fun updateGeocodingCachePrecision(precision: Int)

    // UI refresh intervals
    suspend fun updateDashboardRefreshIntervals(atPlaceSeconds: Int, trackingSeconds: Int, idleSeconds: Int)
    suspend fun updateAnalyticsCacheTimeout(seconds: Int)

    // Pattern analysis parameters
    suspend fun updatePatternAnalysisSettings(minVisits: Int, minConfidence: Float, timeWindowMinutes: Int, analysisDays: Int)

    // Anomaly detection parameters
    suspend fun updateAnomalyDetectionSettings(recentDays: Int, lookbackDays: Int, durationThreshold: Float, timeThresholdHours: Int)

    // Service configuration
    suspend fun updateServiceHealthCheckInterval(seconds: Int)
    suspend fun updateServiceStopGracePeriod(milliseconds: Long)
    suspend fun updatePermissionCheckInterval(seconds: Int)

    // Battery & performance tuning
    suspend fun updateStationaryMultipliers(intervalMultiplier: Float, distanceMultiplier: Float)
    suspend fun updateForceSaveSettings(intervalMultiplier: Int, maxSeconds: Int)
    suspend fun updateMinimumMovementForTimeSave(meters: Float)

    // Daily summary
    suspend fun updateDailySummarySettings(enabled: Boolean, hour: Int)

    // Settings profile management
    suspend fun applySettingsProfile(profileName: String)
    suspend fun getCurrentProfileName(): String
    suspend fun updateCurrentProfile(profileName: String)

    // Place Review Settings (Week 5)
    suspend fun updateAutoApproveEnabled(enabled: Boolean)
    suspend fun updateAutoApproveThreshold(threshold: Float)
    suspend fun updateReviewNotificationsEnabled(enabled: Boolean)

    // Timeline Settings (Phase 3)
    suspend fun updateTimelineTimeWindow(windowMinutes: Long)
    suspend fun updateTimelineDistanceThreshold(distanceMeters: Double)

    /**
     * Reset preferences to defaults
     */
    suspend fun resetToDefaults()

    /**
     * Get current preferences synchronously (for backward compatibility)
     */
    suspend fun getCurrentPreferences(): UserPreferences
}