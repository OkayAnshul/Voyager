package com.cosmiclaboratory.voyager.domain.model

/**
 * User preferences for location tracking and place detection
 * All values have sensible defaults that match current hardcoded values
 */
data class UserPreferences(
    // Location Tracking Settings
    val locationUpdateIntervalMs: Long = 30000L, // 30 seconds (current default)
    val minDistanceChangeMeters: Float = 10f, // 10 meters (current default)
    val trackingAccuracyMode: TrackingAccuracyMode = TrackingAccuracyMode.BALANCED,
    val isLocationTrackingEnabled: Boolean = false,
    
    // Place Detection Settings
    val clusteringDistanceMeters: Double = 50.0, // 50m (current default)
    val minPointsForCluster: Int = 3, // 3 points (current default)
    val placeDetectionRadius: Double = 100.0, // 100m (current default)
    val sessionBreakTimeMinutes: Int = 30, // 30 minutes (current default)
    val minVisitDurationMinutes: Int = 5, // 5 minutes minimum visit
    val autoConfidenceThreshold: Float = 0.7f, // Auto-accept places above this confidence
    
    // Place Categorization Thresholds
    val homeNightActivityThreshold: Float = 0.6f, // 60% night activity for home
    val workHoursActivityThreshold: Float = 0.5f, // 50% work hours for work classification
    val gymActivityThreshold: Float = 0.7f, // 70% workout time patterns
    val shoppingMinDurationMinutes: Int = 30, // Minimum shopping duration
    val shoppingMaxDurationMinutes: Int = 120, // Maximum shopping duration
    val restaurantMealTimeThreshold: Float = 0.6f, // 60% meal time activity
    
    // Location Quality Filtering Settings
    val maxGpsAccuracyMeters: Float = 100f, // Max GPS accuracy to accept (50-500m)
    val maxSpeedKmh: Double = 200.0, // Max speed to filter GPS errors (100-300 km/h)
    val minTimeBetweenUpdatesSeconds: Int = 10, // Min time gap to avoid GPS jitter (5-60s)
    
    // Place Detection Automation Settings
    val placeDetectionFrequencyHours: Int = 6, // How often to run detection (1-24 hours)
    val autoDetectTriggerCount: Int = 50, // Trigger detection after N new locations
    val batteryRequirement: BatteryRequirement = BatteryRequirement.NOT_LOW,
    
    // Analytics Calculation Settings
    val activityTimeRangeStart: Int = 6, // Start hour for activity analysis (0-23)
    val activityTimeRangeEnd: Int = 23, // End hour for activity analysis (0-23)
    val dataProcessingBatchSize: Int = 1000, // Batch size for analytics (100-5000)
    
    // Data Management
    val dataRetentionDays: Int = 365, // Keep data for 1 year by default
    val maxLocationsToProcess: Int = 10000, // Limit for performance
    val maxRecentLocationsDisplay: Int = 1000, // Map display limit
    
    // Notification Settings
    val enableArrivalNotifications: Boolean = true,
    val enableDepartureNotifications: Boolean = false,
    val enablePatternNotifications: Boolean = true,
    val enableWeeklySummary: Boolean = true,
    val notificationUpdateFrequency: Int = 10, // Update every N locations
    
    // Export Settings
    val defaultExportFormat: ExportFormat = ExportFormat.JSON,
    val includeRawLocations: Boolean = true,
    val includePlaceData: Boolean = true,
    val includeVisitData: Boolean = true,
    val includeAnalytics: Boolean = false,
    
    // Privacy Settings
    val enableLocationHistory: Boolean = true,
    val enablePlaceDetection: Boolean = true,
    val enableAnalytics: Boolean = true,
    val anonymizeExports: Boolean = false
)

enum class TrackingAccuracyMode {
    POWER_SAVE,    // Lower frequency, larger distance thresholds
    BALANCED,      // Default settings
    HIGH_ACCURACY  // Higher frequency, smaller distance thresholds
}

enum class ExportFormat {
    JSON,
    CSV,
    GPX,
    KML
}

enum class BatteryRequirement {
    ANY,           // No battery restrictions
    NOT_LOW,       // Only when battery is not low (default)
    CHARGING       // Only when device is charging
}

/**
 * Extension functions to get tracking parameters based on accuracy mode
 */
