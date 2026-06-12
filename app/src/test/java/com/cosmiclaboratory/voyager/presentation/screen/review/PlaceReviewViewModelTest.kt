package com.cosmiclaboratory.voyager.presentation.screen.review

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceReviewViewModelTest {

    private fun place(id: Long, confidence: Float, category: PlaceCategory = PlaceCategory.HOME) =
        TimelinePlace(
            placeId = id, displayName = "P$id", nameSource = "Inferred",
            category = category, confidence = confidence, lat = 0.0, lng = 0.0
        )

    @Test
    fun `low-confidence and uncategorised places are queued, confident known places are not`() {
        val out = PlaceReviewViewModel.pendingReviewPlaces(
            listOf(
                place(1, 0.5f),                                   // low confidence → queue
                place(2, 0.95f),                                  // confident + known → skip
                place(3, 0.99f, category = PlaceCategory.UNKNOWN) // confident but uncategorised → queue
            )
        )
        assertEquals(listOf(1L, 3L), out.map { it.placeId })
    }

    @Test
    fun `the queue is ordered most-uncertain first`() {
        val out = PlaceReviewViewModel.pendingReviewPlaces(
            listOf(place(1, 0.65f), place(2, 0.2f), place(3, 0.5f))
        )
        assertEquals(listOf(2L, 3L, 1L), out.map { it.placeId }) // 0.2 < 0.5 < 0.65
    }
}
