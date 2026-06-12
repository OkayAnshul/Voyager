package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.DetectNotableEventsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.DetectNotableEventsUseCase.PlaceForNotable
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectNotableEventsUseCaseTest {

    // The compute* functions are pure; the DAOs are never touched by them.
    private val useCase = DetectNotableEventsUseCase(mockk(), mockk(), mockk())

    private fun visit(id: Long, placeId: Long, arrivalAt: Long) =
        VisitEntity(visitId = id, placeId = placeId, arrivalAt = arrivalAt, source = "LIVE_DETECTION", dayKey = "2026-06-12")

    private fun rollup(dayKey: String, distance: Double) =
        DailyRollupEntity(dayKey = dayKey, totalDistanceM = distance, computedAt = 0L)

    @Test
    fun `a first visit is notable only when it lands in the window`() {
        val cutoff = 1_000_000L
        val places = listOf(PlaceForNotable(1, "Cafe"), PlaceForNotable(2, "Old Spot"), PlaceForNotable(3, "Empty"))
        val visitsByPlace = mapOf(
            1L to listOf(visit(10, 1, cutoff + 500)),                          // first visit after cutoff → notable
            2L to listOf(visit(20, 2, cutoff - 500), visit(21, 2, cutoff + 999)), // first-ever before cutoff → not new
            3L to emptyList()                                                  // never visited → skip
        )
        val out = useCase.computeFirstVisits(places, visitsByPlace, cutoff)
        assertEquals(listOf(1L), out.map { it.placeId })
        assertEquals("Cafe", out.first().placeName)
    }

    @Test
    fun `longest-distance day fires only when the recent best beats the historical max`() {
        val recent = setOf("2026-06-12")
        val beats = listOf(rollup("2026-05-01", 8_000.0), rollup("2026-06-12", 12_000.0))
        assertEquals("2026-06-12", useCase.computeLongestDistanceDay(beats, recent)?.dayKey)

        val doesntBeat = listOf(rollup("2026-05-01", 15_000.0), rollup("2026-06-12", 12_000.0))
        assertNull(useCase.computeLongestDistanceDay(doesntBeat, recent))
    }

    @Test
    fun `no day in the recent window means no distance record`() {
        assertNull(useCase.computeLongestDistanceDay(listOf(rollup("2026-05-01", 9_000.0)), setOf("2026-06-12")))
    }
}
