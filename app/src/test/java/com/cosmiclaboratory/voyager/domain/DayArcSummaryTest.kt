package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.DayArcSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayArcSummaryTest {

    private fun seg(type: SegmentType, startAt: Long, durationMs: Long, distanceM: Double = 0.0) =
        TimelineSegment(
            segmentId = startAt, type = type, startAt = startAt, endAt = startAt + durationMs,
            durationMs = durationMs, distanceM = distanceM, confidence = 0.7f,
            evidence = null, place = null, route = null, gapReason = null, isUserCorrected = false
        )

    @Test
    fun `dominant mode is the most-travelled by distance`() {
        val s = DayArcSummary.summarize(
            listOf(
                seg(SegmentType.WALK, 0, 600_000, distanceM = 500.0),
                seg(SegmentType.DRIVE, 600_000, 600_000, distanceM = 12_000.0),
            )
        )
        assertEquals(SegmentType.DRIVE, s.dominantMode)
    }

    @Test
    fun `span covers first start to last end`() {
        val s = DayArcSummary.summarize(
            listOf(
                seg(SegmentType.VISIT, 1_000, 1_000),
                seg(SegmentType.WALK, 5_000, 2_000, distanceM = 100.0),
            )
        )
        assertEquals(1_000L, s.firstActivityAt)
        assertEquals(7_000L, s.lastActivityAt)
    }

    @Test
    fun `counts places and trips and emits a slice per non-empty segment`() {
        val s = DayArcSummary.summarize(
            listOf(
                seg(SegmentType.VISIT, 0, 1_000),
                seg(SegmentType.WALK, 1_000, 1_000, distanceM = 100.0),
                seg(SegmentType.GAP, 2_000, 1_000),
                seg(SegmentType.VISIT, 3_000, 0), // zero-duration → no slice
            )
        )
        assertEquals(2, s.visitCount)
        assertEquals(1, s.tripCount)
        assertEquals(3, s.slices.size)
    }

    @Test
    fun `all-stationary day has no dominant mode`() {
        val s = DayArcSummary.summarize(listOf(seg(SegmentType.VISIT, 0, 1_000)))
        assertNull(s.dominantMode)
    }
}
