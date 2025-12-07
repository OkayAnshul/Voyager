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
    val clusteringDistanceMeters: Double = 100.0, // 100m (increased from 50m for better large building detection)
    val minPointsForCluster: Int = 4, // 4 points (increased from 3 for more stable clusters)
    val placeDetectionRadius: Double = 150.0, // 150m (increased from 100m for better proximity checks)
    val sessionBreakTimeMinutes: Int = 30, // 30 minutes (current default)
    val minVisitDurationMinutes: Int = 5, // 5 minutes minimum visit
    val minDwellTimeSeconds: Int = 60, // 60 seconds dwell time before confirming visit (NEW)
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
    val autoDetectTriggerCount: Int = 25, // CRITICAL FIX: Trigger detection after 25 locations (was 50)
    val batteryRequirement: BatteryRequirement = BatteryRequirement.ANY,
    val workerEnqueueTimeoutSeconds: Int = 15, // Max time to wait for worker to start (5-60 seconds)
    
    // Analytics Calculation Settings
    val activityTimeRangeStart: Int = 6, // Start hour for activity analysis (0-23)
    val activityTimeRangeEnd: Int = 23, // End hour for activity analysis (0-23)
    val dataProcessingBatchSize: Int = 1000, // Batch size for analytics (100-5000)
    
    // Data Management
    val dataRetentionDays: Int = 365, // Keep data for 1 year by default
    val maxLocationsToProcess: Int = 10000, // Limit for performance
    val maxRecentLocationsDisplay: Int = 1000, // Map display limit
    val dataMigrationVersion: Int = 0, // Current data migration version
    
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
    val anonymizeExports: Boolean = false,

    // Sleep Schedule Settings (Phase 8.1)
    val sleepModeEnabled: Boolean = false,
    val sleepStartHour: Int = 22,  // 10 PM default
    val sleepEndHour: Int = 6,     // 6 AM default
    val sleepModeStrictness: SleepModeStrictness = SleepModeStrictness.NORMAL,

    // Motion Detection Settings (Phase 8.4)
    val motionDetectionEnabled: Boolean = true,  // Resume tracking if motion detected during sleep
    val motionSensitivityThreshold: Float = 0.5f,  // 0.0 (high) to 1.0 (low)

    // Activity Recognition Settings (Phase 2)
    val useActivityRecognition: Boolean = true,  // Use hybrid activity recognition to prevent false detections while driving

    // Advanced Place Detection Parameters (Previously Hardcoded)
    val minimumDistanceBetweenPlaces: Float = 50f, // Minimum meters between distinct places (increased from 25m to 50m for better duplicate prevention)
    val stationaryThresholdMinutes: Int = 5, // Time stationary before adaptive mode (3-15 min)
    val stationaryMovementThreshold: Float = 20f, // Movement threshold for stationary detection (10-50m)

    // Geocoding & Caching Parameters
    val geocodingCacheDurationDays: Int = 30, // Cache reverse geocoding results (7-90 days)
    val geocodingCachePrecision: Int = 3, // Decimal places for cache key (2=~1km, 3=~111m, 4=~11m)

    // UI Refresh Intervals
    val dashboardRefreshAtPlaceSeconds: Int = 30, // Dashboard refresh when at a place (10-60s)
    val dashboardRefreshTrackingSeconds: Int = 60, // Dashboard refresh while tracking (30-120s)
    val dashboardRefreshIdleSeconds: Int = 120, // Dashboard refresh when idle (60-300s)
    val analyticsCacheTimeoutSeconds: Int = 30, // Analytics cache duration (15-120s)

    // Pattern Analysis Parameters
    val patternMinVisits: Int = 3, // Minimum visits to establish pattern (2-10)
    val patternMinConfidence: Float = 0.3f, // Minimum confidence for pattern detection (0.1-0.8)
    val patternTimeWindowMinutes: Int = 60, // Time window for pattern matching (15-180 min)
    val patternAnalysisDays: Int = 90, // Days to analyze for patterns (30-365)

    // Anomaly Detection Parameters
    val anomalyRecentDays: Int = 14, // Recent period to check for anomalies (7-30)
    val anomalyLookbackDays: Int = 90, // Historical period for baseline (30-365)
    val anomalyDurationThreshold: Float = 0.5f, // % deviation for duration anomalies (0.3-1.0)
    val anomalyTimeThresholdHours: Int = 3, // Hours deviation for time anomalies (1-6)

    // Service & Worker Configuration
    val serviceHealthCheckIntervalSeconds: Int = 30, // Location service health check (15-120s)
    val serviceStopGracePeriodMs: Long = 5000L, // Grace period before stopping service (3000-10000ms)
    val permissionCheckIntervalSeconds: Int = 60, // Permission recheck interval (30-300s)
    val workerProgressiveCheckMs: List<Long> = listOf(500, 1000, 2000, 5000, 10000), // Progressive check delays

    // Battery & Performance Tuning
    val autoDetectBatteryThreshold: Int = 20, // Minimum battery % for auto-detection (10-50%)
    val stationaryIntervalMultiplier: Float = 2.0f, // Multiplier for stationary intervals (1.5-3.0x)
    val stationaryDistanceMultiplier: Float = 1.5f, // Multiplier for stationary distance (1.5-3.0x)
    val forceSaveIntervalMultiplier: Int = 4, // Force save after N intervals missed (2-6x)
    val forceSaveMaxSeconds: Int = 600, // Maximum seconds before force save (300-1200s)
    val minimumMovementForTimeSave: Float = 3.0f, // Minimum movement for time-based save (1.0-10.0m)

    // Daily Summary Configuration
    val dailySummaryHour: Int = 9, // Hour to generate daily summary (0-23)
    val dailySummaryEnabled: Boolean = true, // Enable daily summary worker

    // Current Settings Profile
    val currentProfile: String = "DAILY_COMMUTER", // Active settings profile (BATTERY_SAVER, DAILY_COMMUTER, TRAVELER, CUSTOM)

    // Auto-Accept Review System (Week 2)
    val autoAcceptStrategy: AutoAcceptStrategy = AutoAcceptStrategy.HIGH_CONFIDENCE_ONLY,
    val autoAcceptConfidenceThreshold: Float = 0.75f, // Threshold for high-confidence auto-accept (0.5-0.95)
    val threeVisitAutoAcceptVisitCount: Int = 3, // Number of visits before auto-accept in AFTER_N_VISITS mode (2-10)
    val reviewPromptMode: ReviewPromptMode = ReviewPromptMode.NOTIFICATION_ONLY,
    val disabledCategories: Set<PlaceCategory> = emptySet(), // Categories to completely disable
    val alwaysReviewCategories: Set<PlaceCategory> = emptySet(), // Categories that always require review

    // Place Review UI Settings (Week 5)
    val autoApproveEnabled: Boolean = true, // Enable auto-approval of high confidence places
    val autoApproveThreshold: Float = 0.85f, // Threshold for auto-approval (0.5-0.95)
    val reviewNotificationsEnabled: Boolean = true, // Enable notifications for place reviews

    // Timeline Settings (Phase 2: Week 3)
    val timelineTimeWindowMinutes: Long = 30, // Group visits within 30 minutes (15/30/60)
    val timelineDistanceThresholdMeters: Double = 200.0 // Distance threshold for future use
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

