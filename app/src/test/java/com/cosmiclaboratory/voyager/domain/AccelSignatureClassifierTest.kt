package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.AccelSignature
import com.cosmiclaboratory.voyager.domain.usecase.AccelSignatureClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccelSignatureClassifierTest {

    @Test
    fun `magnitude variance is zero for a constant signal and positive for a varying one`() {
        assertEquals(0.0, AccelSignatureClassifier.magnitudeVariance(List(10) { 9.81f }), 1e-9)
        assertEquals(0.0, AccelSignatureClassifier.magnitudeVariance(listOf(5f)), 1e-9) // <2 samples
        // values 8 and 12, mean 10 → variance 4
        assertEquals(4.0, AccelSignatureClassifier.magnitudeVariance(listOf(8f, 12f)), 1e-9)
    }

    @Test
    fun `variance buckets into still, smooth motion, and on-foot`() {
        assertEquals(AccelSignature.STILL, AccelSignatureClassifier.classify(0.1))
        assertEquals(AccelSignature.SMOOTH_MOTION, AccelSignatureClassifier.classify(1.5)) // riding band
        assertEquals(AccelSignature.ON_FOOT, AccelSignatureClassifier.classify(8.0))       // striding
    }

    @Test
    fun `boundaries fall on the lower bucket`() {
        // exactly STILL_MAX_VARIANCE is no longer STILL; exactly ON_FOOT_MIN_VARIANCE is ON_FOOT
        assertEquals(AccelSignature.SMOOTH_MOTION, AccelSignatureClassifier.classify(AccelSignatureClassifier.STILL_MAX_VARIANCE))
        assertEquals(AccelSignature.ON_FOOT, AccelSignatureClassifier.classify(AccelSignatureClassifier.ON_FOOT_MIN_VARIANCE))
    }

    @Test
    fun `a window with too few samples is not classified`() {
        val tiny = List(AccelSignatureClassifier.MIN_SAMPLES - 1) { Triple(0f, 0f, 9.81f) }
        assertNull(AccelSignatureClassifier.classifyWindow(tiny))
    }

    @Test
    fun `a phone at rest reads STILL`() {
        // ~1g on the z-axis with tiny noise.
        val rest = List(20) { Triple(0.02f, 0.01f, 9.81f) }
        assertEquals(AccelSignature.STILL, AccelSignatureClassifier.classifyWindow(rest))
    }

    @Test
    fun `large rhythmic swings read ON_FOOT`() {
        // Alternating high/low magnitude like footfalls.
        val striding = (0 until 20).map { i ->
            if (i % 2 == 0) Triple(0f, 0f, 6f) else Triple(0f, 0f, 14f)
        }
        assertEquals(AccelSignature.ON_FOOT, AccelSignatureClassifier.classifyWindow(striding))
    }

    @Test
    fun `small steady vibration reads SMOOTH_MOTION (riding, not striding)`() {
        // Low-amplitude jitter around 1g — a vehicle on a road, body not striding.
        val riding = (0 until 20).map { i ->
            val n = if (i % 2 == 0) 9.0f else 10.6f // ~0.64 variance → between still and on-foot
            Triple(0f, 0f, n)
        }
        assertEquals(AccelSignature.SMOOTH_MOTION, AccelSignatureClassifier.classifyWindow(riding))
    }
}
