package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.UserCalibrationProfile
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import javax.inject.Inject

data class FusedMotionState(
    val activityType: ActivityType,
    val confidence: Float,
    val arConfidence: Float,
    val speedConfidence: Float,
    val stepConfidence: Float
)

class FuseActivityStateUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    // Hysteresis: keep last speed-derived activity to avoid oscillation at boundaries
    private var lastSpeedActivity: ActivityType? = null
    // Vehicle context (T4): once we're confidently in a vehicle, a dip into cycling
    // speed is slow traffic, not a bike ride.
    private var inVehicleContext: Boolean = false

    fun reset() {
        lastSpeedActivity = null
        inVehicleContext = false
    }

    fun fuse(
        arActivity: ActivityType?,
        arConfidence: Float,
        speedMps: Float?,
        stepRatePerMinute: Float?,
        calibration: UserCalibrationProfile = UserCalibrationProfile(),
        accuracyM: Float? = null
    ): FusedMotionState {
        // Validate GPS speed against accuracy — poor accuracy produces phantom speed spikes.
        // Real data showed 24.67 m/s "speed" on stationary segments with ~13m avg accuracy.
        val validatedSpeed = when {
            speedMps == null -> null
            speedMps > 55f -> null // 200 km/h absolute sanity cap
            accuracyM != null && accuracyM > 30f && speedMps > accuracyM / 2f -> null
            else -> speedMps
        }

        // Speed-based heuristic with wide dead zones at boundaries to prevent oscillation.
        // GPS speed jitter of ±0.5 m/s is common; dead zones must be wider than the jitter.
        val rawSpeedActivity = when {
            validatedSpeed == null -> null
            validatedSpeed < 0.3f -> ActivityType.STILL
            validatedSpeed < 1.3f -> ActivityType.WALKING
            validatedSpeed in 1.3f..2.1f -> lastSpeedActivity.takeIf {
                it == ActivityType.WALKING || it == ActivityType.RUNNING
            } ?: ActivityType.WALKING
            validatedSpeed < 3.0f -> ActivityType.RUNNING
            validatedSpeed in 3.0f..4.5f -> lastSpeedActivity.takeIf {
                it == ActivityType.RUNNING || it == ActivityType.CYCLING
            } ?: ActivityType.IN_VEHICLE
            validatedSpeed < 6.5f -> ActivityType.CYCLING
            validatedSpeed in 6.5f..8.5f -> lastSpeedActivity.takeIf {
                it == ActivityType.CYCLING || it == ActivityType.IN_VEHICLE
            } ?: ActivityType.CYCLING
            else -> ActivityType.IN_VEHICLE
        }
        // Step-rate override (check higher thresholds first).
        // WALKING threshold at 100 spm: real walking cadence is ≥110 spm. The 80–100
        // band is excluded because brief desk shuffles (30–90 spm bursts) at the high
        // end of that range used to pull fusion toward WALKING without true walking.
        val stepActivity = when {
            stepRatePerMinute == null -> null
            stepRatePerMinute > 140 -> ActivityType.RUNNING
            stepRatePerMinute > 100 -> ActivityType.WALKING
            stepRatePerMinute < 5 && (arActivity == ActivityType.WALKING || arActivity == ActivityType.RUNNING) -> ActivityType.STILL
            else -> null
        }
        // Step confidence reflects signal clarity — pedometer in clear bands is a
        // direct hardware measurement, much more reliable than GPS-derived speed.
        val stepConf = when {
            stepRatePerMinute == null -> 0f
            stepRatePerMinute > 140 || stepRatePerMinute < 5 -> 0.9f
            stepRatePerMinute > 100 -> 0.85f
            else -> 0.4f
        }

        // Settings gate which signals contribute to the fused state.
        val settings = settingsRepository.observeSettings().value
        // Activity Recognition only counts when enabled AND its confidence clears
        // the user-configured threshold (arConfidence is on a 0-100 scale).
        val effectiveArActivity = if (
            settings.activityRecognitionEnabled &&
            arConfidence >= settings.arConfidenceThreshold
        ) arActivity else null

        // ── Vehicle context (T4) ──
        // Slow/stop-go traffic sits in the cycling speed band, so speed alone mislabels a
        // car in congestion as CYCLING. Once we're confidently in a vehicle (AR says so, or
        // we were clearly driving above bike speed), treat cycling-speed readings as slow
        // traffic. Sticky across red lights; cleared only by real walking steps or a
        // confident non-vehicle AR reading.
        when {
            effectiveArActivity == ActivityType.IN_VEHICLE ||
                (validatedSpeed != null && validatedSpeed > VEHICLE_CONTEXT_SPEED_MPS) ->
                inVehicleContext = true
            stepActivity == ActivityType.WALKING || stepActivity == ActivityType.RUNNING ||
                effectiveArActivity == ActivityType.WALKING ||
                effectiveArActivity == ActivityType.RUNNING ||
                effectiveArActivity == ActivityType.ON_BICYCLE ->
                inVehicleContext = false
            // else: hold context through ambiguous slow patches / brief stops
        }

        val speedActivity = if (inVehicleContext && rawSpeedActivity == ActivityType.CYCLING) {
            ActivityType.IN_VEHICLE
        } else {
            rawSpeedActivity
        }
        lastSpeedActivity = speedActivity
        val speedConf = if (validatedSpeed != null) 0.7f else 0f

        // Weighted fusion
        val candidates = mutableMapOf<ActivityType, Float>()
        effectiveArActivity?.let {
            candidates[it] = (candidates[it] ?: 0f) + arConfidence / 100f * calibration.arWeight
        }
        if (settings.speedHeuristicEnabled) {
            speedActivity?.let {
                candidates[it] = (candidates[it] ?: 0f) + speedConf * calibration.speedHeuristicWeight
            }
        }
        if (settings.stepRateFusionEnabled) {
            stepActivity?.let {
                candidates[it] = (candidates[it] ?: 0f) + stepConf * calibration.stepRateWeight
            }
        }

        val best = candidates.maxByOrNull { it.value }
        // Require minimum weighted confidence to avoid noise-driven classifications.
        // 0.12 allows any single confident source to classify. The main fragmentation
        // fixes are in the wider dead zones, higher debounce, and dominant-mode voting.
        val minConfidence = 0.12f
        return FusedMotionState(
            activityType = if (best != null && best.value >= minConfidence) best.key else ActivityType.UNKNOWN,
            confidence = best?.value ?: 0f,
            arConfidence = arConfidence / 100f,
            speedConfidence = speedConf,
            stepConfidence = stepConf
        )
    }

    private companion object {
        /** Above bike top-speed → unambiguously driving; arms the vehicle context. */
        const val VEHICLE_CONTEXT_SPEED_MPS = 8.5f
    }
}
