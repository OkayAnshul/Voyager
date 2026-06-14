package com.cosmiclaboratory.voyager.data.repository

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Pure codec + typed registry for the `key=value` settings backup format produced by
 * `SettingsRepositoryImpl.exportSettings` and consumed by `importSettings`.
 *
 * Typing is driven by the **key** ([KEY_TYPES]), never the value's shape — a string setting
 * whose value looks boolean/numeric (e.g. a timezone of "0") can't be mis-typed and crash the
 * import. [applyTo] reconstructs each DataStore key from its name + type via the standard
 * factories (a `Preferences.Key` is equal by name+type, so this writes the very same entry the
 * repository declares) — so the full settings surface round-trips without a hand-maintained
 * 57-case `when`. Unknown keys / unparseable values are skipped, never fatal.
 */
internal object SettingsBackup {

    enum class Type { BOOL, INT, LONG, STRING }

    /** Every backup-able setting key and how to parse it. Mirrors the keys in
     *  `SettingsRepositoryImpl` — a backup→restore round trip covers all of them. */
    val KEY_TYPES: Map<String, Type> = buildMap {
        // ── Boolean ──
        listOf(
            "tracking_enabled", "tracking_status_notification_enabled", "sleep_detection_enabled",
            "step_counting_enabled", "step_rate_fusion_enabled", "speed_heuristic_enabled",
            "motion_detection_enabled", "activity_recognition_enabled", "auto_discovery_enabled",
            "auto_geocode_new_places", "auto_cleanup_enabled", "charging_boost_enabled",
            "daily_insights_enabled", "weekly_insights_enabled", "anomaly_alerts_enabled",
            "place_confirmation_prompts_enabled", "show_route_polylines", "show_visit_markers",
            "show_gap_segments", "show_low_confidence_segments", "route_color_by_transport_mode",
            "visit_marker_numbering", "unify_travel_segments", "export_include_raw_samples",
            "strip_exact_coordinates_on_export", "coarsen_geocode_queries", "flag_secure_enabled",
        ).forEach { put(it, Type.BOOL) }
        // ── Int ──
        listOf(
            "min_dwell_minutes", "place_radius_m", "entry_hysteresis_count", "exit_hysteresis_count",
            "exit_buffer_m", "discovery_interval_hours", "ar_confidence_threshold",
            "battery_saver_threshold", "battery_saver_threshold_pct", "battery_budget_pct_per_day",
            "cluster_markers_at_zoom", "raw_sample_retention_days", "derived_data_retention_days",
            "correction_feedback_retention_days", "sleep_window_start_hour", "sleep_window_start_minute",
            "sleep_window_end_hour", "sleep_window_end_minute",
        ).forEach { put(it, Type.INT) }
        // ── Long ──
        listOf(
            "custom_sampling_interval_ms", "min_segment_duration_ms", "sleep_sampling_interval_ms",
        ).forEach { put(it, Type.LONG) }
        // ── String ──
        listOf(
            "tracking_tier", "sampling_preset", "day_boundary_mode", "home_timezone",
            "active_preset", "active_job", "geocode_language", "geocoding_provider_order",
            "photon_server_url",
        ).forEach { put(it, Type.STRING) }
    }

    /** A parsed backup line: the raw key and its raw (still-string) value. */
    data class Line(val key: String, val rawValue: String)

    /**
     * Splits a backup blob into `key=value` lines. Blank lines and lines without `=` are
     * dropped; the value keeps everything after the first `=` (so URLs with `?a=b` survive).
     */
    fun parseLines(text: String): List<Line> =
        text.lineSequence()
            .filter { it.contains('=') }
            .map { Line(it.substringBefore('=').trim(), it.substringAfter('=').trim()) }
            .toList()

    /**
     * Parses [rawValue] to the type declared for [key]. Null for an unknown key or a value that
     * doesn't parse — callers skip that line rather than failing the whole import.
     */
    fun coerce(key: String, rawValue: String): Any? = when (KEY_TYPES[key]) {
        Type.BOOL -> rawValue.toBooleanStrictOrNull()
        Type.INT -> rawValue.toIntOrNull()
        Type.LONG -> rawValue.toLongOrNull()
        Type.STRING -> rawValue
        null -> null
    }

    /**
     * Writes one backup line into [prefs] under the key's declared type. No-op for an unknown
     * key or an unparseable value — one bad line never aborts the restore.
     */
    fun applyTo(prefs: MutablePreferences, key: String, rawValue: String) {
        when (KEY_TYPES[key]) {
            Type.BOOL -> rawValue.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(key)] = it }
            Type.INT -> rawValue.toIntOrNull()?.let { prefs[intPreferencesKey(key)] = it }
            Type.LONG -> rawValue.toLongOrNull()?.let { prefs[longPreferencesKey(key)] = it }
            Type.STRING -> prefs[stringPreferencesKey(key)] = rawValue
            null -> {} // unknown key — skip
        }
    }
}
