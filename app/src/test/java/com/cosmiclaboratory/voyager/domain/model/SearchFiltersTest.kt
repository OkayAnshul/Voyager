package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchFilters.isEmpty] gates the search repository between the fast FTS text path (empty) and
 * the structured-filter path (non-empty), and lets the ViewModel run a filter-only search, so its
 * behaviour is worth pinning down.
 */
class SearchFiltersTest {

    @Test
    fun `default filters are empty`() {
        assertTrue(SearchFilters().isEmpty())
    }

    @Test
    fun `an empty category set counts as empty`() {
        assertTrue(SearchFilters(placeCategories = emptySet()).isEmpty())
    }

    @Test
    fun `a date range makes it non-empty`() {
        assertFalse(SearchFilters(dateRange = DateRange("2026-06-01", "2026-06-30")).isEmpty())
    }

    @Test
    fun `a category makes it non-empty`() {
        assertFalse(SearchFilters(placeCategories = setOf(PlaceCategory.RESTAURANT)).isEmpty())
    }

    @Test
    fun `a transport mode makes it non-empty`() {
        assertFalse(SearchFilters(transportModes = setOf(SegmentType.WALK)).isEmpty())
    }
}
