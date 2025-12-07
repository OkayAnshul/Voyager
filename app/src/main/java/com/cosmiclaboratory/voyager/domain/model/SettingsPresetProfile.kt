package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.TrackingAccuracyMode

/**
 * Represents a preset configuration profile for the app settings.
 * Presets allow users to quickly switch between optimized configurations
 * for different use cases without manually adjusting individual parameters.
 */
enum class SettingsPresetProfile(
    val displayName: String,
    val description: String,
    val batteryImpact: BatteryImpact,
    val accuracyLevel: AccuracyLevel
) {
    /**
     * Battery Saver profile - Optimized for maximum battery life
     * Best for: Users who prioritize battery over detailed tracking
     */
    BATTERY_SAVER(
        displayName = "Battery Saver",
        description = "Maximum battery life with basic tracking",
        batteryImpact = BatteryImpact.LOW,
        accuracyLevel = AccuracyLevel.BASIC
    ),

    /**
     * Daily Commuter profile - Balanced settings for routine tracking
     * Best for: Users with regular routines (home-work-frequent places)
     */
    DAILY_COMMUTER(
        displayName = "Daily Commuter",
        description = "Optimized for detecting regular routines and places",
        batteryImpact = BatteryImpact.MODERATE,
        accuracyLevel = AccuracyLevel.BALANCED
    ),

    /**
     * Traveler profile - High accuracy for detailed journey tracking
     * Best for: Users who want detailed tracking and precise place detection
     */
    TRAVELER(
        displayName = "Traveler",
        description = "Detailed tracking with frequent updates and tight clustering",
        batteryImpact = BatteryImpact.HIGH,
        accuracyLevel = AccuracyLevel.PRECISE
    ),

    /**
     * Custom profile - User has manually configured settings
     * Automatically set when user modifies individual parameters
     */
    CUSTOM(
        displayName = "Custom",
        description = "Your personalized settings configuration",
        batteryImpact = BatteryImpact.UNKNOWN,
        accuracyLevel = AccuracyLevel.CUSTOM
    );

    /**
     * Converts this profile to a UserPreferences object with optimized values
     */
    fun toUserPreferences(basePreferences: UserPreferences = UserPreferences()): UserPreferences {
        return when (this) {
            BATTERY_SAVER -> basePreferences.copy(
                // Location tracking - Minimal updates
                locationUpdateIntervalMs = 60000L, // 60 seconds
                minDistanceChangeMeters = 50.0f,
                trackingAccuracyMode = TrackingAccuracyMode.POWER_SAVE,

                // Location quality - Relaxed
                maxGpsAccuracyMeters = 150f,
                maxSpeedKmh = 200.0,
                minTimeBetweenUpdatesSeconds = 30,

                // Place detection - Coarse clustering
                clusteringDistanceMeters = 100.0,
                minPointsForCluster = 5,
                placeDetectionRadius = 200.0,
                sessionBreakTimeMinutes = 60,
                minVisitDurationMinutes = 10,
                autoConfidenceThreshold = 0.8f,

                // Place categorization - Relaxed thresholds
                homeNightActivityThreshold = 0.5f,
                workHoursActivityThreshold = 0.4f,
                gymActivityThreshold = 0.6f,
                shoppingMinDurationMinutes = 45,
                shoppingMaxDurationMinutes = 180,
                restaurantMealTimeThreshold = 0.5f,

                // Detection automation - Infrequent
                placeDetectionFrequencyHours = 24,
                autoDetectTriggerCount = 50,
                autoDetectBatteryThreshold = 30,
                workerEnqueueTimeoutSeconds = 30,

                // Analytics - Reduced scope
                activityTimeRangeStart = 6,
                activityTimeRangeEnd = 23,
                dataProcessingBatchSize = 2000,

                // Data management - Aggressive cleanup
                dataRetentionDays = 180,
                maxLocationsToProcess = 5000,
                maxRecentLocationsDisplay = 500,

                // Notifications - Minimal
                enableArrivalNotifications = false,
                enableDepartureNotifications = false,
                enablePatternNotifications = false,
                enableWeeklySummary = true,
                notificationUpdateFrequency = 50,

                // Export - Minimal data
                includeRawLocations = false,
                includePlaceData = true,
                includeVisitData = true,
                includeAnalytics = false,

                // Sleep mode - Enabled by default
                sleepModeEnabled = true,
                sleepStartHour = 22,
                sleepEndHour = 6,

                // Motion detection - Disabled to save battery
                motionDetectionEnabled = false,

                // Privacy - Standard
                enableLocationHistory = true,
                enableAnalytics = true,
                anonymizeExports = false
            )

            DAILY_COMMUTER -> basePreferences.copy(
                // Location tracking - Moderate updates
                locationUpdateIntervalMs = 30000L, // 30 seconds (default)
                minDistanceChangeMeters = 10.0f,
                trackingAccuracyMode = TrackingAccuracyMode.BALANCED,

                // Location quality - Moderate
                maxGpsAccuracyMeters = 100f,
                maxSpeedKmh = 200.0,
                minTimeBetweenUpdatesSeconds = 10,

                // Place detection - Default clustering
                clusteringDistanceMeters = 50.0,
                minPointsForCluster = 3,
                placeDetectionRadius = 100.0,
                sessionBreakTimeMinutes = 30,
                minVisitDurationMinutes = 5,
                autoConfidenceThreshold = 0.7f,

                // Place categorization - Standard thresholds
                homeNightActivityThreshold = 0.6f,
                workHoursActivityThreshold = 0.5f,
                gymActivityThreshold = 0.7f,
                shoppingMinDurationMinutes = 30,
                shoppingMaxDurationMinutes = 120,
                restaurantMealTimeThreshold = 0.6f,

                // Detection automation - Regular
                placeDetectionFrequencyHours = 6,
                autoDetectTriggerCount = 25,
                autoDetectBatteryThreshold = 20,
                workerEnqueueTimeoutSeconds = 15,

                // Analytics - Full scope
                activityTimeRangeStart = 6,
                activityTimeRangeEnd = 23,
                dataProcessingBatchSize = 1000,

                // Data management - Standard retention
                dataRetentionDays = 365,
                maxLocationsToProcess = 10000,
                maxRecentLocationsDisplay = 1000,

                // Notifications - Selective
                enableArrivalNotifications = true,
                enableDepartureNotifications = false,
                enablePatternNotifications = true,
                enableWeeklySummary = true,
                notificationUpdateFrequency = 10,

                // Export - Standard data
                includeRawLocations = true,
                includePlaceData = true,
                includeVisitData = true,
                includeAnalytics = false,

                // Sleep mode - Enabled for battery savings
                sleepModeEnabled = true,
                sleepStartHour = 22,
                sleepEndHour = 6,

                // Motion detection - Enabled
                motionDetectionEnabled = true,
                motionSensitivityThreshold = 0.5f,

                // Privacy - Standard
                enableLocationHistory = true,
                enableAnalytics = true,
                anonymizeExports = false
            )

            TRAVELER -> basePreferences.copy(
                // Location tracking - Frequent updates
                locationUpdateIntervalMs = 15000L, // 15 seconds
                minDistanceChangeMeters = 5.0f,
                trackingAccuracyMode = TrackingAccuracyMode.HIGH_ACCURACY,

                // Location quality - Strict
                maxGpsAccuracyMeters = 50f,
                maxSpeedKmh = 250.0,
                minTimeBetweenUpdatesSeconds = 5,

                // Place detection - Tight clustering
                clusteringDistanceMeters = 25.0,
                minPointsForCluster = 2,
                placeDetectionRadius = 50.0,
                sessionBreakTimeMinutes = 15,
                minVisitDurationMinutes = 3,
                autoConfidenceThreshold = 0.6f,

                // Place categorization - Sensitive thresholds
                homeNightActivityThreshold = 0.7f,
                workHoursActivityThreshold = 0.6f,
                gymActivityThreshold = 0.8f,
                shoppingMinDurationMinutes = 15,
                shoppingMaxDurationMinutes = 180,
                restaurantMealTimeThreshold = 0.7f,

                // Detection automation - Frequent
                placeDetectionFrequencyHours = 3,
                autoDetectTriggerCount = 15,
                autoDetectBatteryThreshold = 15,
                workerEnqueueTimeoutSeconds = 10,

                // Analytics - Maximum scope
                activityTimeRangeStart = 5,
                activityTimeRangeEnd = 24,
                dataProcessingBatchSize = 500,

                // Data management - Extended retention
                dataRetentionDays = 730, // 2 years
                maxLocationsToProcess = 20000,
                maxRecentLocationsDisplay = 2000,

                // Notifications - All enabled
                enableArrivalNotifications = true,
                enableDepartureNotifications = true,
                enablePatternNotifications = true,
                enableWeeklySummary = true,
                notificationUpdateFrequency = 5,

                // Export - Full data
                includeRawLocations = true,
                includePlaceData = true,
                includeVisitData = true,
                includeAnalytics = true,

                // Sleep mode - Disabled for 24/7 tracking
                sleepModeEnabled = false,
                sleepStartHour = 22,
                sleepEndHour = 6,

                // Motion detection - Enabled with high sensitivity
                motionDetectionEnabled = true,
                motionSensitivityThreshold = 0.3f,

                // Privacy - Full history
                enableLocationHistory = true,
                enableAnalytics = true,
                anonymizeExports = false
            )

            CUSTOM -> basePreferences // Return preferences as-is for custom profile
        }
    }

    /**
     * Gets a summary of key differences from default for display
     */
    fun getKeyFeatures(): List<String> {
        return when (this) {
            BATTERY_SAVER -> listOf(
                "60s location updates",
                "100m place clustering",
                "24h detection interval",
                "Power save GPS mode",
                "Sleep mode enabled"
            )
            DAILY_COMMUTER -> listOf(
                "30s location updates",
                "50m place clustering",
                "6h detection interval",
                "Balanced GPS mode",
                "Pattern notifications"
            )
            TRAVELER -> listOf(
                "15s location updates",
                "25m precise clustering",
                "3h detection interval",
                "High accuracy GPS",
                "All notifications enabled"
            )
            CUSTOM -> listOf(
                "Personalized settings",
                "Individually configured",
                "Your preferences"
            )
        }
    }

    companion object {
        /**
         * Detects which profile best matches the given preferences.
         * Returns CUSTOM if no exact match is found.
         */
        fun fromPreferences(preferences: UserPreferences): SettingsPresetProfile {
            return values()
                .filter { it != CUSTOM }
                .firstOrNull { it.matchesPreferences(preferences) }
                ?: CUSTOM
        }
    }

    /**
     * Checks if this profile matches the given preferences
     */
    private fun matchesPreferences(preferences: UserPreferences): Boolean {
        val profilePrefs = toUserPreferences()

        // Check key parameters that define each profile
        return preferences.locationUpdateIntervalMs == profilePrefs.locationUpdateIntervalMs &&
               preferences.clusteringDistanceMeters == profilePrefs.clusteringDistanceMeters &&
               preferences.trackingAccuracyMode == profilePrefs.trackingAccuracyMode &&
               preferences.placeDetectionFrequencyHours == profilePrefs.placeDetectionFrequencyHours &&
               preferences.minDistanceChangeMeters == profilePrefs.minDistanceChangeMeters
    }
}

/**
 * Battery impact level for a settings profile
 */
enum class BatteryImpact {
    LOW,        // Minimal battery drain
    MODERATE,   // Balanced battery usage
    HIGH,       // Higher battery consumption
    UNKNOWN     // Custom configuration
}

/**
 * Accuracy level for place detection
 */
enum class AccuracyLevel {
    BASIC,      // Coarse clustering, less precise
    BALANCED,   // Moderate precision
    PRECISE,    // Tight clustering, high precision
    CUSTOM      // User-defined accuracy
}
