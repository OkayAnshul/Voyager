package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.AccelSignature
import kotlin.math.sqrt

/**
 * Pure accelerometer-signature classifier (C2). Orientation-independent: it works on the
 * **variance of the acceleration magnitude** over a short window, so it doesn't matter how the
 * phone is held. No Android/sensor deps — the capture layer feeds it raw samples; this is the
 * unit-tested core.
 *
 * Thresholds are in (m/s²)² of magnitude variance and are deliberately conservative starting
 * points — at rest |a| ≈ 9.81 with variance ≈ 0; a vehicle adds only road/engine vibration;
 * striding adds large rhythmic swings. They are device-tunable (see [STILL_MAX_VARIANCE] /
 * [ON_FOOT_MIN_VARIANCE]) and the signal is a *hint* fused with GPS/AR/steps, never a verdict.
 */
object AccelSignatureClassifier {

    /** Below this magnitude variance the body is effectively still. */
    const val STILL_MAX_VARIANCE = 0.5

    /** At/above this, the rhythmic swing of striding (walk/run) dominates. */
    const val ON_FOOT_MIN_VARIANCE = 3.0

    /** Fewer samples than this in a window is too little signal to classify. */
    const val MIN_SAMPLES = 8

    /** Population variance of the acceleration magnitudes (0 for <2 samples). */
    fun magnitudeVariance(magnitudes: List<Float>): Double {
        if (magnitudes.size < 2) return 0.0
        val mean = magnitudes.sumOf { it.toDouble() } / magnitudes.size
        return magnitudes.sumOf { val d = it - mean; d * d } / magnitudes.size
    }

    /** Maps a magnitude variance to a coarse body-motion signature. */
    fun classify(variance: Double): AccelSignature = when {
        variance < STILL_MAX_VARIANCE -> AccelSignature.STILL
        variance >= ON_FOOT_MIN_VARIANCE -> AccelSignature.ON_FOOT
        else -> AccelSignature.SMOOTH_MOTION
    }

    /**
     * Classifies a window of raw 3-axis accelerometer samples (x, y, z in m/s²): per-sample
     * magnitude → variance → signature. Returns null when there's too little signal
     * ([MIN_SAMPLES]) so the fuser can ignore it rather than act on noise.
     */
    fun classifyWindow(samples: List<Triple<Float, Float, Float>>): AccelSignature? {
        if (samples.size < MIN_SAMPLES) return null
        val magnitudes = samples.map { (x, y, z) -> sqrt(x * x + y * y + z * z) }
        return classify(magnitudeVariance(magnitudes))
    }
}
