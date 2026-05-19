package com.cosmiclaboratory.voyager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.cosmiclaboratory.voyager.domain.model.SettingsPresets
import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.*
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val TRACKING_TIER = stringPreferencesKey("tracking_tier")
        val SAMPLING_PRESET = stringPreferencesKey("sampling_preset")
        val MIN_DWELL_MINUTES = intPreferencesKey("min_dwell_minutes")
        val PLACE_RADIUS_M = intPreferencesKey("place_radius_m")
        val DAY_BOUNDARY_MODE = stringPreferencesKey("day_boundary_mode")
        val HOME_TIMEZONE = stringPreferencesKey("home_timezone")
        val SLEEP_DETECTION_ENABLED = booleanPreferencesKey("sleep_detection_enabled")
        val STEP_COUNTING_ENABLED = booleanPreferencesKey("step_counting_enabled")
        val ACTIVE_PRESET = stringPreferencesKey("active_preset")
        val ACTIVE_JOB = stringPreferencesKey("active_job")
        val FLAG_SECURE_ENABLED = booleanPreferencesKey("flag_secure_enabled")
        val BATTERY_SAVER_THRESHOLD = intPreferencesKey("battery_saver_threshold")
        val AUTO_GEOCODE = booleanPreferencesKey("auto_geocode_new_places")
        val DAILY_INSIGHTS_ENABLED = booleanPreferencesKey("daily_insights_enabled")
        val WEEKLY_INSIGHTS_ENABLED = booleanPreferencesKey("weekly_insights_enabled")
        val RAW_RETENTION_DAYS = intPreferencesKey("raw_sample_retention_days")
        val SHOW_ROUTE_POLYLINES = booleanPreferencesKey("show_route_polylines")
        val SHOW_VISIT_MARKERS = booleanPreferencesKey("show_visit_markers")
        val PHOTON_SERVER_URL = stringPreferencesKey("photon_server_url")
        val PROVIDER_ORDER = stringPreferencesKey("geocoding_provider_order")
        val GEOCODE_LANGUAGE = stringPreferencesKey("geocode_language")
        val COARSEN_GEOCODE_QUERIES = booleanPreferencesKey("coarsen_geocode_queries")
        // Additional keys for settings that are configurable from UI
        val CUSTOM_SAMPLING_INTERVAL_MS = longPreferencesKey("custom_sampling_interval_ms")
        val AR_CONFIDENCE_THRESHOLD = intPreferencesKey("ar_confidence_threshold")
        val SPEED_HEURISTIC_ENABLED = booleanPreferencesKey("speed_heuristic_enabled")
        val STEP_RATE_FUSION_ENABLED = booleanPreferencesKey("step_rate_fusion_enabled")
        val SHOW_GAP_SEGMENTS = booleanPreferencesKey("show_gap_segments")
        val SHOW_LOW_CONFIDENCE_SEGMENTS = booleanPreferencesKey("show_low_confidence_segments")
        val MIN_SEGMENT_DURATION_MS = longPreferencesKey("min_segment_duration_ms")
        val ROUTE_COLOR_BY_TRANSPORT_MODE = booleanPreferencesKey("route_color_by_transport_mode")
        val VISIT_MARKER_NUMBERING = booleanPreferencesKey("visit_marker_numbering")
        val MOTION_DETECTION_ENABLED = booleanPreferencesKey("motion_detection_enabled")
        val ACTIVITY_RECOGNITION_ENABLED = booleanPreferencesKey("activity_recognition_enabled")
        val BATTERY_SAVER_THRESHOLD_PCT = intPreferencesKey("battery_saver_threshold_pct")
        val UNIFY_TRAVEL_SEGMENTS = booleanPreferencesKey("unify_travel_segments")
        // Sleep schedule
        val SLEEP_WINDOW_START_HOUR = intPreferencesKey("sleep_window_start_hour")
        val SLEEP_WINDOW_START_MINUTE = intPreferencesKey("sleep_window_start_minute")
        val SLEEP_WINDOW_END_HOUR = intPreferencesKey("sleep_window_end_hour")
        val SLEEP_WINDOW_END_MINUTE = intPreferencesKey("sleep_window_end_minute")
        val SLEEP_SAMPLING_INTERVAL_MS = longPreferencesKey("sleep_sampling_interval_ms")
        // Place detection tuning
        val ENTRY_HYSTERESIS_COUNT = intPreferencesKey("entry_hysteresis_count")
        val EXIT_HYSTERESIS_COUNT = intPreferencesKey("exit_hysteresis_count")
        val EXIT_BUFFER_M = intPreferencesKey("exit_buffer_m")
        val AUTO_DISCOVERY_ENABLED = booleanPreferencesKey("auto_discovery_enabled")
        val DISCOVERY_INTERVAL_HOURS = intPreferencesKey("discovery_interval_hours")
        // Battery
        val CHARGING_BOOST_ENABLED = booleanPreferencesKey("charging_boost_enabled")
        // Notifications
        val TRACKING_STATUS_NOTIFICATION_ENABLED = booleanPreferencesKey("tracking_status_notification_enabled")
        val ANOMALY_ALERTS_ENABLED = booleanPreferencesKey("anomaly_alerts_enabled")
        val PLACE_CONFIRMATION_PROMPTS_ENABLED = booleanPreferencesKey("place_confirmation_prompts_enabled")
        // Privacy & retention
        val STRIP_COORDINATES_ON_EXPORT = booleanPreferencesKey("strip_exact_coordinates_on_export")
        val EXPORT_INCLUDE_RAW_SAMPLES = booleanPreferencesKey("export_include_raw_samples")
        val DERIVED_DATA_RETENTION_DAYS = intPreferencesKey("derived_data_retention_days")
        val CORRECTION_FEEDBACK_RETENTION_DAYS = intPreferencesKey("correction_feedback_retention_days")
        val AUTO_CLEANUP_ENABLED = booleanPreferencesKey("auto_cleanup_enabled")
        // Map
        val CLUSTER_MARKERS_AT_ZOOM = intPreferencesKey("cluster_markers_at_zoom")
    }

    // Built once and shared — every consumer reads the same StateFlow,
    // so adding settings consumers never spawns extra DataStore collectors.
    private val settingsFlow: StateFlow<UserSettings> =
        dataStore.data.map { prefs ->
            UserSettings(
                trackingEnabled = prefs[TRACKING_ENABLED] ?: true,
                trackingTier = TrackingTier.fromName(prefs[TRACKING_TIER]),
                samplingPreset = prefs[SAMPLING_PRESET]?.let { try { SamplingPreset.valueOf(it) } catch (_: Exception) { null } } ?: SamplingPreset.BALANCED,
                customSamplingIntervalMs = prefs[CUSTOM_SAMPLING_INTERVAL_MS] ?: 15000L,
                minDwellMinutes = prefs[MIN_DWELL_MINUTES] ?: 5,
                placeRadiusM = prefs[PLACE_RADIUS_M] ?: 80,
                dayBoundaryMode = prefs[DAY_BOUNDARY_MODE]?.let { try { DayBoundaryMode.valueOf(it) } catch (_: Exception) { null } } ?: DayBoundaryMode.HOME_TIMEZONE,
                homeTimeZone = prefs[HOME_TIMEZONE] ?: java.util.TimeZone.getDefault().id,
                sleepDetectionEnabled = prefs[SLEEP_DETECTION_ENABLED] ?: true,
                stepCountingEnabled = prefs[STEP_COUNTING_ENABLED] ?: true,
                activePreset = prefs[ACTIVE_PRESET] ?: "DAILY_COMMUTER",
                activeJob = prefs[ACTIVE_JOB] ?: "",
                batterySaverThresholdPct = prefs[BATTERY_SAVER_THRESHOLD] ?: 20,
                autoGeocodeNewPlaces = prefs[AUTO_GEOCODE] ?: true,
                dailyInsightsEnabled = prefs[DAILY_INSIGHTS_ENABLED] ?: true,
                weeklyInsightsEnabled = prefs[WEEKLY_INSIGHTS_ENABLED] ?: true,
                rawSampleRetentionDays = prefs[RAW_RETENTION_DAYS] ?: 90,
                showRoutePolylines = prefs[SHOW_ROUTE_POLYLINES] ?: true,
                showVisitMarkers = prefs[SHOW_VISIT_MARKERS] ?: true,
                photonServerUrl = prefs[PHOTON_SERVER_URL] ?: "https://photon.komoot.io",
                providerOrder = prefs[PROVIDER_ORDER]
                    ?.split(",")
                    ?.mapNotNull { runCatching { GeocodingProviderId.valueOf(it.trim()) }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: UserSettings().providerOrder,
                geocodeLanguage = prefs[GEOCODE_LANGUAGE] ?: "",
                coarsenGeocodeQueries = prefs[COARSEN_GEOCODE_QUERIES] ?: false,
                arConfidenceThreshold = prefs[AR_CONFIDENCE_THRESHOLD] ?: 50,
                speedHeuristicEnabled = prefs[SPEED_HEURISTIC_ENABLED] ?: true,
                stepRateFusionEnabled = prefs[STEP_RATE_FUSION_ENABLED] ?: true,
                showGapSegments = prefs[SHOW_GAP_SEGMENTS] ?: true,
                showLowConfidenceSegments = prefs[SHOW_LOW_CONFIDENCE_SEGMENTS] ?: true,
                unifyTravelSegments = prefs[UNIFY_TRAVEL_SEGMENTS] ?: false,
                minSegmentDurationMs = prefs[MIN_SEGMENT_DURATION_MS] ?: 60000L,
                routeColorByTransportMode = prefs[ROUTE_COLOR_BY_TRANSPORT_MODE] ?: true,
                visitMarkerNumbering = prefs[VISIT_MARKER_NUMBERING] ?: true,
                motionDetectionEnabled = prefs[MOTION_DETECTION_ENABLED] ?: true,
                activityRecognitionEnabled = prefs[ACTIVITY_RECOGNITION_ENABLED] ?: true,
                // Sleep schedule
                sleepWindowStartHour = prefs[SLEEP_WINDOW_START_HOUR] ?: 23,
                sleepWindowStartMinute = prefs[SLEEP_WINDOW_START_MINUTE] ?: 0,
                sleepWindowEndHour = prefs[SLEEP_WINDOW_END_HOUR] ?: 7,
                sleepWindowEndMinute = prefs[SLEEP_WINDOW_END_MINUTE] ?: 0,
                sleepSamplingIntervalMs = prefs[SLEEP_SAMPLING_INTERVAL_MS] ?: 300000L,
                // Place detection tuning
                entryHysteresisCount = prefs[ENTRY_HYSTERESIS_COUNT] ?: 2,
                exitHysteresisCount = prefs[EXIT_HYSTERESIS_COUNT] ?: 3,
                exitBufferM = prefs[EXIT_BUFFER_M] ?: 30,
                autoDiscoveryEnabled = prefs[AUTO_DISCOVERY_ENABLED] ?: true,
                discoveryIntervalHours = prefs[DISCOVERY_INTERVAL_HOURS] ?: 6,
                // Battery
                chargingBoostEnabled = prefs[CHARGING_BOOST_ENABLED] ?: true,
                // Notifications
                trackingStatusNotificationEnabled = prefs[TRACKING_STATUS_NOTIFICATION_ENABLED] ?: true,
                anomalyAlertsEnabled = prefs[ANOMALY_ALERTS_ENABLED] ?: true,
                placeConfirmationPromptsEnabled = prefs[PLACE_CONFIRMATION_PROMPTS_ENABLED] ?: true,
                // Privacy & retention
                stripExactCoordinatesOnExport = prefs[STRIP_COORDINATES_ON_EXPORT] ?: false,
                exportIncludeRawSamples = prefs[EXPORT_INCLUDE_RAW_SAMPLES] ?: false,
                flagSecureEnabled = prefs[FLAG_SECURE_ENABLED] ?: false,
                derivedDataRetentionDays = prefs[DERIVED_DATA_RETENTION_DAYS] ?: 365,
                correctionFeedbackRetentionDays = prefs[CORRECTION_FEEDBACK_RETENTION_DAYS] ?: 180,
                autoCleanupEnabled = prefs[AUTO_CLEANUP_ENABLED] ?: true,
                // Map
                clusterMarkersAtZoom = prefs[CLUSTER_MARKERS_AT_ZOOM] ?: 12
            )
        }.stateIn(scope, SharingStarted.Eagerly, UserSettings())

    override fun observeSettings(): StateFlow<UserSettings> = settingsFlow

    override suspend fun updateSetting(key: String, value: Any): Result<Unit> = runCatching {
        // Normalize camelCase keys from UI to snake_case used by DataStore.
        // e.g. "trackingEnabled" → "tracking_enabled", "customSamplingIntervalMs" → "custom_sampling_interval_ms"
        val normalizedKey = key.replace(Regex("([a-z])([A-Z])")) {
            "${it.groupValues[1]}_${it.groupValues[2]}"
        }.lowercase()
        dataStore.edit { prefs ->
            when (normalizedKey) {
                "tracking_enabled" -> prefs[TRACKING_ENABLED] = value as Boolean
                "tracking_tier" -> prefs[TRACKING_TIER] = value as String
                "sampling_preset" -> prefs[SAMPLING_PRESET] = value as String
                "custom_sampling_interval_ms" -> prefs[CUSTOM_SAMPLING_INTERVAL_MS] = (value as Number).toLong()
                "min_dwell_minutes" -> prefs[MIN_DWELL_MINUTES] = (value as Number).toInt()
                "place_radius_m" -> prefs[PLACE_RADIUS_M] = (value as Number).toInt()
                "day_boundary_mode" -> prefs[DAY_BOUNDARY_MODE] = value as String
                "home_timezone" -> prefs[HOME_TIMEZONE] = value as String
                "sleep_detection_enabled" -> prefs[SLEEP_DETECTION_ENABLED] = value as Boolean
                "step_counting_enabled" -> prefs[STEP_COUNTING_ENABLED] = value as Boolean
                "active_preset" -> prefs[ACTIVE_PRESET] = value as String
                "active_job" -> prefs[ACTIVE_JOB] = value as String
                "battery_saver_threshold", "battery_saver_threshold_pct" -> prefs[BATTERY_SAVER_THRESHOLD] = (value as Number).toInt()
                "auto_geocode_new_places" -> prefs[AUTO_GEOCODE] = value as Boolean
                "daily_insights_enabled" -> prefs[DAILY_INSIGHTS_ENABLED] = value as Boolean
                "weekly_insights_enabled" -> prefs[WEEKLY_INSIGHTS_ENABLED] = value as Boolean
                "raw_sample_retention_days" -> prefs[RAW_RETENTION_DAYS] = (value as Number).toInt()
                "show_route_polylines" -> prefs[SHOW_ROUTE_POLYLINES] = value as Boolean
                "show_visit_markers" -> prefs[SHOW_VISIT_MARKERS] = value as Boolean
                "photon_server_url" -> prefs[PHOTON_SERVER_URL] = value as String
                "provider_order" -> (value as? List<*>)?.let { list ->
                    prefs[PROVIDER_ORDER] = list
                        .filterIsInstance<GeocodingProviderId>()
                        .joinToString(",") { it.name }
                }
                "geocode_language" -> prefs[GEOCODE_LANGUAGE] = value as String
                "coarsen_geocode_queries" -> prefs[COARSEN_GEOCODE_QUERIES] = value as Boolean
                // New keys wired from UI
                "ar_confidence_threshold" -> prefs[AR_CONFIDENCE_THRESHOLD] = (value as Number).toInt()
                "speed_heuristic_enabled" -> prefs[SPEED_HEURISTIC_ENABLED] = value as Boolean
                "step_rate_fusion_enabled" -> prefs[STEP_RATE_FUSION_ENABLED] = value as Boolean
                "show_gap_segments" -> prefs[SHOW_GAP_SEGMENTS] = value as Boolean
                "show_low_confidence_segments" -> prefs[SHOW_LOW_CONFIDENCE_SEGMENTS] = value as Boolean
                "unify_travel_segments" -> prefs[UNIFY_TRAVEL_SEGMENTS] = value as Boolean
                "min_segment_duration_ms" -> prefs[MIN_SEGMENT_DURATION_MS] = (value as Number).toLong()
                "route_color_by_transport_mode" -> prefs[ROUTE_COLOR_BY_TRANSPORT_MODE] = value as Boolean
                "visit_marker_numbering" -> prefs[VISIT_MARKER_NUMBERING] = value as Boolean
                "motion_detection_enabled" -> prefs[MOTION_DETECTION_ENABLED] = value as Boolean
                "activity_recognition_enabled" -> prefs[ACTIVITY_RECOGNITION_ENABLED] = value as Boolean
                // Sleep schedule
                "sleep_window_start_hour" -> prefs[SLEEP_WINDOW_START_HOUR] = (value as Number).toInt()
                "sleep_window_start_minute" -> prefs[SLEEP_WINDOW_START_MINUTE] = (value as Number).toInt()
                "sleep_window_end_hour" -> prefs[SLEEP_WINDOW_END_HOUR] = (value as Number).toInt()
                "sleep_window_end_minute" -> prefs[SLEEP_WINDOW_END_MINUTE] = (value as Number).toInt()
                "sleep_sampling_interval_ms" -> prefs[SLEEP_SAMPLING_INTERVAL_MS] = (value as Number).toLong()
                // Place detection tuning
                "entry_hysteresis_count" -> prefs[ENTRY_HYSTERESIS_COUNT] = (value as Number).toInt()
                "exit_hysteresis_count" -> prefs[EXIT_HYSTERESIS_COUNT] = (value as Number).toInt()
                "exit_buffer_m" -> prefs[EXIT_BUFFER_M] = (value as Number).toInt()
                "auto_discovery_enabled" -> prefs[AUTO_DISCOVERY_ENABLED] = value as Boolean
                "discovery_interval_hours" -> prefs[DISCOVERY_INTERVAL_HOURS] = (value as Number).toInt()
                // Battery
                "charging_boost_enabled" -> prefs[CHARGING_BOOST_ENABLED] = value as Boolean
                // Notifications
                "tracking_status_notification_enabled" -> prefs[TRACKING_STATUS_NOTIFICATION_ENABLED] = value as Boolean
                "anomaly_alerts_enabled" -> prefs[ANOMALY_ALERTS_ENABLED] = value as Boolean
                "place_confirmation_prompts_enabled" -> prefs[PLACE_CONFIRMATION_PROMPTS_ENABLED] = value as Boolean
                // Privacy & retention
                "strip_exact_coordinates_on_export" -> prefs[STRIP_COORDINATES_ON_EXPORT] = value as Boolean
                "export_include_raw_samples" -> prefs[EXPORT_INCLUDE_RAW_SAMPLES] = value as Boolean
                "flag_secure_enabled" -> prefs[FLAG_SECURE_ENABLED] = value as Boolean
                "derived_data_retention_days" -> prefs[DERIVED_DATA_RETENTION_DAYS] = (value as Number).toInt()
                "correction_feedback_retention_days" -> prefs[CORRECTION_FEEDBACK_RETENTION_DAYS] = (value as Number).toInt()
                "auto_cleanup_enabled" -> prefs[AUTO_CLEANUP_ENABLED] = value as Boolean
                // Map
                "cluster_markers_at_zoom" -> prefs[CLUSTER_MARKERS_AT_ZOOM] = (value as Number).toInt()
            }
        }
    }

    override suspend fun applyPreset(presetId: String): Result<Unit> = runCatching {
        val preset = SettingsPresets.forId(presetId)
            ?: throw IllegalArgumentException("Unknown preset: $presetId")
        // A preset comprehensively reconfigures every tracking-behaviour setting,
        // so switching presets fully changes how the app captures and detects.
        dataStore.edit { prefs ->
            prefs[ACTIVE_PRESET] = presetId
            prefs[SAMPLING_PRESET] = preset.samplingPreset.name
            prefs[CUSTOM_SAMPLING_INTERVAL_MS] = preset.customSamplingIntervalMs
            prefs[MIN_DWELL_MINUTES] = preset.minDwellMinutes
            prefs[PLACE_RADIUS_M] = preset.placeRadiusM
            prefs[ENTRY_HYSTERESIS_COUNT] = preset.entryHysteresisCount
            prefs[EXIT_HYSTERESIS_COUNT] = preset.exitHysteresisCount
            prefs[EXIT_BUFFER_M] = preset.exitBufferM
            prefs[AUTO_DISCOVERY_ENABLED] = preset.autoDiscoveryEnabled
            prefs[DISCOVERY_INTERVAL_HOURS] = preset.discoveryIntervalHours
            prefs[MOTION_DETECTION_ENABLED] = preset.motionDetectionEnabled
            prefs[ACTIVITY_RECOGNITION_ENABLED] = preset.activityRecognitionEnabled
            prefs[STEP_COUNTING_ENABLED] = preset.stepCountingEnabled
            prefs[AR_CONFIDENCE_THRESHOLD] = preset.arConfidenceThreshold
            prefs[SPEED_HEURISTIC_ENABLED] = preset.speedHeuristicEnabled
            prefs[STEP_RATE_FUSION_ENABLED] = preset.stepRateFusionEnabled
            prefs[SLEEP_DETECTION_ENABLED] = preset.sleepDetectionEnabled
            prefs[SLEEP_WINDOW_START_HOUR] = preset.sleepWindowStartHour
            prefs[SLEEP_WINDOW_START_MINUTE] = preset.sleepWindowStartMinute
            prefs[SLEEP_WINDOW_END_HOUR] = preset.sleepWindowEndHour
            prefs[SLEEP_WINDOW_END_MINUTE] = preset.sleepWindowEndMinute
            prefs[SLEEP_SAMPLING_INTERVAL_MS] = preset.sleepSamplingIntervalMs
            prefs[BATTERY_SAVER_THRESHOLD] = preset.batterySaverThresholdPct
            prefs[CHARGING_BOOST_ENABLED] = preset.chargingBoostEnabled
            prefs[DAILY_INSIGHTS_ENABLED] = preset.dailyInsightsEnabled
            prefs[WEEKLY_INSIGHTS_ENABLED] = preset.weeklyInsightsEnabled
            prefs[RAW_RETENTION_DAYS] = preset.rawSampleRetentionDays
            prefs[DERIVED_DATA_RETENTION_DAYS] = preset.derivedDataRetentionDays
            prefs[AUTO_GEOCODE] = preset.autoGeocodeNewPlaces
            prefs[SHOW_ROUTE_POLYLINES] = preset.showRoutePolylines
            prefs[SHOW_VISIT_MARKERS] = preset.showVisitMarkers
        }
    }

    override suspend fun exportSettings(): Result<String> = runCatching {
        val prefs = dataStore.data.first()
        prefs.asMap().entries.joinToString("\n") { "${it.key.name}=${it.value}" }
    }

    override suspend fun importSettings(json: String): Result<Unit> = runCatching {
        val lines = json.lines().filter { it.contains("=") }
        // Atomic batch write — partial import on crash is impossible
        dataStore.edit { prefs ->
            for (line in lines) {
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                when {
                    value == "true" || value == "false" -> applyPref(prefs, key, value.toBoolean())
                    value.toIntOrNull() != null -> applyPref(prefs, key, value.toInt())
                    else -> applyPref(prefs, key, value)
                }
            }
        }
    }

    private fun applyPref(prefs: MutablePreferences, key: String, value: Any) {
        when (key) {
            "tracking_enabled" -> prefs[TRACKING_ENABLED] = value as Boolean
            "sampling_preset" -> prefs[SAMPLING_PRESET] = value as String
            "min_dwell_minutes" -> prefs[MIN_DWELL_MINUTES] = value as Int
            "place_radius_m" -> prefs[PLACE_RADIUS_M] = value as Int
            "day_boundary_mode" -> prefs[DAY_BOUNDARY_MODE] = value as String
            "home_timezone" -> prefs[HOME_TIMEZONE] = value as String
            "sleep_detection_enabled" -> prefs[SLEEP_DETECTION_ENABLED] = value as Boolean
            "step_counting_enabled" -> prefs[STEP_COUNTING_ENABLED] = value as Boolean
            "active_preset" -> prefs[ACTIVE_PRESET] = value as String
            "active_job" -> prefs[ACTIVE_JOB] = value as String
            "battery_saver_threshold" -> prefs[BATTERY_SAVER_THRESHOLD] = value as Int
            "auto_geocode_new_places" -> prefs[AUTO_GEOCODE] = value as Boolean
            "daily_insights_enabled" -> prefs[DAILY_INSIGHTS_ENABLED] = value as Boolean
            "weekly_insights_enabled" -> prefs[WEEKLY_INSIGHTS_ENABLED] = value as Boolean
            "raw_sample_retention_days" -> prefs[RAW_RETENTION_DAYS] = value as Int
            "show_route_polylines" -> prefs[SHOW_ROUTE_POLYLINES] = value as Boolean
            "show_visit_markers" -> prefs[SHOW_VISIT_MARKERS] = value as Boolean
            "photon_server_url" -> prefs[PHOTON_SERVER_URL] = value as String
        }
    }
}
