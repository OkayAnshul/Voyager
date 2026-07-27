package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.TimelineReconciler
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineReconcilerTest {

    private val reconciler = TimelineReconciler()

    private fun place(id: Long) = TimelinePlace(
        placeId = id, displayName = "P$id", nameSource = "Inferred",
        category = PlaceCategory.UNKNOWN, confidence = 0.7f, lat = 0.0, lng = 0.0
    )

    private fun seg(
        type: SegmentType,
        startAt: Long,
        durationMs: Long,
        distanceM: Double = 0.0,
        place: TimelinePlace? = null,
        gapReason: String? = null,
    ) = TimelineSegment(
        segmentId = startAt, type = type, startAt = startAt, endAt = startAt + durationMs,
        durationMs = durationMs, distanceM = distanceM, confidence = 0.7f,
        evidence = null, place = place, route = null, gapReason = gapReason, isUserCorrected = false
    )

    @Test
    fun `collapseGaps coalesces consecutive gaps into one quiet span`() {
        val out = reconciler.collapseGaps(
            listOf(
                seg(SegmentType.GAP, 0, 600_000, gapReason = "DOZE"),
                seg(SegmentType.GAP, 600_000, 600_000, gapReason = "GPS_LOSS"),
            )
        )
        assertEquals(1, out.size)
        assertEquals(1_200_000, out.first().durationMs)
    }

    @Test
    fun `collapseGaps favours the actionable permission reason`() {
        val out = reconciler.collapseGaps(
            listOf(
                seg(SegmentType.GAP, 0, 600_000, gapReason = "DOZE"),
                seg(SegmentType.GAP, 600_000, 60_000, gapReason = "PERMISSION"),
            )
        )
        assertEquals(1, out.size)
        assertEquals("PERMISSION", out.first().gapReason)
    }

    @Test
    fun `collapseGaps leaves a gap between movement untouched`() {
        val out = reconciler.collapseGaps(
            listOf(
                seg(SegmentType.WALK, 0, 60_000, distanceM = 100.0),
                seg(SegmentType.GAP, 60_000, 600_000, gapReason = "DOZE"),
                seg(SegmentType.WALK, 660_000, 60_000, distanceM = 100.0),
            )
        )
        assertEquals(3, out.size)
    }

    @Test
    fun `filterNoise drops transient noise but keeps gaps and real segments`() {
        val kept = reconciler.filterNoise(
            listOf(
                seg(SegmentType.UNKNOWN_MOTION, 0, 120_000),              // <5min → drop
                seg(SegmentType.VISIT, 200_000, 60_000),                  // placeless <3min → drop
                seg(SegmentType.WALK, 300_000, 30_000, distanceM = 5.0),  // <1min & <50m → drop
                seg(SegmentType.GAP, 400_000, 10_000),                    // always keep
                seg(SegmentType.VISIT, 500_000, 60_000, place = place(1)),// has place → keep
                seg(SegmentType.WALK, 600_000, 30_000, distanceM = 100.0),// ≥50m → keep
            )
        )
        assertEquals(listOf(SegmentType.GAP, SegmentType.VISIT, SegmentType.WALK), kept.map { it.type })
    }

    @Test
    fun `mergeConsecutiveVisits collapses same-place flush fragments into one`() {
        val merged = reconciler.mergeConsecutiveVisits(
            listOf(
                seg(SegmentType.VISIT, 0, 300_000, place = place(1)),
                seg(SegmentType.VISIT, 300_000, 300_000, place = place(1)),
            )
        )
        assertEquals(1, merged.size)
        assertEquals(600_000, merged.first().durationMs)
    }

    @Test
    fun `mergeConsecutiveVisits keeps distinct placeless stops apart when the time gap is large`() {
        val merged = reconciler.mergeConsecutiveVisits(
            listOf(
                seg(SegmentType.VISIT, 0, 60_000),          // placeless
                seg(SegmentType.VISIT, 200_000, 60_000),    // 140s gap > 2min rule
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `reconcile coalesces consecutive same-type movement fragments`() {
        val out = reconciler.reconcile(
            listOf(
                seg(SegmentType.WALK, 0, 300_000, distanceM = 200.0),
                seg(SegmentType.WALK, 300_000, 300_000, distanceM = 300.0),
            )
        )
        assertEquals(1, out.size)
        assertEquals(SegmentType.WALK, out.first().type)
        assertEquals(500.0, out.first().distanceM, 1e-6)
        assertEquals(600_000, out.first().durationMs)
    }

    @Test
    fun `reconcile with unifyTravel merges mixed modes into one segment of the dominant mode`() {
        val out = reconciler.reconcile(
            listOf(
                seg(SegmentType.WALK, 0, 300_000, distanceM = 200.0),
                seg(SegmentType.DRIVE, 300_000, 300_000, distanceM = 5_000.0),
            ),
            unifyTravel = true
        )
        assertEquals(1, out.size)
        assertEquals(SegmentType.DRIVE, out.first().type)           // dominant by distance
        assertEquals(5_200.0, out.first().distanceM, 1e-6)
        assertEquals(2, out.first().subSegments?.size)              // legs preserved for evidence
    }
}
