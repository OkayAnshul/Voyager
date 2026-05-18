package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.SamplingPreset

/**
 * The single catalogue of tracking presets ("personas").
 *
 * Each preset is a complete [UserSettings] behaviour profile. Applying a preset
 * (via SettingsRepository.applyPreset) writes every tracking-behaviour setting,
 * so switching a preset genuinely reconfigures the whole capture pipeline —
 * not just a handful of values.
 *
 * Identity/geocoding settings (home timezone, provider order) are deliberately
 * NOT part of a preset — those are user identity, not tracking behaviour.
 */
object SettingsPresets {

    data class Preset(
        val id: String,
        val displayName: String,
        val description: String,
        val settings: UserSettings
    )

    /** Defaults already encode the DAILY_COMMUTER profile. */
    private val DEFAULTS = UserSettings()

    val all: List<Preset> = listOf(
        Preset(
            id = "DAILY_COMMUTER",
            displayName = "Daily Commuter",
            description = "Balanced tracking for regular home–work routines.",
            settings = DEFAULTS
        ),
        Preset(
            id = "BATTERY_SAVER",
            displayName = "Battery Saver",
            description = "Maximum battery life with coarser tracking.",
            settings = DEFAULTS.copy(
                samplingPreset = SamplingPreset.BATTERY_SAVER,
                minDwellMinutes = 10,
                placeRadiusM = 120,
                batterySaverThresholdPct = 30,
                stepCountingEnabled = false,
                motionDetectionEnabled = false,
                dailyInsightsEnabled = false,
                rawSampleRetentionDays = 30
            )
        ),
        Preset(
            id = "PRECISION_MAX",
            displayName = "Precision Max",
            description = "Tightest detail — frequent updates, tight clustering.",
            settings = DEFAULTS.copy(
                samplingPreset = SamplingPreset.HIGH_ACCURACY,
                minDwellMinutes = 2,
                placeRadiusM = 40,
                batterySaverThresholdPct = 10,
                rawSampleRetentionDays = 365
            )
        ),
        Preset(
            id = "PRIVACY_MAX",
            displayName = "Privacy Max",
            description = "Minimal footprint — no geocoding, short retention.",
            settings = DEFAULTS.copy(
                minDwellMinutes = 5,
                placeRadiusM = 100,
                autoGeocodeNewPlaces = false,
                dailyInsightsEnabled = false,
                rawSampleRetentionDays = 30
            )
        ),
        Preset(
            id = "CYCLIST_RIDER",
            displayName = "Cyclist / Rider",
            description = "Tuned for rides — route polylines, no step counting.",
            settings = DEFAULTS.copy(
                samplingPreset = SamplingPreset.HIGH_ACCURACY,
                minDwellMinutes = 8,
                placeRadiusM = 60,
                batterySaverThresholdPct = 15,
                stepCountingEnabled = false,
                rawSampleRetentionDays = 180,
                showRoutePolylines = true,
                showVisitMarkers = true
            )
        ),
        Preset(
            id = "CITY_EXPLORER",
            displayName = "City Explorer",
            description = "Catches short stops while wandering a city.",
            settings = DEFAULTS.copy(
                samplingPreset = SamplingPreset.HIGH_ACCURACY,
                minDwellMinutes = 3,
                placeRadiusM = 60,
                batterySaverThresholdPct = 15,
                rawSampleRetentionDays = 90
            )
        ),
        Preset(
            id = "SHORT_TRIPPER",
            displayName = "Short Tripper",
            description = "Weekend trips — slightly wider places, longer retention.",
            settings = DEFAULTS.copy(
                minDwellMinutes = 5,
                placeRadiusM = 100,
                rawSampleRetentionDays = 120
            )
        ),
        Preset(
            id = "LONG_TRAVELER",
            displayName = "Long Traveler",
            description = "Extended journeys — wide places, full history kept.",
            settings = DEFAULTS.copy(
                minDwellMinutes = 10,
                placeRadiusM = 150,
                batterySaverThresholdPct = 25,
                stepCountingEnabled = false,
                weeklyInsightsEnabled = true,
                rawSampleRetentionDays = 365
            )
        ),
        Preset(
            id = "ROAD_TRIPPER",
            displayName = "Road Tripper",
            description = "Driving-focused — route polylines, wide places.",
            settings = DEFAULTS.copy(
                minDwellMinutes = 8,
                placeRadiusM = 120,
                stepCountingEnabled = false,
                rawSampleRetentionDays = 180,
                showRoutePolylines = true
            )
        ),
        Preset(
            id = "TRANSIT_COMMUTER",
            displayName = "Transit Commuter",
            description = "Public-transport routines with balanced tracking.",
            settings = DEFAULTS.copy(
                minDwellMinutes = 5,
                placeRadiusM = 80,
                rawSampleRetentionDays = 90
            )
        ),
        Preset(
            id = "BACKPACKER",
            displayName = "Backpacker",
            description = "Long off-grid trips — wide places, weekly recaps.",
            settings = DEFAULTS.copy(
                minDwellMinutes = 10,
                placeRadiusM = 130,
                batterySaverThresholdPct = 30,
                dailyInsightsEnabled = false,
                weeklyInsightsEnabled = true,
                rawSampleRetentionDays = 365
            )
        )
    )

    fun forId(id: String): UserSettings? = all.firstOrNull { it.id == id }?.settings
}
