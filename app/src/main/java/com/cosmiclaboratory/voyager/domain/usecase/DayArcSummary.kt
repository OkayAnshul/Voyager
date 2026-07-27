package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType

/**
 * Pure, Android-free derivation of the day-overview header from the already-loaded
 * timeline segments. Computing from in-memory segments (rather than the 3 AM daily
 * rollup) keeps the header live-correct for today.
 */
object DayArcSummary {

    /** Coarse visual category for a slice of the day-arc bar. */
    enum class ArcKind { VISIT, WALK, RUN, CYCLE, DRIVE, TRANSIT, FLIGHT, GAP, OTHER }

    /** One proportional slice of the day's 24h arc (fraction is the slice duration). */
    data class Slice(val fraction: Float, val kind: ArcKind)

    data class Summary(
        val slices: List<Slice>,
        /** Most-travelled movement mode by distance, or null when the day is all visits. */
        val dominantMode: SegmentType?,
        val firstActivityAt: Long?,
        val lastActivityAt: Long?,
        val visitCount: Int,
        val tripCount: Int
    )

    private val MOVEMENT = setOf(
        SegmentType.WALK, SegmentType.RUN, SegmentType.CYCLE,
        SegmentType.DRIVE, SegmentType.TRANSIT, SegmentType.FLIGHT, SegmentType.UNKNOWN_MOTION
    )

    fun summarize(segments: List<TimelineSegment>): Summary {
        val slices = segments
            .filter { it.durationMs > 0 }
            .map { Slice(it.durationMs.toFloat(), it.type.toArcKind()) }

        val dominantMode = segments
            .filter { it.type in MOVEMENT && it.type != SegmentType.UNKNOWN_MOTION }
            .groupBy { it.type }
            .maxByOrNull { (_, segs) -> segs.sumOf { it.distanceM } }
            ?.key

        return Summary(
            slices = slices,
            dominantMode = dominantMode,
            firstActivityAt = segments.minOfOrNull { it.startAt },
            lastActivityAt = segments.maxOfOrNull { it.endAt },
            visitCount = segments.count { it.type == SegmentType.VISIT || it.type == SegmentType.DWELL },
            tripCount = segments.count { it.type in MOVEMENT }
        )
    }

    private fun SegmentType.toArcKind(): ArcKind = when (this) {
        SegmentType.VISIT, SegmentType.DWELL -> ArcKind.VISIT
        SegmentType.WALK -> ArcKind.WALK
        SegmentType.RUN -> ArcKind.RUN
        SegmentType.CYCLE -> ArcKind.CYCLE
        SegmentType.DRIVE -> ArcKind.DRIVE
        SegmentType.TRANSIT -> ArcKind.TRANSIT
        SegmentType.FLIGHT -> ArcKind.FLIGHT
        SegmentType.GAP -> ArcKind.GAP
        SegmentType.UNKNOWN_MOTION -> ArcKind.OTHER
    }
}
