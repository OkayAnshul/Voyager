package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Verifies [BuildOnThisDayUseCase] surfaces past-year days correctly. */
class BuildOnThisDayUseCaseTest {

    private val dao = mockk<DailyRollupDao>()
    private val useCase = BuildOnThisDayUseCase(dao)

    private fun rollup(dayKey: String, visits: Int, distanceM: Double) = DailyRollupEntity(
        dayKey = dayKey,
        totalDistanceM = distanceM,
        totalSteps = 1000,
        placeVisitCount = visits,
        uniquePlacesVisited = visits,
        computedAt = 0L
    )

    @Test
    fun `surfaces a non-empty day from one year ago`() = runTest {
        coEvery { dao.getByDayKey(any()) } returns null
        coEvery { dao.getByDayKey("2025-05-19") } returns rollup("2025-05-19", visits = 3, distanceM = 4200.0)

        val result = useCase.build(LocalDate.of(2026, 5, 19))

        assertEquals(1, result.size)
        assertEquals(1, result[0].yearsAgo)
        assertEquals("2025-05-19", result[0].dayKey)
        assertEquals(3, result[0].placeVisitCount)
    }

    @Test
    fun `skips empty days`() = runTest {
        coEvery { dao.getByDayKey(any()) } returns null
        coEvery { dao.getByDayKey("2025-05-19") } returns rollup("2025-05-19", visits = 0, distanceM = 0.0)

        assertTrue(useCase.build(LocalDate.of(2026, 5, 19)).isEmpty())
    }

    @Test
    fun `surfaces multiple years newest first`() = runTest {
        coEvery { dao.getByDayKey(any()) } returns null
        coEvery { dao.getByDayKey("2025-05-19") } returns rollup("2025-05-19", 3, 1000.0)
        coEvery { dao.getByDayKey("2023-05-19") } returns rollup("2023-05-19", 5, 2000.0)

        val result = useCase.build(LocalDate.of(2026, 5, 19))

        assertEquals(listOf(1, 3), result.map { it.yearsAgo })
    }

    @Test
    fun `no rollups means no memories`() = runTest {
        coEvery { dao.getByDayKey(any()) } returns null
        assertTrue(useCase.build(LocalDate.of(2026, 5, 19)).isEmpty())
    }
}
