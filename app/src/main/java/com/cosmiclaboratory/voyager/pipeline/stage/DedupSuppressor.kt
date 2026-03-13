package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.pipeline.RawSample
import javax.inject.Inject

class DedupSuppressor @Inject constructor() {

    private var lastAcceptedSample: RawSample? = null

    fun shouldSuppress(sample: RawSample): Boolean {
        val last = lastAcceptedSample ?: run {
            lastAcceptedSample = sample
            return false
        }

        val distance = LocationUtils.calculateDistance(last.lat, last.lng, sample.lat, sample.lng)
        val timeDelta = sample.capturedAt - last.capturedAt

        // Reject out-of-order samples from FLP batching or post-restart cached delivery
        if (timeDelta < 0) return true

        // Accuracy-aware jitter filtering: use GPS accuracy as noise floor instead of
        // hardcoded 3m. GPS jitter at home (~10m accuracy) creates 3-10m position jumps
        // that previously passed through, causing 48+ samples/min when stationary.
        val noiseFloor = maxOf(3.0, ((last.accuracyM ?: 10f) + (sample.accuracyM ?: 10f)).toDouble() / 2.0)
        if (distance < noiseFloor && timeDelta < 30_000) {
            return true // Within GPS noise floor and recent — suppress jitter
        }

        lastAcceptedSample = sample
        return false
    }

    fun reset() {
        lastAcceptedSample = null
    }
}