fun UserPreferences.getEffectiveUpdateInterval(): Long {
    return when (trackingAccuracyMode) {
        TrackingAccuracyMode.POWER_SAVE -> maxOf(locationUpdateIntervalMs * 2, 60000L) // At least 1 minute
        TrackingAccuracyMode.BALANCED -> locationUpdateIntervalMs
        TrackingAccuracyMode.HIGH_ACCURACY -> maxOf(locationUpdateIntervalMs / 2, 10000L) // At least 10 seconds
    }
}

fun UserPreferences.getEffectiveMinDistance(): Float {
    return when (trackingAccuracyMode) {
        TrackingAccuracyMode.POWER_SAVE -> minDistanceChangeMeters * 2f
        TrackingAccuracyMode.BALANCED -> minDistanceChangeMeters
        TrackingAccuracyMode.HIGH_ACCURACY -> minDistanceChangeMeters / 2f
    }
}

/**
 * Validation functions to ensure safe parameter values
 */
fun UserPreferences.validated(): UserPreferences {
    return copy(
        locationUpdateIntervalMs = locationUpdateIntervalMs.coerceIn(5000L, 300000L), // 5 seconds to 5 minutes
        minDistanceChangeMeters = minDistanceChangeMeters.coerceIn(1f, 100f), // 1m to 100m
        clusteringDistanceMeters = clusteringDistanceMeters.coerceIn(10.0, 500.0), // 10m to 500m
        minPointsForCluster = minPointsForCluster.coerceIn(2, 20), // 2 to 20 points
        placeDetectionRadius = placeDetectionRadius.coerceIn(10.0, 1000.0), // 10m to 1km
        sessionBreakTimeMinutes = sessionBreakTimeMinutes.coerceIn(5, 180), // 5 minutes to 3 hours
        minVisitDurationMinutes = minVisitDurationMinutes.coerceIn(1, 60), // 1 to 60 minutes
        autoConfidenceThreshold = autoConfidenceThreshold.coerceIn(0.1f, 1.0f), // 10% to 100%
        dataRetentionDays = dataRetentionDays.coerceIn(7, 3650), // 1 week to 10 years
        maxLocationsToProcess = maxLocationsToProcess.coerceIn(100, 50000), // 100 to 50k locations
        maxRecentLocationsDisplay = maxRecentLocationsDisplay.coerceIn(50, 5000), // 50 to 5k locations
        notificationUpdateFrequency = notificationUpdateFrequency.coerceIn(1, 100), // Every 1 to 100 locations
        homeNightActivityThreshold = homeNightActivityThreshold.coerceIn(0.1f, 1.0f),
        workHoursActivityThreshold = workHoursActivityThreshold.coerceIn(0.1f, 1.0f),
        gymActivityThreshold = gymActivityThreshold.coerceIn(0.1f, 1.0f),
        shoppingMinDurationMinutes = shoppingMinDurationMinutes.coerceIn(5, 60),
        shoppingMaxDurationMinutes = shoppingMaxDurationMinutes.coerceIn(30, 480), // 30 minutes to 8 hours
        restaurantMealTimeThreshold = restaurantMealTimeThreshold.coerceIn(0.1f, 1.0f),
        // New validation ranges for user-configurable settings
        maxGpsAccuracyMeters = maxGpsAccuracyMeters.coerceIn(50f, 500f), // 50m to 500m
        maxSpeedKmh = maxSpeedKmh.coerceIn(100.0, 300.0), // 100 to 300 km/h
        minTimeBetweenUpdatesSeconds = minTimeBetweenUpdatesSeconds.coerceIn(5, 60), // 5 to 60 seconds
        placeDetectionFrequencyHours = placeDetectionFrequencyHours.coerceIn(1, 24), // 1 to 24 hours
        autoDetectTriggerCount = autoDetectTriggerCount.coerceIn(10, 500), // 10 to 500 locations
        activityTimeRangeStart = activityTimeRangeStart.coerceIn(0, 23), // 0 to 23 hours
        activityTimeRangeEnd = activityTimeRangeEnd.coerceIn(0, 23), // 0 to 23 hours
        dataProcessingBatchSize = dataProcessingBatchSize.coerceIn(100, 5000) // 100 to 5000 items
    )
}