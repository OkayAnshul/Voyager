package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType

/**
 * Pure predicate for the timeline's "needs confirm" flow (Arc-style). A visit is
 * reviewable when its place is unknown or low-confidence — those are the rows that
 * benefit from a one-tap confirm/rename. Movement is corrected via reclassify, not
 * confirm, so it is intentionally excluded here.
 */
object TimelineReview {

    fun isReviewable(segment: TimelineSegment, threshold: Float = 0.7f): Boolean =
        segment.type == SegmentType.VISIT &&
            (segment.place == null || segment.place.needsReview(threshold))

    fun reviewCount(segments: List<TimelineSegment>, threshold: Float = 0.7f): Int =
        segments.count { isReviewable(it, threshold) }
}
