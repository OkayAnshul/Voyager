package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.TimelineRoute
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import javax.inject.Inject

/**
 * Reconciles raw timeline segments by filtering noise and merging consecutive visits.
 * Applied in the repository layer so ALL consumers (UI, export, map) see clean data.
 */
class TimelineReconciler @Inject constructor() {

    fun reconcile(
        segments: List<TimelineSegment>,
        unifyTravel: Boolean = false
    ): List<TimelineSegment> {
        val base = mergeConsecutiveMovement(
            mergeConsecutiveVisits(absorbOrphanedDwells(filterNoise(segments)))
        )
        return if (unifyTravel) unifyTravelSegments(base) else base
    }

    /**
     * Filter out noise segments that add no value to the timeline:
     * - UNKNOWN_MOTION under 5 minutes (transient classification noise)
     * - "Stationary" VISIT under 3 minutes with no place (GPS jitter artifacts)
     * - Movement segments under 1 minute with 0 distance (false transitions)
     */
    fun filterNoise(segments: List<TimelineSegment>): List<TimelineSegment> {
        return segments.filter { seg ->
            when (seg.type) {
                SegmentType.UNKNOWN_MOTION -> seg.durationMs >= 300_000 // 5 min
                SegmentType.VISIT -> seg.place != null || seg.durationMs >= 180_000 // 3 min
                SegmentType.GAP -> true // Always show gaps
                else -> seg.durationMs >= 60_000 || seg.distanceM >= 50.0 // 1 min or 50m
            }
        }
    }

    /**
     * Absorb short placeless DWELL segments (< 3 min) that sit between movement segments.
     * These are pipeline artifacts from the visit candidate starting/stopping during transit.
     * The preceding movement segment is extended to cover the orphaned DWELL.
     */
    private fun absorbOrphanedDwells(segments: List<TimelineSegment>): List<TimelineSegment> {
        if (segments.size <= 2) return segments
        val result = mutableListOf<TimelineSegment>()
        for (seg in segments) {
            if (seg.type == SegmentType.VISIT &&
                seg.durationMs < 180_000 &&
                seg.place == null &&
                result.isNotEmpty() &&
                result.last().type.isMovement()
            ) {
                // Absorb into preceding movement segment by extending its endAt
                val prev = result.removeAt(result.size - 1)
                result.add(prev.copy(
                    endAt = seg.endAt,
                    durationMs = seg.endAt - prev.startAt
                ))
            } else {
                result.add(seg)
            }
        }
        return result
    }

    private val MOVEMENT_TYPES = setOf(
        SegmentType.WALK, SegmentType.RUN, SegmentType.CYCLE,
        SegmentType.DRIVE, SegmentType.TRANSIT
    )

    private fun SegmentType.isMovement(): Boolean = this in MOVEMENT_TYPES

    /**
     * Merge consecutive VISIT segments at the same place into a single display segment.
     * The Segmenter creates a new segment every 5-min flush cycle, but the user should
     * see one continuous visit entry.
     */
    fun mergeConsecutiveVisits(segments: List<TimelineSegment>): List<TimelineSegment> {
        if (segments.size <= 1) return segments
        val result = mutableListOf<TimelineSegment>()
        var i = 0
        while (i < segments.size) {
            val current = segments[i]
            if (current.type != SegmentType.VISIT) {
                result.add(current)
                i++
                continue
            }
            // Accumulate consecutive VISIT segments, absorbing placeless ones
            var merged = current
            var j = i + 1
            while (j < segments.size) {
                val next = segments[j]
                if (next.type != SegmentType.VISIT) break
                // Merge if: same place, or temporally adjacent artifacts.
                // Placeless merging is restricted by time gap to prevent collapsing
                // distinct unmatched stops into a single phantom visit.
                val gap = next.startAt - merged.endAt
                val mergeable = when {
                    // Same place — always merge (Segmenter flush splits)
                    merged.place?.placeId != null &&
                        merged.place?.placeId == next.place?.placeId -> true
                    // Both placeless — only merge if close in time (< 2 min),
                    // indicating a Segmenter flush artifact, not distinct stops
                    merged.place == null && next.place == null -> gap < 120_000L
                    // One has place, other doesn't — absorb only if very tight (< 1 min)
                    else -> gap < 60_000L
                }
                if (!mergeable) break
                merged = merged.copy(
                    endAt = next.endAt,
                    durationMs = next.endAt - merged.startAt,
                    confidence = maxOf(merged.confidence, next.confidence),
                    place = merged.place ?: next.place,
                    evidence = merged.evidence ?: next.evidence
                )
                j++
            }
            result.add(merged)
            i = j
        }
        return result
    }

