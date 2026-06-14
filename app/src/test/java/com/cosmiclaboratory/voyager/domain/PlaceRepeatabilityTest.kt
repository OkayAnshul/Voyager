package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.PlaceRepeatability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRepeatabilityTest {

    @Test
    fun `a never-visited place scores zero`() {
        assertEquals(0f, PlaceRepeatability.score(totalVisitCount = 0, visitCountLast30d = 0), 1e-6f)
    }

    @Test
    fun `a frequent recent haunt scores high`() {
        // 8+ visits in 30 days saturates recency (0.7); total 20+ saturates established (0.3).
        assertEquals(1f, PlaceRepeatability.score(totalVisitCount = 30, visitCountLast30d = 10), 1e-6f)
    }

    @Test
    fun `recency dominates established history`() {
        val recentOnly = PlaceRepeatability.score(totalVisitCount = 2, visitCountLast30d = 8)   // recency 1
        val establishedOnly = PlaceRepeatability.score(totalVisitCount = 20, visitCountLast30d = 0) // established 1
        assertTrue("recent should outweigh established", recentOnly > establishedOnly)
        assertEquals(0.3f, establishedOnly, 1e-6f) // 0.3 weight
    }

    @Test
    fun `score is clamped to 0 and 1`() {
        assertEquals(1f, PlaceRepeatability.score(totalVisitCount = 9999, visitCountLast30d = 9999), 1e-6f)
        assertTrue(PlaceRepeatability.score(0, 0) in 0f..1f)
    }
}
