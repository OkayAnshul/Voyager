package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PredictNextPlaceUseCaseTest {

    private val useCase = PredictNextPlaceUseCase(
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

    @Test
    fun `later-today patterns not yet done are surfaced in time order`() {
        val upcoming = useCase.detect(
            todaysPatterns = listOf(pattern(2L, 19), pattern(1L, 17)),
            placeNames = mapOf(1L to "Cafe", 2L to "Gym"),
            visitHoursTodayByPlace = emptyMap(),
            currentHour = 15
        )
        assertEquals(listOf(1L, 2L), upcoming.map { it.placeId }) // 17:00 before 19:00
    }

    @Test
    fun `past patterns are not upcoming`() {
        val upcoming = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 10)),
            placeNames = mapOf(1L to "Cafe"),
            visitHoursTodayByPlace = emptyMap(),
            currentHour = 15
        )
        assertTrue(upcoming.isEmpty())
    }

    @Test
    fun `an already-completed routine is dropped`() {
        val upcoming = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 17)),
            placeNames = mapOf(1L to "Cafe"),
            visitHoursTodayByPlace = mapOf(1L to listOf(17)), // already went
            currentHour = 15
        )
        assertTrue(upcoming.isEmpty())
    }

    @Test
    fun `low-confidence patterns are not predicted`() {
        val upcoming = useCase.detect(
            todaysPatterns = listOf(pattern(1L, 19, confidence = 0.4f)),
            placeNames = mapOf(1L to "Cafe"),
            visitHoursTodayByPlace = emptyMap(),
            currentHour = 15
        )
        assertTrue(upcoming.isEmpty())
    }
}