    /**
     * Merge consecutive movement segments of the same type (WALK+WALK, DRIVE+DRIVE).
     * The Segmenter flushes every 5 minutes, so a 20-minute walk becomes 4 fragments.
     * A gap tolerance of 2 minutes allows merging across brief pauses mid-walk.
     */
    private fun mergeConsecutiveMovement(segments: List<TimelineSegment>): List<TimelineSegment> {
        if (segments.size <= 1) return segments
        val result = mutableListOf<TimelineSegment>()
        var i = 0
        while (i < segments.size) {
            val current = segments[i]
            if (!current.type.isMovement()) {
                result.add(current)
                i++
                continue
            }
            var merged = current
            var j = i + 1
            while (j < segments.size) {
                val next = segments[j]
                if (next.type != merged.type) break
                // Allow merging across a gap of up to 2 minutes
                val gapMs = next.startAt - merged.endAt
                if (gapMs > 120_000) break

                val mergedRoute = mergeRoutes(merged.route, next.route, next.endAt - merged.startAt)
                merged = merged.copy(
                    endAt = next.endAt,
                    durationMs = next.endAt - merged.startAt,
                    distanceM = merged.distanceM + next.distanceM,
                    confidence = maxOf(merged.confidence, next.confidence),
                    evidence = merged.evidence ?: next.evidence,
                    route = mergedRoute
                )
                j++
            }
            result.add(merged)
            i = j
        }
        return result
    }

    /**
     * Merge consecutive movement segments of ANY transport type into a single unified
     * "travel" segment. The dominant mode (by distance) becomes the segment type.
     * Sub-segments are preserved for evidence display (distance, speed, time per leg).
     * A gap > 5 minutes between movement segments breaks the chain.
     */
    private fun unifyTravelSegments(segments: List<TimelineSegment>): List<TimelineSegment> {
        if (segments.size <= 1) return segments
        val result = mutableListOf<TimelineSegment>()
        var i = 0
        while (i < segments.size) {
            val current = segments[i]
            if (!current.type.isMovement()) {
                result.add(current)
                i++
                continue
            }
            // Accumulate consecutive movement segments (any type)
            val legs = mutableListOf(current)
            var j = i + 1
            while (j < segments.size) {
                val next = segments[j]
                if (!next.type.isMovement()) break
                val gapMs = next.startAt - legs.last().endAt
                if (gapMs > 300_000) break // 5-minute gap breaks the chain
                legs.add(next)
                j++
            }
            if (legs.size == 1) {
                // Single movement segment — no unification needed
                result.add(current)
            } else {
                // Pick dominant type by total distance
                val dominantType = legs.groupBy { it.type }
                    .maxByOrNull { (_, segs) -> segs.sumOf { it.distanceM } }
                    ?.key ?: current.type
                val totalDist = legs.sumOf { it.distanceM }
                val totalDuration = legs.last().endAt - legs.first().startAt
                // Merge all routes
                var mergedRoute: TimelineRoute? = null
                for (leg in legs) {
                    mergedRoute = mergeRoutes(mergedRoute, leg.route, totalDuration)
                }
                result.add(
                    TimelineSegment(
                        segmentId = legs.first().segmentId,
                        type = dominantType,
                        startAt = legs.first().startAt,
                        endAt = legs.last().endAt,
                        durationMs = totalDuration,
                        distanceM = totalDist,
                        confidence = legs.maxOf { it.confidence },
                        evidence = legs.firstOrNull { it.evidence != null }?.evidence,
                        place = null,
                        route = mergedRoute,
                        gapReason = null,
                        isUserCorrected = false,
                        subSegments = legs
                    )
                )
            }
            i = j
        }
        return result
    }

    private fun mergeRoutes(a: TimelineRoute?, b: TimelineRoute?, durationMs: Long): TimelineRoute? {
        if (a == null) return b
        if (b == null) return a
        val mergedPolyline = PolylineEncoder.mergePolylines(
            listOf(a.encodedPolyline, b.encodedPolyline)
        )
        val mergedSimplified = if (a.simplifiedPolyline != null && b.simplifiedPolyline != null) {
            PolylineEncoder.mergePolylines(listOf(a.simplifiedPolyline, b.simplifiedPolyline))
        } else null
        val totalDist = a.totalDistanceM + b.totalDistanceM
        // Calculate speed from actual timestamps rather than back-deriving from
        // per-segment speed/distance (which breaks when either is 0 or coerced)
        val durationSec = durationMs / 1000.0
        return a.copy(
            encodedPolyline = mergedPolyline,
            simplifiedPolyline = mergedSimplified,
            totalDistanceM = totalDist,
            avgSpeedMps = if (durationSec > 0 && totalDist > 0)
                (totalDist / durationSec).toFloat()
            else
                maxOf(a.avgSpeedMps, b.avgSpeedMps)
        )
    }
}