enum class SleepModeStrictness {
    RELAXED,      // Pause tracking but listen for motion (future)
    NORMAL,       // Don't track during sleep hours
    STRICT        // Pause completely, disable all background work (future)
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
        minDwellTimeSeconds = minDwellTimeSeconds.coerceIn(10, 300), // 10 to 300 seconds (NEW)
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
        dataProcessingBatchSize = dataProcessingBatchSize.coerceIn(100, 5000), // 100 to 5000 items
        sleepStartHour = sleepStartHour.coerceIn(0, 23), // 0 to 23 hours
        sleepEndHour = sleepEndHour.coerceIn(0, 23), // 0 to 23 hours
        motionSensitivityThreshold = motionSensitivityThreshold.coerceIn(0.0f, 1.0f), // 0.0 (high) to 1.0 (low)

        // Advanced Parameters Validation
        minimumDistanceBetweenPlaces = minimumDistanceBetweenPlaces.coerceIn(10f, 100f),
        stationaryThresholdMinutes = stationaryThresholdMinutes.coerceIn(3, 15),
        stationaryMovementThreshold = stationaryMovementThreshold.coerceIn(10f, 50f),
        geocodingCacheDurationDays = geocodingCacheDurationDays.coerceIn(7, 90),
        geocodingCachePrecision = geocodingCachePrecision.coerceIn(2, 4),
        dashboardRefreshAtPlaceSeconds = dashboardRefreshAtPlaceSeconds.coerceIn(10, 60),
        dashboardRefreshTrackingSeconds = dashboardRefreshTrackingSeconds.coerceIn(30, 120),
        dashboardRefreshIdleSeconds = dashboardRefreshIdleSeconds.coerceIn(60, 300),
        analyticsCacheTimeoutSeconds = analyticsCacheTimeoutSeconds.coerceIn(15, 120),
        patternMinVisits = patternMinVisits.coerceIn(2, 10),
        patternMinConfidence = patternMinConfidence.coerceIn(0.1f, 0.8f),
        patternTimeWindowMinutes = patternTimeWindowMinutes.coerceIn(15, 180),
        patternAnalysisDays = patternAnalysisDays.coerceIn(30, 365),
        anomalyRecentDays = anomalyRecentDays.coerceIn(7, 30),
        anomalyLookbackDays = anomalyLookbackDays.coerceIn(30, 365),
        anomalyDurationThreshold = anomalyDurationThreshold.coerceIn(0.3f, 1.0f),
        anomalyTimeThresholdHours = anomalyTimeThresholdHours.coerceIn(1, 6),
        serviceHealthCheckIntervalSeconds = serviceHealthCheckIntervalSeconds.coerceIn(15, 120),
        serviceStopGracePeriodMs = serviceStopGracePeriodMs.coerceIn(3000L, 10000L),
        permissionCheckIntervalSeconds = permissionCheckIntervalSeconds.coerceIn(30, 300),
        autoDetectBatteryThreshold = autoDetectBatteryThreshold.coerceIn(10, 50),
        stationaryIntervalMultiplier = stationaryIntervalMultiplier.coerceIn(1.5f, 3.0f),
        stationaryDistanceMultiplier = stationaryDistanceMultiplier.coerceIn(1.5f, 3.0f),
        forceSaveIntervalMultiplier = forceSaveIntervalMultiplier.coerceIn(2, 6),
        forceSaveMaxSeconds = forceSaveMaxSeconds.coerceIn(300, 1200),
        minimumMovementForTimeSave = minimumMovementForTimeSave.coerceIn(1.0f, 10.0f),
        dailySummaryHour = dailySummaryHour.coerceIn(0, 23),

        // Auto-Accept System Validation
        autoAcceptConfidenceThreshold = autoAcceptConfidenceThreshold.coerceIn(0.5f, 0.95f),
        threeVisitAutoAcceptVisitCount = threeVisitAutoAcceptVisitCount.coerceIn(2, 10),

        // Place Review UI Validation (Week 5)
        autoApproveThreshold = autoApproveThreshold.coerceIn(0.5f, 0.95f),

        // Timeline Settings Validation (Phase 2)
        timelineTimeWindowMinutes = timelineTimeWindowMinutes.coerceIn(15L, 60L), // 15, 30, or 60 minutes
        timelineDistanceThresholdMeters = timelineDistanceThresholdMeters.coerceIn(50.0, 1000.0) // 50m to 1km
    )
}