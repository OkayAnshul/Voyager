package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.pipeline.PipelineSerializer
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import javax.inject.Inject

class StateCommitter @Inject constructor(
    private val pipelineSerializer: PipelineSerializer,
    private val stateStore: TimelineStateStore
) {
    /**
     * Record timestamp early — before pipeline stages run — so the gap watchdog
     * doesn't see a stale lastAcceptedAt and create a false GPS_LOSS gap.
     */
    suspend fun commitTimestampEarly(timestamp: Long) {
        stateStore.recordTimestamp(timestamp)
    }

    suspend fun commit(sample: RawSample, pipelineStartMs: Long) {
        pipelineSerializer.withStateCommitLock {
            val latencyMs = System.currentTimeMillis() - pipelineStartMs
            stateStore.recordSampleAccepted(sample.sampleId, System.currentTimeMillis(), latencyMs)
        }
    }
}
