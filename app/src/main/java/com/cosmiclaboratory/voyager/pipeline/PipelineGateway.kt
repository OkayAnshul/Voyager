package com.cosmiclaboratory.voyager.pipeline

/**
 * The pipeline's view of persistence.
 *
 * The one storage seam the `pipeline` package talks to — so the package no longer
 * imports Room DAOs or storage entities (hardening audit A6 / KMP seam). The
 * implementation lives in `data/`, which maps these pipeline-owned types to Room.
 *
 * The interface is intentionally thin for reads/writes that the pipeline already
 * reasons about (the consumer's gap-bridge condition, AR-staleness, step-rate
 * math, place-linking orchestration all stay in `pipeline/`). The one
 * compound method — [commitClosedSegment] — encapsulates the **atomic
 * transaction** that closes a segment with its evidence and (optional) route,
 * so the pipeline doesn't need to touch `withTransaction` either.
 */
interface PipelineGateway {

    // ---------- raw samples ----------

    /** Persists a raw location sample; returns its new row id. */
    suspend fun recordSample(sample: RawSample): Long

    // ---------- segments ----------

    /** The most recently started segment, or null if none. */
    suspend fun latestSegment(): SegmentRef?

    /** Extends an existing segment's end time. */
    suspend fun updateSegmentEndAt(segmentId: Long, endAt: Long)

    /** Inserts a GAP segment for an interval with no tracking. */
    suspend fun recordGapSegment(startAt: Long, endAt: Long, dayKey: String, reason: String)

    /**
     * Atomically persists a closed segment, its evidence, and (optionally) its route.
     *
     * The transaction boundary lives in the impl — callers don't import Room. If
     * [route] is provided, it's inserted and the segment's `routeId` is back-patched
     * in the same transaction. Returns the new segment id.
     */
    suspend fun commitClosedSegment(
        segment: SegmentDraft,
        evidence: EvidenceDraft,
        route: RouteDraft?
    ): Long

    /** All segments for a day (ordered by startAt asc). */
    suspend fun segmentsForDay(dayKey: String): List<SegmentRef>

    /** Stamps a placeId onto a single segment. */
    suspend fun setSegmentPlace(segmentId: Long, placeId: Long)

    // ---------- visits ----------

    /** A single visit by id, or null. */
    suspend fun getVisit(visitId: Long): VisitRef?

    /** Associates a visit with a place. */
    suspend fun setVisitPlace(visitId: Long, placeId: Long)

    /** How many visits link to a place — used to gate auto-promotion. */
    suspend fun countVisitsForPlace(placeId: Long): Int

    // ---------- activity / steps ----------

    /** The most recent activity-recognition sample, or null. */
    suspend fun latestActivity(): ActivityRef?

    /** Step buckets captured within [fromMs]..[toMs]. */
    suspend fun stepBuckets(fromMs: Long, toMs: Long): List<StepBucket>

    // ---------- places ----------

    /** A single place by id, or null. */
    suspend fun getPlace(placeId: Long): PlaceRef?

    /** Updates a place's lifecycle + confidence (the auto-promote path). */
    suspend fun setPlaceLifecycle(placeId: Long, lifecycleStatus: String, confidence: Float)

    /** Bumps `lastVisitedAt` on an existing place. */
    suspend fun touchPlaceVisited(placeId: Long, atMs: Long)

    /** Places whose geohash starts with [prefix] — narrow-radius nearby probe. */
    suspend fun placesNearGeohash(prefix: String): List<PlaceRef>

    /** Inserts a fresh candidate place; returns its id. */
    suspend fun createCandidatePlace(draft: PlaceDraft): Long
}

// ---------- ref / projection types ----------

/** Minimal projection of a movement segment. Fields cover every pipeline read site. */
data class SegmentRef(
    val segmentId: Long,
    val segmentType: String,
    val startAt: Long,
    val endAt: Long,
    val placeId: Long?
)

/** Minimal projection of an activity-recognition sample. */
data class ActivityRef(val activityType: String, val capturedAt: Long, val confidence: Int)

/** Minimal projection of a step-count bucket. */
data class StepBucket(val stepCount: Int, val periodStart: Long, val periodEnd: Long)

/** Minimal projection of a visit. */
data class VisitRef(
    val visitId: Long,
    val placeId: Long,
    val dayKey: String,
    val arrivalAt: Long,
    val departureAt: Long?
)

/** Minimal projection of a place. Carries every field any pipeline reader needs. */
data class PlaceRef(
    val placeId: Long,
    val centroidLat: Double,
    val centroidLng: Double,
    val lifecycleStatus: String,
    val confidence: Float
)

// ---------- draft types for the atomic segment commit ----------

/** All fields needed to insert a movement segment row. */
data class SegmentDraft(
    val segmentType: String,
    val startAt: Long,
    val endAt: Long,
    val startSampleId: Long?,
    val endSampleId: Long?,
    val distanceM: Double,
    val confidence: Float,
    val dayKey: String
)

/** All fields needed to insert the segment's evidence row. `segmentId` is filled by the impl. */
data class EvidenceDraft(
    val avgSpeedMps: Float?,
    val maxSpeedMps: Float?,
    val sampleCount: Int,
    val activityVotesJson: String
)

/** All fields needed to insert the segment's route row. `segmentId` is filled by the impl. */
data class RouteDraft(
    val encodedPolyline: String,
    val simplifiedPolyline: String?,
    val totalDistanceM: Double,
    val totalDurationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val transportMode: String,
    val sampleCount: Int
)

/** Fields needed to insert a fresh candidate place. */
data class PlaceDraft(
    val centroidLat: Double,
    val centroidLng: Double,
    val radiusM: Float,
    val geohash: String,
    val confidence: Float,
    val lifecycleStatus: String,
    val createdAt: Long,
    val lastVisitedAt: Long
)
