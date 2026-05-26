package com.cosmiclaboratory.voyager.pipeline

/**
 * The pipeline's view of persistence.
 *
 * The only database access [PipelineConsumer] needs, behind a thin interface that
 * speaks in pipeline types + primitives — so the `pipeline` package no longer
 * imports Room DAOs or entities (hardening audit A6). The implementation lives in
 * `data/`, which keeps this the seam for a future KMP/iOS extraction of the
 * pipeline.
 *
 * Methods are deliberately thin: all pipeline *logic* stays in [PipelineConsumer];
 * this only abstracts the storage calls.
 */
interface PipelineGateway {
    /** Persists a raw location sample; returns its new row id. */
    suspend fun recordSample(sample: RawSample): Long

    /** The most recently started segment, or null if none. */
    suspend fun latestSegment(): SegmentRef?

    /** Extends an existing segment's end time. */
    suspend fun updateSegmentEndAt(segmentId: Long, endAt: Long)

    /** Inserts a GAP segment for an interval with no tracking. */
    suspend fun recordGapSegment(startAt: Long, endAt: Long, dayKey: String, reason: String)

    /** The most recent activity-recognition sample, or null. */
    suspend fun latestActivity(): ActivityRef?

    /** Step buckets captured within [fromMs]..[toMs]. */
    suspend fun stepBuckets(fromMs: Long, toMs: Long): List<StepBucket>
}

/** Minimal projection of a movement segment the pipeline reasons about. */
data class SegmentRef(val segmentId: Long, val segmentType: String, val endAt: Long)

/** Minimal projection of an activity-recognition sample. */
data class ActivityRef(val activityType: String, val capturedAt: Long, val confidence: Int)

/** Minimal projection of a step-count bucket. */
data class StepBucket(val stepCount: Int, val periodStart: Long, val periodEnd: Long)
