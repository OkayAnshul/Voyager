package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DetectRoutineBreaksUseCaseTest {

    private val useCase = DetectRoutineBreaksUseCase(
        detectRecurringPatterns = mockk(relaxed = true),
        visitDao = mockk<VisitDao>(relaxed = true),
        placeDao = mockk<PlaceDao>(relaxed = true),
    )

    private fun pattern(placeId: Long, hour: Int, confidence: Float = 0.8f) = RecurringPattern(
        placeId = placeId,
        dayOfWeek = Calendar.TUESDAY,
        typicalHour = hour,
        visitCount = 6,
        arrivalHourStdDev = 0.5f,
        confidence = confidence
    )

    private val names = mapOf(1L to "Gym")

    @Test
    fun `missed once the usual hour is well past`() {
        val breaks = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 19)),
            placeNames = names,
            visitHoursTodayByPlace = mapOf(1L to emptyList()),
            todayHour = 23,          // 19 + 3 grace = 22, so 23 counts
            dayKey = "2026-01-06"
        )
        assertEquals(1, breaks.size)
        assertEquals(RoutineBreak.Kind.MISSED, breaks.single().kind)
    }

    @Test
    fun `not yet missed before the grace window elapses`() {
        val breaks = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 19)),
            placeNames = names,
            visitHoursTodayByPlace = mapOf(1L to emptyList()),
            todayHour = 20,          // still before 22
            dayKey = "2026-01-06"
        )
        assertTrue(breaks.isEmpty())
    }

    @Test
    fun `a visit far from the usual hour reads as late`() {
        val breaks = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 19)),
            placeNames = names,
            visitHoursTodayByPlace = mapOf(1L to listOf(23)), // +4h
            todayHour = 23,
            dayKey = "2026-01-06"
        )
        assertEquals(RoutineBreak.Kind.LATE, breaks.single().kind)
    }

    @Test
    fun `a visit at the usual hour is not a break`() {
        val breaks = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 19)),
            placeNames = names,
            visitHoursTodayByPlace = mapOf(1L to listOf(19)),
            todayHour = 23,
            dayKey = "2026-01-06"
        )
        assertTrue(breaks.isEmpty())
    }

    @Test
    fun `low-confidence patterns are ignored`() {
        val breaks = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 19, confidence = 0.5f)),
            placeNames = names,
            visitHoursTodayByPlace = mapOf(1L to emptyList()),
            todayHour = 23,
            dayKey = "2026-01-06"
        )
        assertTrue(breaks.isEmpty())
    }
}
