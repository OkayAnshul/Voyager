package com.cosmiclaboratory.voyager.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks the settings-backup codec (W9.3): line parsing and **key-driven** typing. The headline
 * guarantee is that a string-typed setting whose value looks boolean/numeric stays a String —
 * the old value-shape inference would mis-type it and crash the whole import.
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
    fun `boolean keys parse strict true and false`() {
        assertThat(SettingsBackup.coerce("tracking_enabled", "true")).isEqualTo(true)
        assertThat(SettingsBackup.coerce("show_visit_markers", "false")).isEqualTo(false)
        // Strict: only lowercase true/false parse; anything else is skipped, not coerced.
        assertThat(SettingsBackup.coerce("tracking_enabled", "TRUE")).isNull()
        assertThat(SettingsBackup.coerce("tracking_enabled", "yes")).isNull()
    }

    @Test
    fun `int keys parse integers and skip non-numbers`() {
        assertThat(SettingsBackup.coerce("min_dwell_minutes", "5")).isEqualTo(5)
        assertThat(SettingsBackup.coerce("place_radius_m", "abc")).isNull()
        assertThat(SettingsBackup.coerce("place_radius_m", "")).isNull()
    }

    @Test
    fun `string keys keep the raw value even when it looks boolean or numeric`() {
        // The bug this codec fixes: value-shape inference would have turned these into
        // Boolean/Int and then crashed on the `as String` cast, failing the whole import.
        assertThat(SettingsBackup.coerce("home_timezone", "true")).isEqualTo("true")
        assertThat(SettingsBackup.coerce("photon_server_url", "123")).isEqualTo("123")
        assertThat(SettingsBackup.coerce("sampling_preset", "HIGH_ACCURACY")).isEqualTo("HIGH_ACCURACY")
    }

    @Test
    fun `an unknown key coerces to null so the importer skips it`() {
        assertThat(SettingsBackup.coerce("totally_unknown_setting", "whatever")).isNull()
    }

    @Test
    fun `every declared key type round-trips a representative value`() {
        SettingsBackup.KEY_TYPES.forEach { (key, type) ->
            val raw = when (type) {
                SettingsBackup.Type.BOOL -> "true"
                SettingsBackup.Type.INT -> "7"
                SettingsBackup.Type.STRING -> "x"
            }
            assertThat(SettingsBackup.coerce(key, raw)).isNotNull()
        }
    }
}
