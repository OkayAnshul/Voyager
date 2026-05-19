package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.SamplingPreset
import com.cosmiclaboratory.voyager.domain.model.enums.TrackingTier
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SamplingPolicy(
    val intervalMs: Long,
    val minDistanceM: Float,
    val accuracy: Int // Priority constant
)

@Singleton
class AdaptiveSamplingPolicy @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    enum class MotionState {
        STILL, WALKING, RUNNING, CYCLING, DRIVING, SLEEP, CHARGING,
        /** GPS completely off — significant motion sensor is the wake trigger.
         *  Entered after 3+ consecutive STILL samples. Exits via SignificantMotionDetector. */
        DORMANT
    }

    companion object {
        /** Base sampling intervals at the BALANCED preset. */
        private const val MOVING_BASE_MS = 10_000L
        private const val STILL_BASE_MS = 90_000L
        private const val CHARGING_INTERVAL_MS = 15_000L
        /** In CUSTOM mode the STILL interval is this multiple of the MOVING interval. */
        private const val STILL_TO_MOVING_RATIO = 6
    }

    @Volatile
    private var currentMotionState: MotionState = MotionState.STILL
    @Volatile
    private var batterySaverMultiplier: Float = 1.0f

    /**
     * Sampling keys only on STILL vs MOVING — walk/drive classification is too
     * speed-dependent to trust, but "stationary vs moving" is robust. All genuine
     * motion states collapse to one MOVING tier.
     */
    fun getCurrentPolicy(): SamplingPolicy {
        val settings = settingsRepository.observeSettings().value
        // The tracking tier sets the structural mode. OFF/PASSIVE run no active
        // GPS request (interval 0) — capture relies on the always-on passive
        // listener plus motion/step sensors. WORKOUT is a fixed 1 Hz high-accuracy
        // recording mode. BALANCED/ACCURATE fall through to motion-based sampling.
        when (settings.trackingTier) {
            TrackingTier.OFF, TrackingTier.PASSIVE -> return SamplingPolicy(0L, 0f, -1)
            TrackingTier.WORKOUT -> return SamplingPolicy(1_000L, 0f, 100)
            TrackingTier.BALANCED, TrackingTier.ACCURATE -> { /* motion-based, below */ }
        }
        // Sleep window overrides motion-based sampling — within the user's sleep
        // hours, fall back to the low-power sleep interval (DORMANT still wins,
        // since GPS is fully off in that state).
        val base = when {
            currentMotionState == MotionState.DORMANT ->
                SamplingPolicy(0, 0f, -1) // GPS off — significant motion wake
            settings.sleepDetectionEnabled && isWithinSleepWindow(settings) ->
                SamplingPolicy(settings.sleepSamplingIntervalMs, 0f, 104) // LOW
            currentMotionState == MotionState.SLEEP ->
                SamplingPolicy(settings.sleepSamplingIntervalMs, 0f, 104)
            currentMotionState == MotionState.CHARGING ->
                if (settings.chargingBoostEnabled) SamplingPolicy(CHARGING_INTERVAL_MS, 5f, 100)
                else stillPolicy(settings)
            currentMotionState == MotionState.STILL -> stillPolicy(settings)
            else -> movingPolicy(settings) // WALKING / RUNNING / CYCLING / DRIVING
        }
        // ACCURATE tightens the motion-based cadence; BALANCED is the baseline.
        val tierMultiplier = if (settings.trackingTier == TrackingTier.ACCURATE) 0.5f else 1.0f
        return base.copy(
            intervalMs = (base.intervalMs * batterySaverMultiplier * tierMultiplier).toLong()
        )
    }

    /** True when [now] falls inside the configured sleep window
     *  (handles windows that cross midnight, e.g. 23:00 → 07:00). */
    internal fun isWithinSleepWindow(
        s: UserSettings,
        now: java.time.LocalTime = java.time.LocalTime.now()
    ): Boolean {
        val start = java.time.LocalTime.of(s.sleepWindowStartHour, s.sleepWindowStartMinute)
        val end = java.time.LocalTime.of(s.sleepWindowEndHour, s.sleepWindowEndMinute)
        return if (start <= end) now >= start && now < end
        else now >= start || now < end
    }

    /** Preset scales the base intervals; CUSTOM uses an explicit interval instead. */
    private fun presetMultiplier(preset: SamplingPreset): Float = when (preset) {
        SamplingPreset.HIGH_ACCURACY -> 0.5f
        SamplingPreset.BALANCED -> 1.0f
        SamplingPreset.BATTERY_SAVER -> 2.0f
        SamplingPreset.CUSTOM -> 1.0f
    }

    private fun movingPolicy(s: UserSettings): SamplingPolicy {
        val interval = if (s.samplingPreset == SamplingPreset.CUSTOM) {
            s.customSamplingIntervalMs
        } else {
            (MOVING_BASE_MS * presetMultiplier(s.samplingPreset)).toLong()
        }
        return SamplingPolicy(interval, 10f, 100) // HIGH
    }

    private fun stillPolicy(s: UserSettings): SamplingPolicy {
        val interval = if (s.samplingPreset == SamplingPreset.CUSTOM) {
            s.customSamplingIntervalMs * STILL_TO_MOVING_RATIO
        } else {
            (STILL_BASE_MS * presetMultiplier(s.samplingPreset)).toLong()
        }
        return SamplingPolicy(interval, 0f, 102) // BALANCED
    }

    fun getCurrentMotionState(): MotionState = currentMotionState

    fun updateMotionState(state: MotionState) {
        currentMotionState = state
    }

    fun setBatterySaverMultiplier(multiplier: Float) {
        batterySaverMultiplier = multiplier
    }

    // ── Hysteresis for sampling state changes ──
    // Require 2 consecutive samples with the new state before actually switching,
    // to prevent thrashing location requests at motion boundaries.

    private var lastConfirmedState: MotionState? = null
    private var pendingState: MotionState? = null
    private var pendingCount = 0

    /**
     * Apply hysteresis before updating the motion state.
     * Returns true if the state actually changed, false if still pending.
     */
    fun updateMotionStateWithHysteresis(proposed: MotionState): Boolean {
        if (proposed == lastConfirmedState) {
            pendingState = null
            pendingCount = 0
            return false
        }
        if (proposed == pendingState) {
            pendingCount++
            if (pendingCount >= 3) {
                updateMotionState(proposed)
                lastConfirmedState = proposed
                pendingState = null
                pendingCount = 0
                return true
            }
        } else {
            pendingState = proposed
            pendingCount = 1
        }
        return false
    }

    /** Force-set the confirmed state (bypasses hysteresis). Used after displacement override or dormant exit. */
    fun forceMotionState(state: MotionState) {
        updateMotionState(state)
        lastConfirmedState = state
        pendingState = null
        pendingCount = 0
    }

    /** Get the last confirmed hysteresis state */
    fun getLastConfirmedState(): MotionState? = lastConfirmedState

    fun resetHysteresis() {
        lastConfirmedState = null
        pendingState = null
        pendingCount = 0
    }
}
