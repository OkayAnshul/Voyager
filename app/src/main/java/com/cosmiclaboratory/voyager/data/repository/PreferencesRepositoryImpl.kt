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
                dataProcessingBatchSize = sharedPreferences.getInt(KEY_DATA_PROCESSING_BATCH_SIZE, 1000)
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
    }
}