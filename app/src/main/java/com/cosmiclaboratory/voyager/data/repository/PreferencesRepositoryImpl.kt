package com.cosmiclaboratory.voyager.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cosmiclaboratory.voyager.domain.model.BatteryRequirement
import com.cosmiclaboratory.voyager.domain.model.ExportFormat
import com.cosmiclaboratory.voyager.domain.model.TrackingAccuracyMode
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import com.cosmiclaboratory.voyager.domain.model.validated
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    private val _userPreferences = MutableStateFlow(loadPreferences())
    
    override fun getUserPreferences(): Flow<UserPreferences> = _userPreferences.asStateFlow()
    
    override suspend fun updateUserPreferences(preferences: UserPreferences) {
        val validatedPrefs = preferences.validated()
        savePreferences(validatedPrefs)
        _userPreferences.value = validatedPrefs
    }
    
    override suspend fun updateLocationUpdateInterval(intervalMs: Long) {
        val current = _userPreferences.value
        val updated = current.copy(locationUpdateIntervalMs = intervalMs).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateMinDistanceChange(distanceMeters: Float) {
        val current = _userPreferences.value
        val updated = current.copy(minDistanceChangeMeters = distanceMeters).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateTrackingAccuracyMode(mode: TrackingAccuracyMode) {
        val current = _userPreferences.value
        val updated = current.copy(trackingAccuracyMode = mode)
        updateUserPreferences(updated)
    }
    
    override suspend fun updateLocationTrackingEnabled(enabled: Boolean) {
        val current = _userPreferences.value
        val updated = current.copy(isLocationTrackingEnabled = enabled)
        updateUserPreferences(updated)
    }
    
    override suspend fun updateClusteringDistance(distanceMeters: Double) {
        val current = _userPreferences.value
        val updated = current.copy(clusteringDistanceMeters = distanceMeters).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateMinPointsForCluster(points: Int) {
        val current = _userPreferences.value
        val updated = current.copy(minPointsForCluster = points).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updatePlaceDetectionRadius(radiusMeters: Double) {
        val current = _userPreferences.value
        val updated = current.copy(placeDetectionRadius = radiusMeters).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateSessionBreakTime(minutes: Int) {
        val current = _userPreferences.value
        val updated = current.copy(sessionBreakTimeMinutes = minutes).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateAutoConfidenceThreshold(threshold: Float) {
        val current = _userPreferences.value
        val updated = current.copy(autoConfidenceThreshold = threshold).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateDataRetentionDays(days: Int) {
        val current = _userPreferences.value
        val updated = current.copy(dataRetentionDays = days).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateNotificationSettings(
        enableArrival: Boolean,
        enableDeparture: Boolean,
        enablePattern: Boolean,
        enableWeeklySummary: Boolean
    ) {
        val current = _userPreferences.value
        val updated = current.copy(
            enableArrivalNotifications = enableArrival,
            enableDepartureNotifications = enableDeparture,
            enablePatternNotifications = enablePattern,
            enableWeeklySummary = enableWeeklySummary
        )
        updateUserPreferences(updated)
    }
    
    override suspend fun resetToDefaults() {
        val defaults = UserPreferences()
        updateUserPreferences(defaults)
    }
    
    override suspend fun getCurrentPreferences(): UserPreferences {
        return _userPreferences.value
    }
    
    // New user-configurable settings implementations
    override suspend fun updateMaxGpsAccuracy(accuracyMeters: Float) {
        val current = _userPreferences.value
        val updated = current.copy(maxGpsAccuracyMeters = accuracyMeters).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateMaxSpeedKmh(speedKmh: Double) {
        val current = _userPreferences.value
        val updated = current.copy(maxSpeedKmh = speedKmh).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateMinTimeBetweenUpdates(seconds: Int) {
        val current = _userPreferences.value
        val updated = current.copy(minTimeBetweenUpdatesSeconds = seconds).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updatePlaceDetectionFrequency(hours: Int) {
        val current = _userPreferences.value
        val updated = current.copy(placeDetectionFrequencyHours = hours).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateAutoDetectTriggerCount(count: Int) {
        val current = _userPreferences.value
        val updated = current.copy(autoDetectTriggerCount = count).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateBatteryRequirement(requirement: BatteryRequirement) {
        val current = _userPreferences.value
        val updated = current.copy(batteryRequirement = requirement)
        updateUserPreferences(updated)
    }
    
    override suspend fun updateActivityTimeRange(startHour: Int, endHour: Int) {
        val current = _userPreferences.value
        val updated = current.copy(
            activityTimeRangeStart = startHour,
            activityTimeRangeEnd = endHour
        ).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateDataProcessingBatchSize(size: Int) {
        val current = _userPreferences.value
        val updated = current.copy(dataProcessingBatchSize = size).validated()
        updateUserPreferences(updated)
    }
    
    override suspend fun updateDataMigrationVersion(version: Int) {
        val current = _userPreferences.value
        val updated = current.copy(dataMigrationVersion = version)
        updateUserPreferences(updated)
    }

    // Sleep schedule settings (Phase 8.1)
    override suspend fun updateSleepModeEnabled(enabled: Boolean) {
        val current = _userPreferences.value
        val updated = current.copy(sleepModeEnabled = enabled)
        updateUserPreferences(updated)
    }

    override suspend fun updateSleepStartHour(hour: Int) {
        val current = _userPreferences.value
        val updated = current.copy(sleepStartHour = hour).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateSleepEndHour(hour: Int) {
        val current = _userPreferences.value
        val updated = current.copy(sleepEndHour = hour).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateSleepModeStrictness(strictness: com.cosmiclaboratory.voyager.domain.model.SleepModeStrictness) {
        val current = _userPreferences.value
        val updated = current.copy(sleepModeStrictness = strictness)
        updateUserPreferences(updated)
    }

    // Motion detection settings (Phase 8.4)
    override suspend fun updateMotionDetectionEnabled(enabled: Boolean) {
        val current = _userPreferences.value
        val updated = current.copy(motionDetectionEnabled = enabled)
        updateUserPreferences(updated)
    }

    override suspend fun updateMotionSensitivityThreshold(threshold: Float) {
        val current = _userPreferences.value
        val updated = current.copy(motionSensitivityThreshold = threshold).validated()
        updateUserPreferences(updated)
    }

    // Phase 2: Activity Recognition
    override suspend fun updateActivityRecognition(enabled: Boolean) {
        val current = _userPreferences.value
        val updated = current.copy(useActivityRecognition = enabled)
        updateUserPreferences(updated)
    }

    // Advanced place detection parameters
    override suspend fun updateMinimumDistanceBetweenPlaces(meters: Float) {
        val current = _userPreferences.value
        val updated = current.copy(minimumDistanceBetweenPlaces = meters).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateStationaryThreshold(minutes: Int) {
        val current = _userPreferences.value
        val updated = current.copy(stationaryThresholdMinutes = minutes).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateStationaryMovementThreshold(meters: Float) {
        val current = _userPreferences.value
        val updated = current.copy(stationaryMovementThreshold = meters).validated()
        updateUserPreferences(updated)
    }

    // Geocoding parameters
    override suspend fun updateGeocodingCacheDuration(days: Int) {
        val current = _userPreferences.value
        val updated = current.copy(geocodingCacheDurationDays = days).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateGeocodingCachePrecision(precision: Int) {
        val current = _userPreferences.value
        val updated = current.copy(geocodingCachePrecision = precision).validated()
        updateUserPreferences(updated)
    }

    // UI refresh intervals
    override suspend fun updateDashboardRefreshIntervals(atPlaceSeconds: Int, trackingSeconds: Int, idleSeconds: Int) {
        val current = _userPreferences.value
        val updated = current.copy(
            dashboardRefreshAtPlaceSeconds = atPlaceSeconds,
            dashboardRefreshTrackingSeconds = trackingSeconds,
            dashboardRefreshIdleSeconds = idleSeconds
        ).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateAnalyticsCacheTimeout(seconds: Int) {
        val current = _userPreferences.value
        val updated = current.copy(analyticsCacheTimeoutSeconds = seconds).validated()
        updateUserPreferences(updated)
    }

    // Pattern analysis parameters
    override suspend fun updatePatternAnalysisSettings(minVisits: Int, minConfidence: Float, timeWindowMinutes: Int, analysisDays: Int) {
        val current = _userPreferences.value
        val updated = current.copy(
            patternMinVisits = minVisits,
            patternMinConfidence = minConfidence,
            patternTimeWindowMinutes = timeWindowMinutes,
            patternAnalysisDays = analysisDays
        ).validated()
        updateUserPreferences(updated)
    }

    // Anomaly detection parameters
    override suspend fun updateAnomalyDetectionSettings(recentDays: Int, lookbackDays: Int, durationThreshold: Float, timeThresholdHours: Int) {
        val current = _userPreferences.value
        val updated = current.copy(
            anomalyRecentDays = recentDays,
            anomalyLookbackDays = lookbackDays,
            anomalyDurationThreshold = durationThreshold,
            anomalyTimeThresholdHours = timeThresholdHours
        ).validated()
        updateUserPreferences(updated)
    }

    // Service configuration
    override suspend fun updateServiceHealthCheckInterval(seconds: Int) {
        val current = _userPreferences.value
        val updated = current.copy(serviceHealthCheckIntervalSeconds = seconds).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateServiceStopGracePeriod(milliseconds: Long) {
        val current = _userPreferences.value
        val updated = current.copy(serviceStopGracePeriodMs = milliseconds).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updatePermissionCheckInterval(seconds: Int) {
        val current = _userPreferences.value
        val updated = current.copy(permissionCheckIntervalSeconds = seconds).validated()
        updateUserPreferences(updated)
    }

    // Battery & performance tuning
    override suspend fun updateStationaryMultipliers(intervalMultiplier: Float, distanceMultiplier: Float) {
        val current = _userPreferences.value
        val updated = current.copy(
            stationaryIntervalMultiplier = intervalMultiplier,
            stationaryDistanceMultiplier = distanceMultiplier
        ).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateForceSaveSettings(intervalMultiplier: Int, maxSeconds: Int) {
        val current = _userPreferences.value
        val updated = current.copy(
            forceSaveIntervalMultiplier = intervalMultiplier,
            forceSaveMaxSeconds = maxSeconds
        ).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateMinimumMovementForTimeSave(meters: Float) {
        val current = _userPreferences.value
        val updated = current.copy(minimumMovementForTimeSave = meters).validated()
        updateUserPreferences(updated)
    }

    // Daily summary
    override suspend fun updateDailySummarySettings(enabled: Boolean, hour: Int) {
        val current = _userPreferences.value
        val updated = current.copy(
            dailySummaryEnabled = enabled,
            dailySummaryHour = hour
        ).validated()
        updateUserPreferences(updated)
    }

    // Settings profile management
    override suspend fun applySettingsProfile(profileName: String) {
        val profile = try {
            com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile.valueOf(profileName)
        } catch (e: IllegalArgumentException) {
            com.cosmiclaboratory.voyager.domain.model.SettingsPresetProfile.CUSTOM
        }

        val current = _userPreferences.value
        val profilePreferences = profile.toUserPreferences(current)
        val updated = profilePreferences.copy(currentProfile = profileName)
        updateUserPreferences(updated)
    }

    override suspend fun getCurrentProfileName(): String {
        return _userPreferences.value.currentProfile
    }

    override suspend fun updateCurrentProfile(profileName: String) {
        val current = _userPreferences.value
        val updated = current.copy(currentProfile = profileName)
        updateUserPreferences(updated)
    }

    // Place Review Settings (Week 5)
    override suspend fun updateAutoApproveEnabled(enabled: Boolean) {
        val current = _userPreferences.value
        val updated = current.copy(autoApproveEnabled = enabled).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateAutoApproveThreshold(threshold: Float) {
        val current = _userPreferences.value
        val updated = current.copy(autoApproveThreshold = threshold).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateReviewNotificationsEnabled(enabled: Boolean) {
        val current = _userPreferences.value
        val updated = current.copy(reviewNotificationsEnabled = enabled).validated()
        updateUserPreferences(updated)
    }

    // Timeline Settings (Phase 3)
    override suspend fun updateTimelineTimeWindow(windowMinutes: Long) {
        val current = _userPreferences.value
        val updated = current.copy(timelineTimeWindowMinutes = windowMinutes).validated()
        updateUserPreferences(updated)
    }

    override suspend fun updateTimelineDistanceThreshold(distanceMeters: Double) {
        val current = _userPreferences.value
        val updated = current.copy(timelineDistanceThresholdMeters = distanceMeters).validated()
        updateUserPreferences(updated)
    }

    private fun loadPreferences(): UserPreferences {
        return try {
            UserPreferences(
                locationUpdateIntervalMs = sharedPreferences.getLong(KEY_LOCATION_UPDATE_INTERVAL, 30000L),
                minDistanceChangeMeters = sharedPreferences.getFloat(KEY_MIN_DISTANCE_CHANGE, 10f),
                trackingAccuracyMode = TrackingAccuracyMode.valueOf(
                    sharedPreferences.getString(KEY_TRACKING_ACCURACY_MODE, TrackingAccuracyMode.BALANCED.name) 
                        ?: TrackingAccuracyMode.BALANCED.name
                ),
                isLocationTrackingEnabled = sharedPreferences.getBoolean(KEY_LOCATION_TRACKING_ENABLED, false),
                clusteringDistanceMeters = sharedPreferences.getFloat(KEY_CLUSTERING_DISTANCE, 50f).toDouble(),
                minPointsForCluster = sharedPreferences.getInt(KEY_MIN_POINTS_FOR_CLUSTER, 3),
                placeDetectionRadius = sharedPreferences.getFloat(KEY_PLACE_DETECTION_RADIUS, 100f).toDouble(),
                sessionBreakTimeMinutes = sharedPreferences.getInt(KEY_SESSION_BREAK_TIME, 30),
                minVisitDurationMinutes = sharedPreferences.getInt(KEY_MIN_VISIT_DURATION, 5),
                autoConfidenceThreshold = sharedPreferences.getFloat(KEY_AUTO_CONFIDENCE_THRESHOLD, 0.7f),
                homeNightActivityThreshold = sharedPreferences.getFloat(KEY_HOME_NIGHT_THRESHOLD, 0.6f),
                workHoursActivityThreshold = sharedPreferences.getFloat(KEY_WORK_HOURS_THRESHOLD, 0.5f),
                gymActivityThreshold = sharedPreferences.getFloat(KEY_GYM_ACTIVITY_THRESHOLD, 0.7f),
                shoppingMinDurationMinutes = sharedPreferences.getInt(KEY_SHOPPING_MIN_DURATION, 30),
                shoppingMaxDurationMinutes = sharedPreferences.getInt(KEY_SHOPPING_MAX_DURATION, 120),
                restaurantMealTimeThreshold = sharedPreferences.getFloat(KEY_RESTAURANT_MEAL_THRESHOLD, 0.6f),
                dataRetentionDays = sharedPreferences.getInt(KEY_DATA_RETENTION_DAYS, 365),
                maxLocationsToProcess = sharedPreferences.getInt(KEY_MAX_LOCATIONS_PROCESS, 10000),
                maxRecentLocationsDisplay = sharedPreferences.getInt(KEY_MAX_RECENT_LOCATIONS, 1000),
                dataMigrationVersion = sharedPreferences.getInt(KEY_DATA_MIGRATION_VERSION, 0),
                enableArrivalNotifications = sharedPreferences.getBoolean(KEY_ENABLE_ARRIVAL_NOTIFICATIONS, true),
                enableDepartureNotifications = sharedPreferences.getBoolean(KEY_ENABLE_DEPARTURE_NOTIFICATIONS, false),
                enablePatternNotifications = sharedPreferences.getBoolean(KEY_ENABLE_PATTERN_NOTIFICATIONS, true),
                enableWeeklySummary = sharedPreferences.getBoolean(KEY_ENABLE_WEEKLY_SUMMARY, true),
                notificationUpdateFrequency = sharedPreferences.getInt(KEY_NOTIFICATION_UPDATE_FREQUENCY, 10),
                defaultExportFormat = ExportFormat.valueOf(
                    sharedPreferences.getString(KEY_DEFAULT_EXPORT_FORMAT, ExportFormat.JSON.name) 
                        ?: ExportFormat.JSON.name
                ),
                includeRawLocations = sharedPreferences.getBoolean(KEY_INCLUDE_RAW_LOCATIONS, true),
                includePlaceData = sharedPreferences.getBoolean(KEY_INCLUDE_PLACE_DATA, true),
                includeVisitData = sharedPreferences.getBoolean(KEY_INCLUDE_VISIT_DATA, true),
                includeAnalytics = sharedPreferences.getBoolean(KEY_INCLUDE_ANALYTICS, false),
                enableLocationHistory = sharedPreferences.getBoolean(KEY_ENABLE_LOCATION_HISTORY, true),
                enablePlaceDetection = sharedPreferences.getBoolean(KEY_ENABLE_PLACE_DETECTION, true),
                enableAnalytics = sharedPreferences.getBoolean(KEY_ENABLE_ANALYTICS, true),
                anonymizeExports = sharedPreferences.getBoolean(KEY_ANONYMIZE_EXPORTS, false),
                // New user-configurable settings with defaults
                maxGpsAccuracyMeters = sharedPreferences.getFloat(KEY_MAX_GPS_ACCURACY, 100f),
                maxSpeedKmh = sharedPreferences.getFloat(KEY_MAX_SPEED_KMH, 200f).toDouble(),
                minTimeBetweenUpdatesSeconds = sharedPreferences.getInt(KEY_MIN_TIME_BETWEEN_UPDATES, 10),
                placeDetectionFrequencyHours = sharedPreferences.getInt(KEY_PLACE_DETECTION_FREQUENCY, 6),
                autoDetectTriggerCount = sharedPreferences.getInt(KEY_AUTO_DETECT_TRIGGER_COUNT, 25),
                batteryRequirement = BatteryRequirement.valueOf(
                    sharedPreferences.getString(KEY_BATTERY_REQUIREMENT, BatteryRequirement.ANY.name) 
                        ?: BatteryRequirement.ANY.name
                ),
                activityTimeRangeStart = sharedPreferences.getInt(KEY_ACTIVITY_TIME_START, 6),
                activityTimeRangeEnd = sharedPreferences.getInt(KEY_ACTIVITY_TIME_END, 23),
                dataProcessingBatchSize = sharedPreferences.getInt(KEY_DATA_PROCESSING_BATCH_SIZE, 1000),
                // Sleep schedule settings (Phase 8.1)
                sleepModeEnabled = sharedPreferences.getBoolean(KEY_SLEEP_MODE_ENABLED, false),
                sleepStartHour = sharedPreferences.getInt(KEY_SLEEP_START_HOUR, 22),
                sleepEndHour = sharedPreferences.getInt(KEY_SLEEP_END_HOUR, 6),
                sleepModeStrictness = com.cosmiclaboratory.voyager.domain.model.SleepModeStrictness.valueOf(
                    sharedPreferences.getString(KEY_SLEEP_MODE_STRICTNESS, com.cosmiclaboratory.voyager.domain.model.SleepModeStrictness.NORMAL.name)
                        ?: com.cosmiclaboratory.voyager.domain.model.SleepModeStrictness.NORMAL.name
                ),
                // Motion detection settings (Phase 8.4)
                motionDetectionEnabled = sharedPreferences.getBoolean(KEY_MOTION_DETECTION_ENABLED, true),
                motionSensitivityThreshold = sharedPreferences.getFloat(KEY_MOTION_SENSITIVITY_THRESHOLD, 0.5f),
                // Advanced place detection parameters
                minimumDistanceBetweenPlaces = sharedPreferences.getFloat(KEY_MIN_DISTANCE_BETWEEN_PLACES, 25f),
                stationaryThresholdMinutes = sharedPreferences.getInt(KEY_STATIONARY_THRESHOLD_MINUTES, 5),
                stationaryMovementThreshold = sharedPreferences.getFloat(KEY_STATIONARY_MOVEMENT_THRESHOLD, 20f),
                // Geocoding & caching parameters
                geocodingCacheDurationDays = sharedPreferences.getInt(KEY_GEOCODING_CACHE_DURATION, 30),
                geocodingCachePrecision = sharedPreferences.getInt(KEY_GEOCODING_CACHE_PRECISION, 3),
                // UI refresh intervals
                dashboardRefreshAtPlaceSeconds = sharedPreferences.getInt(KEY_DASHBOARD_REFRESH_AT_PLACE, 30),
                dashboardRefreshTrackingSeconds = sharedPreferences.getInt(KEY_DASHBOARD_REFRESH_TRACKING, 60),
                dashboardRefreshIdleSeconds = sharedPreferences.getInt(KEY_DASHBOARD_REFRESH_IDLE, 120),
                analyticsCacheTimeoutSeconds = sharedPreferences.getInt(KEY_ANALYTICS_CACHE_TIMEOUT, 30),
                // Pattern analysis parameters
                patternMinVisits = sharedPreferences.getInt(KEY_PATTERN_MIN_VISITS, 3),
                patternMinConfidence = sharedPreferences.getFloat(KEY_PATTERN_MIN_CONFIDENCE, 0.3f),
                patternTimeWindowMinutes = sharedPreferences.getInt(KEY_PATTERN_TIME_WINDOW, 60),
                patternAnalysisDays = sharedPreferences.getInt(KEY_PATTERN_ANALYSIS_DAYS, 90),
                // Anomaly detection parameters
                anomalyRecentDays = sharedPreferences.getInt(KEY_ANOMALY_RECENT_DAYS, 14),
                anomalyLookbackDays = sharedPreferences.getInt(KEY_ANOMALY_LOOKBACK_DAYS, 90),
                anomalyDurationThreshold = sharedPreferences.getFloat(KEY_ANOMALY_DURATION_THRESHOLD, 0.5f),
                anomalyTimeThresholdHours = sharedPreferences.getInt(KEY_ANOMALY_TIME_THRESHOLD, 3),
                // Service & worker configuration
                serviceHealthCheckIntervalSeconds = sharedPreferences.getInt(KEY_SERVICE_HEALTH_CHECK_INTERVAL, 30),
                serviceStopGracePeriodMs = sharedPreferences.getLong(KEY_SERVICE_STOP_GRACE_PERIOD, 5000L),
                permissionCheckIntervalSeconds = sharedPreferences.getInt(KEY_PERMISSION_CHECK_INTERVAL, 60),
                // Battery & performance tuning
                stationaryIntervalMultiplier = sharedPreferences.getFloat(KEY_STATIONARY_INTERVAL_MULTIPLIER, 2.0f),
                stationaryDistanceMultiplier = sharedPreferences.getFloat(KEY_STATIONARY_DISTANCE_MULTIPLIER, 1.5f),
                forceSaveIntervalMultiplier = sharedPreferences.getInt(KEY_FORCE_SAVE_INTERVAL_MULTIPLIER, 4),
                forceSaveMaxSeconds = sharedPreferences.getInt(KEY_FORCE_SAVE_MAX_SECONDS, 600),
                minimumMovementForTimeSave = sharedPreferences.getFloat(KEY_MIN_MOVEMENT_FOR_TIME_SAVE, 3.0f),
                // Daily summary configuration
                dailySummaryHour = sharedPreferences.getInt(KEY_DAILY_SUMMARY_HOUR, 9),
                dailySummaryEnabled = sharedPreferences.getBoolean(KEY_DAILY_SUMMARY_ENABLED, true),
                // Current settings profile
                currentProfile = sharedPreferences.getString(KEY_CURRENT_PROFILE, "DAILY_COMMUTER") ?: "DAILY_COMMUTER",
                // Place Review Settings (Week 5)
                autoApproveEnabled = sharedPreferences.getBoolean(KEY_AUTO_APPROVE_ENABLED, true),
                autoApproveThreshold = sharedPreferences.getFloat(KEY_AUTO_APPROVE_THRESHOLD, 0.85f),
                reviewNotificationsEnabled = sharedPreferences.getBoolean(KEY_REVIEW_NOTIFICATIONS_ENABLED, true),
                // Timeline Settings (Phase 3)
                timelineTimeWindowMinutes = sharedPreferences.getLong(KEY_TIMELINE_TIME_WINDOW, 30L),
                timelineDistanceThresholdMeters = sharedPreferences.getFloat(KEY_TIMELINE_DISTANCE_THRESHOLD, 200f).toDouble()
            ).validated()
        } catch (e: Exception) {
            // If there's any error loading preferences, return defaults
            UserPreferences()
        }
    }
    
    private fun savePreferences(preferences: UserPreferences) {
        with(sharedPreferences.edit()) {
            putLong(KEY_LOCATION_UPDATE_INTERVAL, preferences.locationUpdateIntervalMs)
            putFloat(KEY_MIN_DISTANCE_CHANGE, preferences.minDistanceChangeMeters)
            putString(KEY_TRACKING_ACCURACY_MODE, preferences.trackingAccuracyMode.name)
            putBoolean(KEY_LOCATION_TRACKING_ENABLED, preferences.isLocationTrackingEnabled)
            putFloat(KEY_CLUSTERING_DISTANCE, preferences.clusteringDistanceMeters.toFloat())
            putInt(KEY_MIN_POINTS_FOR_CLUSTER, preferences.minPointsForCluster)
            putFloat(KEY_PLACE_DETECTION_RADIUS, preferences.placeDetectionRadius.toFloat())
            putInt(KEY_SESSION_BREAK_TIME, preferences.sessionBreakTimeMinutes)
            putInt(KEY_MIN_VISIT_DURATION, preferences.minVisitDurationMinutes)
            putFloat(KEY_AUTO_CONFIDENCE_THRESHOLD, preferences.autoConfidenceThreshold)
            putFloat(KEY_HOME_NIGHT_THRESHOLD, preferences.homeNightActivityThreshold)
            putFloat(KEY_WORK_HOURS_THRESHOLD, preferences.workHoursActivityThreshold)
            putFloat(KEY_GYM_ACTIVITY_THRESHOLD, preferences.gymActivityThreshold)
            putInt(KEY_SHOPPING_MIN_DURATION, preferences.shoppingMinDurationMinutes)
            putInt(KEY_SHOPPING_MAX_DURATION, preferences.shoppingMaxDurationMinutes)
            putFloat(KEY_RESTAURANT_MEAL_THRESHOLD, preferences.restaurantMealTimeThreshold)
            putInt(KEY_DATA_RETENTION_DAYS, preferences.dataRetentionDays)
            putInt(KEY_MAX_LOCATIONS_PROCESS, preferences.maxLocationsToProcess)
            putInt(KEY_MAX_RECENT_LOCATIONS, preferences.maxRecentLocationsDisplay)
            putBoolean(KEY_ENABLE_ARRIVAL_NOTIFICATIONS, preferences.enableArrivalNotifications)
            putBoolean(KEY_ENABLE_DEPARTURE_NOTIFICATIONS, preferences.enableDepartureNotifications)
            putBoolean(KEY_ENABLE_PATTERN_NOTIFICATIONS, preferences.enablePatternNotifications)
            putBoolean(KEY_ENABLE_WEEKLY_SUMMARY, preferences.enableWeeklySummary)
            putInt(KEY_NOTIFICATION_UPDATE_FREQUENCY, preferences.notificationUpdateFrequency)
            putString(KEY_DEFAULT_EXPORT_FORMAT, preferences.defaultExportFormat.name)
            putBoolean(KEY_INCLUDE_RAW_LOCATIONS, preferences.includeRawLocations)
            putBoolean(KEY_INCLUDE_PLACE_DATA, preferences.includePlaceData)
            putBoolean(KEY_INCLUDE_VISIT_DATA, preferences.includeVisitData)
            putBoolean(KEY_INCLUDE_ANALYTICS, preferences.includeAnalytics)
            putBoolean(KEY_ENABLE_LOCATION_HISTORY, preferences.enableLocationHistory)
            putBoolean(KEY_ENABLE_PLACE_DETECTION, preferences.enablePlaceDetection)
            putBoolean(KEY_ENABLE_ANALYTICS, preferences.enableAnalytics)
            putBoolean(KEY_ANONYMIZE_EXPORTS, preferences.anonymizeExports)
            // Save new user-configurable settings
            putFloat(KEY_MAX_GPS_ACCURACY, preferences.maxGpsAccuracyMeters)
            putFloat(KEY_MAX_SPEED_KMH, preferences.maxSpeedKmh.toFloat())
            putInt(KEY_MIN_TIME_BETWEEN_UPDATES, preferences.minTimeBetweenUpdatesSeconds)
            putInt(KEY_PLACE_DETECTION_FREQUENCY, preferences.placeDetectionFrequencyHours)
            putInt(KEY_AUTO_DETECT_TRIGGER_COUNT, preferences.autoDetectTriggerCount)
            putString(KEY_BATTERY_REQUIREMENT, preferences.batteryRequirement.name)
            putInt(KEY_ACTIVITY_TIME_START, preferences.activityTimeRangeStart)
            putInt(KEY_ACTIVITY_TIME_END, preferences.activityTimeRangeEnd)
            putInt(KEY_DATA_PROCESSING_BATCH_SIZE, preferences.dataProcessingBatchSize)
            putInt(KEY_DATA_MIGRATION_VERSION, preferences.dataMigrationVersion)
            // Save sleep schedule settings (Phase 8.1)
            putBoolean(KEY_SLEEP_MODE_ENABLED, preferences.sleepModeEnabled)
            putInt(KEY_SLEEP_START_HOUR, preferences.sleepStartHour)
            putInt(KEY_SLEEP_END_HOUR, preferences.sleepEndHour)
            putString(KEY_SLEEP_MODE_STRICTNESS, preferences.sleepModeStrictness.name)
            // Save motion detection settings (Phase 8.4)
            putBoolean(KEY_MOTION_DETECTION_ENABLED, preferences.motionDetectionEnabled)
            putFloat(KEY_MOTION_SENSITIVITY_THRESHOLD, preferences.motionSensitivityThreshold)
            // Save advanced place detection parameters
            putFloat(KEY_MIN_DISTANCE_BETWEEN_PLACES, preferences.minimumDistanceBetweenPlaces)
            putInt(KEY_STATIONARY_THRESHOLD_MINUTES, preferences.stationaryThresholdMinutes)
            putFloat(KEY_STATIONARY_MOVEMENT_THRESHOLD, preferences.stationaryMovementThreshold)
            // Save geocoding parameters
            putInt(KEY_GEOCODING_CACHE_DURATION, preferences.geocodingCacheDurationDays)
            putInt(KEY_GEOCODING_CACHE_PRECISION, preferences.geocodingCachePrecision)
            // Save UI refresh intervals
            putInt(KEY_DASHBOARD_REFRESH_AT_PLACE, preferences.dashboardRefreshAtPlaceSeconds)
            putInt(KEY_DASHBOARD_REFRESH_TRACKING, preferences.dashboardRefreshTrackingSeconds)
            putInt(KEY_DASHBOARD_REFRESH_IDLE, preferences.dashboardRefreshIdleSeconds)
            putInt(KEY_ANALYTICS_CACHE_TIMEOUT, preferences.analyticsCacheTimeoutSeconds)
            // Save pattern analysis parameters
            putInt(KEY_PATTERN_MIN_VISITS, preferences.patternMinVisits)
            putFloat(KEY_PATTERN_MIN_CONFIDENCE, preferences.patternMinConfidence)
            putInt(KEY_PATTERN_TIME_WINDOW, preferences.patternTimeWindowMinutes)
            putInt(KEY_PATTERN_ANALYSIS_DAYS, preferences.patternAnalysisDays)
            // Save anomaly detection parameters
            putInt(KEY_ANOMALY_RECENT_DAYS, preferences.anomalyRecentDays)
            putInt(KEY_ANOMALY_LOOKBACK_DAYS, preferences.anomalyLookbackDays)
            putFloat(KEY_ANOMALY_DURATION_THRESHOLD, preferences.anomalyDurationThreshold)
            putInt(KEY_ANOMALY_TIME_THRESHOLD, preferences.anomalyTimeThresholdHours)
            // Save service & worker configuration
            putInt(KEY_SERVICE_HEALTH_CHECK_INTERVAL, preferences.serviceHealthCheckIntervalSeconds)
            putLong(KEY_SERVICE_STOP_GRACE_PERIOD, preferences.serviceStopGracePeriodMs)
            putInt(KEY_PERMISSION_CHECK_INTERVAL, preferences.permissionCheckIntervalSeconds)
            // Save battery & performance tuning
            putFloat(KEY_STATIONARY_INTERVAL_MULTIPLIER, preferences.stationaryIntervalMultiplier)
            putFloat(KEY_STATIONARY_DISTANCE_MULTIPLIER, preferences.stationaryDistanceMultiplier)
            putInt(KEY_FORCE_SAVE_INTERVAL_MULTIPLIER, preferences.forceSaveIntervalMultiplier)
            putInt(KEY_FORCE_SAVE_MAX_SECONDS, preferences.forceSaveMaxSeconds)
            putFloat(KEY_MIN_MOVEMENT_FOR_TIME_SAVE, preferences.minimumMovementForTimeSave)
            // Save daily summary configuration
            putInt(KEY_DAILY_SUMMARY_HOUR, preferences.dailySummaryHour)
            putBoolean(KEY_DAILY_SUMMARY_ENABLED, preferences.dailySummaryEnabled)
            // Save current settings profile
            putString(KEY_CURRENT_PROFILE, preferences.currentProfile)
            // Save place review settings (Week 5)
            putBoolean(KEY_AUTO_APPROVE_ENABLED, preferences.autoApproveEnabled)
            putFloat(KEY_AUTO_APPROVE_THRESHOLD, preferences.autoApproveThreshold)
            putBoolean(KEY_REVIEW_NOTIFICATIONS_ENABLED, preferences.reviewNotificationsEnabled)
            // Save timeline settings (Phase 3)
            putLong(KEY_TIMELINE_TIME_WINDOW, preferences.timelineTimeWindowMinutes)
            putFloat(KEY_TIMELINE_DISTANCE_THRESHOLD, preferences.timelineDistanceThresholdMeters.toFloat())
            apply()
        }
    }
    
    companion object {
        private const val PREFS_NAME = "voyager_user_preferences"
        
        // Location Tracking Keys
        private const val KEY_LOCATION_UPDATE_INTERVAL = "location_update_interval"
        private const val KEY_MIN_DISTANCE_CHANGE = "min_distance_change"
        private const val KEY_TRACKING_ACCURACY_MODE = "tracking_accuracy_mode"
        private const val KEY_LOCATION_TRACKING_ENABLED = "location_tracking_enabled"
        
        // Place Detection Keys
        private const val KEY_CLUSTERING_DISTANCE = "clustering_distance"
        private const val KEY_MIN_POINTS_FOR_CLUSTER = "min_points_for_cluster"
        private const val KEY_PLACE_DETECTION_RADIUS = "place_detection_radius"
        private const val KEY_SESSION_BREAK_TIME = "session_break_time"
        private const val KEY_MIN_VISIT_DURATION = "min_visit_duration"
        private const val KEY_AUTO_CONFIDENCE_THRESHOLD = "auto_confidence_threshold"
        
        // Place Categorization Keys
        private const val KEY_HOME_NIGHT_THRESHOLD = "home_night_threshold"
        private const val KEY_WORK_HOURS_THRESHOLD = "work_hours_threshold"
        private const val KEY_GYM_ACTIVITY_THRESHOLD = "gym_activity_threshold"
        private const val KEY_SHOPPING_MIN_DURATION = "shopping_min_duration"
        private const val KEY_SHOPPING_MAX_DURATION = "shopping_max_duration"
        private const val KEY_RESTAURANT_MEAL_THRESHOLD = "restaurant_meal_threshold"
        
        // Data Management Keys
        private const val KEY_DATA_RETENTION_DAYS = "data_retention_days"
        private const val KEY_MAX_LOCATIONS_PROCESS = "max_locations_process"
        private const val KEY_MAX_RECENT_LOCATIONS = "max_recent_locations"
        private const val KEY_DATA_MIGRATION_VERSION = "data_migration_version"
        
        // Notification Keys
        private const val KEY_ENABLE_ARRIVAL_NOTIFICATIONS = "enable_arrival_notifications"
        private const val KEY_ENABLE_DEPARTURE_NOTIFICATIONS = "enable_departure_notifications"
        private const val KEY_ENABLE_PATTERN_NOTIFICATIONS = "enable_pattern_notifications"
        private const val KEY_ENABLE_WEEKLY_SUMMARY = "enable_weekly_summary"
        private const val KEY_NOTIFICATION_UPDATE_FREQUENCY = "notification_update_frequency"
        
        // Export Keys
        private const val KEY_DEFAULT_EXPORT_FORMAT = "default_export_format"
        private const val KEY_INCLUDE_RAW_LOCATIONS = "include_raw_locations"
        private const val KEY_INCLUDE_PLACE_DATA = "include_place_data"
        private const val KEY_INCLUDE_VISIT_DATA = "include_visit_data"
        private const val KEY_INCLUDE_ANALYTICS = "include_analytics"
        
        // Privacy Keys
        private const val KEY_ENABLE_LOCATION_HISTORY = "enable_location_history"
        private const val KEY_ENABLE_PLACE_DETECTION = "enable_place_detection"
        private const val KEY_ENABLE_ANALYTICS = "enable_analytics"
        private const val KEY_ANONYMIZE_EXPORTS = "anonymize_exports"
        
        // New User-Configurable Settings Keys
        private const val KEY_MAX_GPS_ACCURACY = "max_gps_accuracy"
        private const val KEY_MAX_SPEED_KMH = "max_speed_kmh"
        private const val KEY_MIN_TIME_BETWEEN_UPDATES = "min_time_between_updates"
        private const val KEY_PLACE_DETECTION_FREQUENCY = "place_detection_frequency"
        private const val KEY_AUTO_DETECT_TRIGGER_COUNT = "auto_detect_trigger_count"
        private const val KEY_BATTERY_REQUIREMENT = "battery_requirement"
        private const val KEY_ACTIVITY_TIME_START = "activity_time_start"
        private const val KEY_ACTIVITY_TIME_END = "activity_time_end"
        private const val KEY_DATA_PROCESSING_BATCH_SIZE = "data_processing_batch_size"

        // Sleep Schedule Keys (Phase 8.1)
        private const val KEY_SLEEP_MODE_ENABLED = "sleep_mode_enabled"
        private const val KEY_SLEEP_START_HOUR = "sleep_start_hour"
        private const val KEY_SLEEP_END_HOUR = "sleep_end_hour"
        private const val KEY_SLEEP_MODE_STRICTNESS = "sleep_mode_strictness"
        // Motion detection settings keys (Phase 8.4)
        private const val KEY_MOTION_DETECTION_ENABLED = "motion_detection_enabled"
        private const val KEY_MOTION_SENSITIVITY_THRESHOLD = "motion_sensitivity_threshold"

        // Advanced Place Detection Keys
        private const val KEY_MIN_DISTANCE_BETWEEN_PLACES = "min_distance_between_places"
        private const val KEY_STATIONARY_THRESHOLD_MINUTES = "stationary_threshold_minutes"
        private const val KEY_STATIONARY_MOVEMENT_THRESHOLD = "stationary_movement_threshold"

        // Geocoding & Caching Keys
        private const val KEY_GEOCODING_CACHE_DURATION = "geocoding_cache_duration"
        private const val KEY_GEOCODING_CACHE_PRECISION = "geocoding_cache_precision"

        // UI Refresh Interval Keys
        private const val KEY_DASHBOARD_REFRESH_AT_PLACE = "dashboard_refresh_at_place"
        private const val KEY_DASHBOARD_REFRESH_TRACKING = "dashboard_refresh_tracking"
        private const val KEY_DASHBOARD_REFRESH_IDLE = "dashboard_refresh_idle"
        private const val KEY_ANALYTICS_CACHE_TIMEOUT = "analytics_cache_timeout"

        // Pattern Analysis Keys
        private const val KEY_PATTERN_MIN_VISITS = "pattern_min_visits"
        private const val KEY_PATTERN_MIN_CONFIDENCE = "pattern_min_confidence"
        private const val KEY_PATTERN_TIME_WINDOW = "pattern_time_window"
        private const val KEY_PATTERN_ANALYSIS_DAYS = "pattern_analysis_days"

        // Anomaly Detection Keys
        private const val KEY_ANOMALY_RECENT_DAYS = "anomaly_recent_days"
        private const val KEY_ANOMALY_LOOKBACK_DAYS = "anomaly_lookback_days"
        private const val KEY_ANOMALY_DURATION_THRESHOLD = "anomaly_duration_threshold"
        private const val KEY_ANOMALY_TIME_THRESHOLD = "anomaly_time_threshold"

        // Service & Worker Configuration Keys
        private const val KEY_SERVICE_HEALTH_CHECK_INTERVAL = "service_health_check_interval"
        private const val KEY_SERVICE_STOP_GRACE_PERIOD = "service_stop_grace_period"
        private const val KEY_PERMISSION_CHECK_INTERVAL = "permission_check_interval"

        // Battery & Performance Tuning Keys
        private const val KEY_STATIONARY_INTERVAL_MULTIPLIER = "stationary_interval_multiplier"
        private const val KEY_STATIONARY_DISTANCE_MULTIPLIER = "stationary_distance_multiplier"
        private const val KEY_FORCE_SAVE_INTERVAL_MULTIPLIER = "force_save_interval_multiplier"
        private const val KEY_FORCE_SAVE_MAX_SECONDS = "force_save_max_seconds"
        private const val KEY_MIN_MOVEMENT_FOR_TIME_SAVE = "min_movement_for_time_save"

        // Daily Summary Configuration Keys
        private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"
        private const val KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled"

        // Place Review Settings Keys (Week 5)
        private const val KEY_AUTO_APPROVE_ENABLED = "auto_approve_enabled"
        private const val KEY_AUTO_APPROVE_THRESHOLD = "auto_approve_threshold"
        private const val KEY_REVIEW_NOTIFICATIONS_ENABLED = "review_notifications_enabled"

        // Timeline Settings Keys (Phase 3)
        private const val KEY_TIMELINE_TIME_WINDOW = "timeline_time_window_minutes"
        private const val KEY_TIMELINE_DISTANCE_THRESHOLD = "timeline_distance_threshold_meters"

        // Settings Profile Key
        private const val KEY_CURRENT_PROFILE = "current_profile"
    }
}