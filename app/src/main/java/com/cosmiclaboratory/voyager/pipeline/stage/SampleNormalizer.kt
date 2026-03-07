package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.pipeline.RawSample
import javax.inject.Inject

class SampleNormalizer @Inject constructor() {

    fun normalize(sample: RawSample): RawSample {
        return sample.copy(
            lat = roundCoordinate(sample.lat, 7),
            lng = roundCoordinate(sample.lng, 7),
            speedMps = sample.speedMps?.coerceAtLeast(0f),
            bearingDeg = sample.bearingDeg?.let { it % 360f }
        )
    }

    private fun roundCoordinate(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(value * factor) / factor
    }
}
