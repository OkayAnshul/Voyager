package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.SettingsPresets
import com.cosmiclaboratory.voyager.domain.model.enums.SamplingPreset
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the preset catalogue is complete and the presets are genuinely
 * distinct behaviour profiles (not the cosmetic 5-value presets they used to be).
 */
class SettingsPresetsTest {

    @Test
    fun `every advertised preset id resolves`() {
        val expected = listOf(
            "DAILY_COMMUTER", "BATTERY_SAVER", "PRECISION_MAX", "PRIVACY_MAX",
            "CYCLIST_RIDER", "CITY_EXPLORER", "SHORT_TRIPPER", "LONG_TRAVELER",
            "ROAD_TRIPPER", "TRANSIT_COMMUTER", "BACKPACKER"
        )
        expected.forEach { id ->
            assertNotNull("Preset $id must resolve", SettingsPresets.forId(id))
        }
    }

    @Test
    fun `unknown preset id resolves to null`() {
        assertNull(SettingsPresets.forId("NOPE"))
    }

    @Test
    fun `battery saver is gentler on the battery than precision max`() {
        val saver = SettingsPresets.forId("BATTERY_SAVER")!!
        val precision = SettingsPresets.forId("PRECISION_MAX")!!
        assertEquals(SamplingPreset.BATTERY_SAVER, saver.samplingPreset)
        assertEquals(SamplingPreset.HIGH_ACCURACY, precision.samplingPreset)
        // Battery saver dwells longer (fewer place writes) and keeps less history.
        assertTrue(saver.minDwellMinutes > precision.minDwellMinutes)
        assertTrue(saver.rawSampleRetentionDays < precision.rawSampleRetentionDays)
        assertFalse("Battery saver disables motion detection", saver.motionDetectionEnabled)
    }

    @Test
    fun `every preset in the catalogue resolves to itself by id`() {
        // Stronger than the hardcoded list above — covers any preset added later.
        SettingsPresets.all.forEach { preset ->
            assertEquals(
                "forId(${preset.id}) must return that preset's settings",
                preset.settings,
                SettingsPresets.forId(preset.id)
            )
        }
    }

    @Test
    fun `preset ids are unique`() {
        val ids = SettingsPresets.all.map { it.id }
        assertEquals("Duplicate preset ids would be masked by forId", ids.size, ids.toSet().size)
    }

    @Test
    fun `every preset is a sane tracking profile`() {
        SettingsPresets.all.forEach { p ->
            val s = p.settings
            assertTrue("${p.id}: displayName blank", p.displayName.isNotBlank())
            assertTrue("${p.id}: description blank", p.description.isNotBlank())
            assertTrue("${p.id}: minDwellMinutes must be positive", s.minDwellMinutes > 0)
            assertTrue("${p.id}: placeRadiusM must be positive", s.placeRadiusM > 0)
            assertTrue("${p.id}: raw retention out of range", s.rawSampleRetentionDays in 1..3650)
            assertTrue("${p.id}: battery threshold out of range", s.batterySaverThresholdPct in 0..100)
        }
    }

    @Test
    fun `presets are comprehensive — they differ across many fields`() {
        // The old bug: presets only changed ~5 values. Confirm presets now diverge
        // across the full behaviour surface.
        val commuter = SettingsPresets.forId("DAILY_COMMUTER")!!
        val backpacker = SettingsPresets.forId("BACKPACKER")!!
        val differing = listOf(
            commuter.minDwellMinutes != backpacker.minDwellMinutes,
            commuter.placeRadiusM != backpacker.placeRadiusM,
            commuter.rawSampleRetentionDays != backpacker.rawSampleRetentionDays,
            commuter.weeklyInsightsEnabled != backpacker.weeklyInsightsEnabled,
            commuter.dailyInsightsEnabled != backpacker.dailyInsightsEnabled
        ).count { it }
        assertTrue("Presets must differ across multiple fields", differing >= 3)
    }
}
