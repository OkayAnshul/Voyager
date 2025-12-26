package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.UserCalibrationProfile
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import javax.inject.Inject

data class FusedMotionState(
    val activityType: ActivityType,
    val confidence: Float,
    val arConfidence: Float,
    val speedConfidence: Float,
    val stepConfidence: Float
)

class FuseActivityStateUseCase @Inject constructor() {

    // Hysteresis: keep last speed-derived activity to avoid oscillation at boundaries
    private var lastSpeedActivity: ActivityType? = null

    fun reset() {
        lastSpeedActivity = null
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
            } ?: ActivityType.CYCLING
            validatedSpeed < 6.5f -> ActivityType.CYCLING
            validatedSpeed in 6.5f..8.5f -> lastSpeedActivity.takeIf {
                it == ActivityType.CYCLING || it == ActivityType.IN_VEHICLE
            } ?: ActivityType.CYCLING
            else -> ActivityType.IN_VEHICLE
        }
        val speedActivity = rawSpeedActivity.also { lastSpeedActivity = it }
        val speedConf = if (validatedSpeed != null) 0.7f else 0f

        // Step-rate override (check higher thresholds first)
        val stepActivity = when {
            stepRatePerMinute == null -> null
            stepRatePerMinute > 140 -> ActivityType.RUNNING
            stepRatePerMinute > 80 -> ActivityType.WALKING
            stepRatePerMinute < 5 && (arActivity == ActivityType.WALKING || arActivity == ActivityType.RUNNING) -> ActivityType.STILL
            else -> null
        }
        // Step confidence reflects signal clarity — pedometer in clear bands is a
        // direct hardware measurement, much more reliable than GPS-derived speed.
        val stepConf = when {
            stepRatePerMinute == null -> 0f
            stepRatePerMinute > 140 || stepRatePerMinute < 5 -> 0.9f
            stepRatePerMinute > 80 -> 0.85f
            else -> 0.4f
        }

        // Weighted fusion
        val candidates = mutableMapOf<ActivityType, Float>()
        arActivity?.let {
            candidates[it] = (candidates[it] ?: 0f) + arConfidence / 100f * calibration.arWeight
        }
        speedActivity?.let {
            candidates[it] = (candidates[it] ?: 0f) + speedConf * calibration.speedHeuristicWeight
        }
        stepActivity?.let {
            candidates[it] = (candidates[it] ?: 0f) + stepConf * calibration.stepRateWeight
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
}
