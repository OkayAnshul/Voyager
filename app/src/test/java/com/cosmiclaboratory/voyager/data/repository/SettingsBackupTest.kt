package com.cosmiclaboratory.voyager.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks the settings-backup codec (W9.3): line parsing, **key-driven** typing, and the typed
 * write path. Headline guarantees: a string-typed setting whose value looks boolean/numeric
 * stays a String (no crash), and the full settings surface round-trips (not just a subset).
 */
class SettingsBackupTest {

    @Test
    fun `parseLines drops blanks and lines without an equals, trimming key and value`() {
        val text = """
            tracking_enabled = true

            not a setting line
            min_dwell_minutes=5
        """.trimIndent()

        val lines = SettingsBackup.parseLines(text)

        assertThat(lines).containsExactly(
            SettingsBackup.Line("tracking_enabled", "true"),
            SettingsBackup.Line("min_dwell_minutes", "5"),
        ).inOrder()
    }

    @Test
    fun `a value containing an equals keeps everything after the first one`() {
        val lines = SettingsBackup.parseLines("photon_server_url=https://photon.example/api?lang=en")
        assertThat(lines.single().rawValue).isEqualTo("https://photon.example/api?lang=en")
    }

    @Test
    fun `coerce parses each type by the key and skips bad values`() {
        assertThat(SettingsBackup.coerce("tracking_enabled", "true")).isEqualTo(true)
        assertThat(SettingsBackup.coerce("tracking_enabled", "TRUE")).isNull() // strict
        assertThat(SettingsBackup.coerce("min_dwell_minutes", "5")).isEqualTo(5)
        assertThat(SettingsBackup.coerce("place_radius_m", "abc")).isNull()
        assertThat(SettingsBackup.coerce("custom_sampling_interval_ms", "60000")).isEqualTo(60_000L)
        assertThat(SettingsBackup.coerce("custom_sampling_interval_ms", "x")).isNull()
    }

    @Test
    fun `string keys keep the raw value even when it looks boolean or numeric`() {
        // The bug this codec fixes: value-shape inference would have turned these into Boolean/Int
        // and then crashed on the `as String` cast, failing the whole import.
        assertThat(SettingsBackup.coerce("home_timezone", "true")).isEqualTo("true")
        assertThat(SettingsBackup.coerce("photon_server_url", "123")).isEqualTo("123")
        assertThat(SettingsBackup.coerce("sampling_preset", "HIGH_ACCURACY")).isEqualTo("HIGH_ACCURACY")
    }

    @Test
    fun `applyTo writes each type under the reconstructed datastore key`() {
        val prefs = mutablePreferencesOf()
        SettingsBackup.applyTo(prefs, "tracking_enabled", "true")
        SettingsBackup.applyTo(prefs, "min_dwell_minutes", "12")
        SettingsBackup.applyTo(prefs, "custom_sampling_interval_ms", "60000")
        SettingsBackup.applyTo(prefs, "home_timezone", "Asia/Kolkata")

        assertThat(prefs[booleanPreferencesKey("tracking_enabled")]).isEqualTo(true)
        assertThat(prefs[intPreferencesKey("min_dwell_minutes")]).isEqualTo(12)
        assertThat(prefs[longPreferencesKey("custom_sampling_interval_ms")]).isEqualTo(60_000L)
        assertThat(prefs[stringPreferencesKey("home_timezone")]).isEqualTo("Asia/Kolkata")
    }

    @Test
    fun `applyTo ignores unknown keys and unparseable values`() {
        val prefs = mutablePreferencesOf()
        SettingsBackup.applyTo(prefs, "totally_unknown_setting", "x")
        SettingsBackup.applyTo(prefs, "min_dwell_minutes", "not-a-number")
        assertThat(prefs.asMap()).isEmpty()
    }

    @Test
    fun `applyTo writes a boolean-looking string setting as a string, never crashing`() {
        val prefs = mutablePreferencesOf()
        SettingsBackup.applyTo(prefs, "home_timezone", "true")
        assertThat(prefs[stringPreferencesKey("home_timezone")]).isEqualTo("true")
    }

    @Test
    fun `the registry covers the full settings surface, not a subset`() {
        // Was 18; the lossy-backup fix widened it to the whole persisted set.
        assertThat(SettingsBackup.KEY_TYPES).hasSize(57)
        // Spot-check keys the old 18-key importer silently dropped.
        assertThat(SettingsBackup.KEY_TYPES).containsEntry("sleep_window_start_hour", SettingsBackup.Type.INT)
        assertThat(SettingsBackup.KEY_TYPES).containsEntry("custom_sampling_interval_ms", SettingsBackup.Type.LONG)
        assertThat(SettingsBackup.KEY_TYPES).containsEntry("geocoding_provider_order", SettingsBackup.Type.STRING)
        assertThat(SettingsBackup.KEY_TYPES).containsEntry("flag_secure_enabled", SettingsBackup.Type.BOOL)
    }

    @Test
    fun `every declared key coerces a representative value of its type`() {
        SettingsBackup.KEY_TYPES.forEach { (key, type) ->
            val raw = when (type) {
                SettingsBackup.Type.BOOL -> "true"
                SettingsBackup.Type.INT -> "7"
                SettingsBackup.Type.LONG -> "7"
                SettingsBackup.Type.STRING -> "x"
            }
            assertThat(SettingsBackup.coerce(key, raw)).isNotNull()
        }
    }
}
