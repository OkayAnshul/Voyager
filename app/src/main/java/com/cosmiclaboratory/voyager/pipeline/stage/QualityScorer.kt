package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.capture.AdaptiveSamplingPolicy
import com.cosmiclaboratory.voyager.pipeline.RawSample
import javax.inject.Inject

data class QualityResult(
    val sample: RawSample,
    val qualityScore: Float,
    val shouldDiscard: Boolean,
    val reason: String?
)

class QualityScorer @Inject constructor(
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy
) {

    fun score(sample: RawSample): QualityResult {
        // Mock detection
        if (sample.isMock) {
            return QualityResult(sample, 0f, true, "Mock location detected")
        }

        // Accuracy filter
        if (sample.accuracyM > 200f) {
            return QualityResult(sample, 0.1f, true, "Accuracy too low: ${sample.accuracyM}m")
        }

        // Staleness check — must account for FLP batching delay and Doze mode.
        // In low-power states (STILL/DORMANT/SLEEP/CHARGING), Doze on Samsung/Xiaomi
        // can delay samples 10-15 min, so use a generous 20-minute threshold.
        val policy = adaptiveSamplingPolicy.getCurrentPolicy()
        val motionState = adaptiveSamplingPolicy.getCurrentMotionState()
        val stalenessThresholdMs = when (motionState) {
            AdaptiveSamplingPolicy.MotionState.STILL,
            AdaptiveSamplingPolicy.MotionState.DORMANT,
            AdaptiveSamplingPolicy.MotionState.SLEEP,
            AdaptiveSamplingPolicy.MotionState.CHARGING ->
                1_200_000L // 20 min — Doze/battery optimization delays
            else ->
                (policy.intervalMs * 3).coerceAtLeast(600_000L)
        }
        val age = System.currentTimeMillis() - sample.capturedAt
        if (age > stalenessThresholdMs) {
            return QualityResult(sample, 0.3f, true, "Stale sample: ${age}ms old")
        }

        val score = when {
            sample.accuracyM <= 10f -> 1.0f
            sample.accuracyM <= 25f -> 0.9f
            sample.accuracyM <= 50f -> 0.7f
            sample.accuracyM <= 100f -> 0.5f
            else -> 0.3f
        }

        return QualityResult(sample, score, false, null)
    }
}
