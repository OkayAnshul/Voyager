package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeExplorationScoreUseCaseTest {

    private val useCase = ComputeExplorationScoreUseCase(mockk<VisitDao>(relaxed = true))

    @Test
    fun `new places are weighted more heavily than repeat places`() {
        assertEquals(30, useCase.score(uniquePlaces = 5, newPlaces = 0))   // 5*6
        assertEquals(54, useCase.score(uniquePlaces = 5, newPlaces = 2))   // 5*6 + 2*12
    }

    @Test
    fun `score is capped at 100`() {
        assertEquals(100, useCase.score(uniquePlaces = 40, newPlaces = 10))
    }

    @Test
    fun `an empty period scores zero`() {
        assertEquals(0, useCase.score(uniquePlaces = 0, newPlaces = 0))
    }
}
