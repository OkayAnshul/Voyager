package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.TimelineReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReviewTest {

    private fun place(id: Long, category: PlaceCategory, confidence: Float) = TimelinePlace(
        placeId = id, displayName = "P$id", nameSource = "Inferred",
        category = category, confidence = confidence, lat = 0.0, lng = 0.0
    )

    private fun visit(place: TimelinePlace?, startAt: Long = 0) = TimelineSegment(
        segmentId = startAt, type = SegmentType.VISIT, startAt = startAt, endAt = startAt + 1000,
        durationMs = 1000, distanceM = 0.0, confidence = 0.9f,
        evidence = null, place = place, route = null, gapReason = null, isUserCorrected = false
    )

    private fun walk(startAt: Long = 0) = TimelineSegment(
        segmentId = startAt, type = SegmentType.WALK, startAt = startAt, endAt = startAt + 1000,
        durationMs = 1000, distanceM = 100.0, confidence = 0.2f,
        evidence = null, place = null, route = null, gapReason = null, isUserCorrected = false
    )

    @Test
    fun `confident named visit needs no review`() {
        assertFalse(TimelineReview.isReviewable(visit(place(1, PlaceCategory.HOME, 0.9f))))
    }

    @Test
    fun `unknown-category visit needs review`() {
        assertTrue(TimelineReview.isReviewable(visit(place(2, PlaceCategory.UNKNOWN, 0.9f))))
    }

    @Test
    fun `low-confidence visit needs review`() {
        assertTrue(TimelineReview.isReviewable(visit(place(3, PlaceCategory.RESTAURANT, 0.5f))))
    }

    @Test
    fun `placeless visit needs review`() {
        assertTrue(TimelineReview.isReviewable(visit(null)))
    }

    @Test
    fun `low-confidence movement is not a confirm candidate`() {
        assertFalse(TimelineReview.isReviewable(walk()))
    }

    @Test
    fun `reviewCount counts only reviewable visits`() {
        val segments = listOf(
            visit(place(1, PlaceCategory.HOME, 0.9f), 0),     // ok
            visit(place(2, PlaceCategory.UNKNOWN, 0.9f), 10), // review
            visit(null, 20),                                  // review
            walk(30),                                         // not counted
        )
        assertEquals(2, TimelineReview.reviewCount(segments))
    }
}
