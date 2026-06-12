package com.cosmiclaboratory.voyager.data.repository

/**
 * Pure codec for the `key=value` settings backup format produced by
 * `SettingsRepositoryImpl.exportSettings` and consumed by `importSettings`.
 *
 * Why this exists: import used to infer a value's type from its *shape* (`"true"` ⇒ Boolean,
 * all-digits ⇒ Int, else String) and then cast it to the key's declared type. A string-typed
 * setting whose value happened to look boolean/numeric would hit a `ClassCastException` and
 * fail the **entire** import. Typing must be driven by the **key**, not the value — that's what
 * [coerce] does, against [KEY_TYPES]. An unknown key or a value that won't parse returns null,
 * so the importer skips that one line instead of aborting the whole restore.
 *
 * Note: [KEY_TYPES] is the explicit set of settings the importer restores today; the exporter
 * dumps every stored preference, so a backup→restore round trip is currently lossy for keys
 * absent here. Widening it to the full settings surface wants a typed key registry (tracked
 * follow-up), not a hand-maintained `when`.
 */
internal object SettingsBackup {

    enum class Type { BOOL, INT, STRING }

    /** The settings keys the importer understands, and how to parse each. */
    val KEY_TYPES: Map<String, Type> = mapOf(
        "tracking_enabled" to Type.BOOL,
        "sleep_detection_enabled" to Type.BOOL,
        "step_counting_enabled" to Type.BOOL,
        "auto_geocode_new_places" to Type.BOOL,
        "daily_insights_enabled" to Type.BOOL,
        "weekly_insights_enabled" to Type.BOOL,
        "show_route_polylines" to Type.BOOL,
        "show_visit_markers" to Type.BOOL,
        "min_dwell_minutes" to Type.INT,
        "place_radius_m" to Type.INT,
        "battery_saver_threshold" to Type.INT,
        "raw_sample_retention_days" to Type.INT,
        "sampling_preset" to Type.STRING,
        "day_boundary_mode" to Type.STRING,
        "home_timezone" to Type.STRING,
        "active_preset" to Type.STRING,
        "active_job" to Type.STRING,
        "photon_server_url" to Type.STRING,
    )

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
     * Parses [rawValue] to the type declared for [key]. Returns null for an unknown key or a
     * value that doesn't parse — the caller skips that line rather than failing the import.
     */
    fun coerce(key: String, rawValue: String): Any? = when (KEY_TYPES[key]) {
        Type.BOOL -> rawValue.toBooleanStrictOrNull()
        Type.INT -> rawValue.toIntOrNull()
        Type.STRING -> rawValue
        null -> null
    }
}
