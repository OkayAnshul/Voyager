package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.pipeline.RawSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Heuristic indoor/outdoor classifier. Pure — driven only by signals already
 * recorded on raw samples and the step rate provided by the caller.
 *
 * Used by:
 * - the segmenter to refine RUN → TREADMILL when indoor steps fire without
 *   matching GPS movement
 * - power management to drop GPS rate inside the "indoor at a confirmed
 *   place" state (Wave 9 P1 builds on this)
 *
 * Output is a single probability [0..1] — 0 = clearly outdoor, 1 = clearly
 * indoor, intermediate values mean uncertain. Callers should set their own
 * thresholds; this layer doesn't decide.
 */
@Singleton
class IndoorOutdoorClassifier @Inject constructor() {

    /**
     * Snapshot of recent signal quality.  The pipeline keeps a small rolling
     * window of samples; the classifier folds that into a single feature
     * vector each call.
     */
    data class Window(
        val samples: List<RawSample>,
        val stepRatePerMinute: Float?
    )

    fun probabilityIndoor(window: Window): Float {
        if (window.samples.isEmpty()) return 0.5f
        val accuracies = window.samples.map { it.accuracyM }
        val avgAccuracy = accuracies.average().toFloat()
        val accuracyVariance = variance(accuracies)
        val avgSpeed = window.samples.mapNotNull { it.speedMps }.average().toFloat()
            .takeIf { !it.isNaN() } ?: 0f

        // High average accuracy = poor GPS = likely indoor.
        val accuracyScore = when {
            avgAccuracy >= 50f -> 1.0f
            avgAccuracy >= 25f -> 0.7f
            avgAccuracy >= 12f -> 0.4f
            else -> 0.1f
        }

        // Very low accuracy variance with poor accuracy = sustained indoor signal.
        // Wild variance suggests transitioning (e.g. walking out of a building).
        val varianceScore = if (avgAccuracy >= 25f && accuracyVariance < 100f) 0.15f else 0f

        // Stepping with no matching GPS movement is a strong indoor signal —
        // treadmill, walking in a building, etc.
        val stepWithoutMovementScore = if (
            (window.stepRatePerMinute ?: 0f) > 60f && avgSpeed < 0.3f
        ) 0.25f else 0f

        // Outdoor cancellation: sustained GPS speed > 1.5 m/s with sub-25 m accuracy
        // means the user is moving outside — clamp the indoor probability down.
        val outdoorEvidence = if (avgSpeed > 1.5f && avgAccuracy < 25f) 0.5f else 0f

        val raw = accuracyScore + varianceScore + stepWithoutMovementScore - outdoorEvidence
        return raw.coerceIn(0f, 1f)
    }

    private fun variance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        var sumSq = 0f
        for (v in values) sumSq += (v - mean) * (v - mean)
        return max(0f, sumSq / values.size)
    }

    /** Convenience overload — pass raw samples and a step rate directly. */
    fun probabilityIndoor(samples: List<RawSample>, stepRatePerMinute: Float?): Float =
        probabilityIndoor(Window(samples, stepRatePerMinute))
}
