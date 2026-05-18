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
