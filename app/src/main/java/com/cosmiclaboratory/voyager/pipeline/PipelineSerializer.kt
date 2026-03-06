package com.cosmiclaboratory.voyager.pipeline

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class RawSample(
    val sampleId: Long = 0,
    val capturedAt: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val verticalAccuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val altitudeM: Double? = null,
    val provider: String,
    val isMock: Boolean = false,
    val batteryPct: Int? = null,
    val isCharging: Boolean = false,
    val deviceIdleMode: Boolean = false,
    val permissionSnapshot: String,
    val trackingSessionId: Long,
    val localTimeZone: String,
    val geohash: String
)

@Singleton
class PipelineSerializer @Inject constructor() {

    val sampleChannel = Channel<RawSample>(capacity = 64, onBufferOverflow = BufferOverflow.SUSPEND)

    private val stateCommitMutex = Mutex()

    suspend fun submitSample(sample: RawSample) {
        sampleChannel.send(sample)
    }

    suspend fun <T> withStateCommitLock(block: suspend () -> T): T {
        return stateCommitMutex.withLock { block() }
    }
}
