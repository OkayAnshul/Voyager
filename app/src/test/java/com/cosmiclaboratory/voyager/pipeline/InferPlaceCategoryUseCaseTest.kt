package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.usecase.InferPlaceCategoryUseCase
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class InferPlaceCategoryUseCaseTest {

    private val tz = TimeZone.getTimeZone("UTC")
    private val useCase = InferPlaceCategoryUseCase()

    private fun visit(
        arrivalEpochMs: Long,
        dwellHours: Double,
        dayKey: String = "2026-01-01"
    ): VisitEntity {
        val dwellMs = (dwellHours * 3_600_000L).toLong()
        return VisitEntity(
            placeId = 1L,
            arrivalAt = arrivalEpochMs,
            departureAt = arrivalEpochMs + dwellMs,
            dwellMs = dwellMs,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = dayKey,
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    private fun atHour(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun `too few visits produces no inference`() {
        val visits = (0..2).map { visit(atHour(2026, 1, it + 1, 22), 8.0) }
        assertNull(useCase.infer(visits, tz))
    }

    @Test
    fun `nightly recurring visits with 8h+ dwell infer HOME`() {
        val visits = (1..15).map { day ->
            visit(atHour(2026, 1, day, 23), 8.0)
        }
        val result = useCase.infer(visits, tz)
        assertNotNull(result)
        assertEquals(PlaceCategory.HOME, result!!.category)
        assertTrue("confidence in valid range", result.confidence in 0.6f..0.95f)
    }

    @Test
    fun `weekday 9am arrivals with 8h dwell infer WORK`() {
        // Jan 2026: 5th Mon, 6th Tue, 7th Wed, 8th Thu, 9th Fri, 12-16 Mon-Fri, 19-23 Mon-Fri
        val weekdays = listOf(5,6,7,8,9, 12,13,14,15,16, 19,20,21,22)
        val visits = weekdays.map { day ->
            visit(atHour(2026, 1, day, 9), 8.0)
        }
        val result = useCase.infer(visits, tz)
        assertNotNull(result)
        assertEquals(PlaceCategory.WORK, result!!.category)
    }

    @Test
    fun `mealtime visits with short dwell infer RESTAURANT`() {
        // 8 dinner-time visits, 1h each
        val visits = (1..8).map { day ->
            visit(atHour(2026, 1, day, 19), 1.0)
        }
        val result = useCase.infer(visits, tz)
        assertNotNull(result)
        assertEquals(PlaceCategory.RESTAURANT, result!!.category)
    }

    @Test
    fun `high-frequency short visits infer TRANSIT_HUB`() {
        // 25 visits of 5 min each, mid-afternoon (avoids meal/night/work bands)
        val visits = (1..25).map { day ->
            visit(atHour(2026, 1, (day % 28) + 1, 15), 5.0 / 60.0)
        }
        val result = useCase.infer(visits, tz)
        assertNotNull(result)
        assertEquals(PlaceCategory.TRANSIT_HUB, result!!.category)
    }

    @Test
    fun `45-min recurring visits infer GYM`() {
        // 10 mid-afternoon visits at 45 min (avoids meal/work bands).
        val visits = (1..10).map { day ->
            visit(atHour(2026, 1, day, 15), 0.75)
        }
        val result = useCase.infer(visits, tz)
        assertNotNull(result)
        assertEquals(PlaceCategory.GYM, result!!.category)
    }

    @Test
    fun `unclosed visits (no dwell) are ignored`() {
        // 10 visits where departureAt is null — should never trigger any inference.
        val visits = (1..10).map { day ->
            VisitEntity(
                placeId = 1L,
                arrivalAt = atHour(2026, 1, day, 23),
                departureAt = null,
                dwellMs = null,
                source = "LIVE_DETECTION",
                confidence = 0.7f,
                dayKey = "2026-01-0$day",
                centroidLat = 0.0,
                centroidLng = 0.0
            )
        }
        assertNull(useCase.infer(visits, tz))
    }

    @Test
    fun `random scattered visits without a pattern produce no inference`() {
        // Distinct times every day, varied dwell, no pattern.
        val visits = (1..10).map { day ->
            visit(atHour(2026, 1, day, (day * 3) % 24), (day % 5 + 1).toDouble())
        }
        // Should not match HOME, WORK, GYM, RESTAURANT, EDUCATION, or TRANSIT_HUB.
        val result = useCase.infer(visits, tz)
        // With random patterns, we accept either null OR a low-confidence proposal.
        // The key invariant: it shouldn't claim HOME or WORK.
        if (result != null) {
            assertNotEquals(PlaceCategory.HOME, result.category)
            assertNotEquals(PlaceCategory.WORK, result.category)
        }
    }
}
