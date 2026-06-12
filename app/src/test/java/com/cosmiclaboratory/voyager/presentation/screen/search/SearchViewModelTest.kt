package com.cosmiclaboratory.voyager.presentation.screen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchViewModelTest {

    @Test
    fun `toggling into an empty filter adds the item`() {
        assertEquals(setOf("A"), SearchViewModel.toggleFilter(null, "A"))
    }

    @Test
    fun `toggling a new item adds it to the set`() {
        assertEquals(setOf("A", "B"), SearchViewModel.toggleFilter(setOf("A"), "B"))
    }

    @Test
    fun `toggling a present item removes it`() {
        assertEquals(setOf("A"), SearchViewModel.toggleFilter(setOf("A", "B"), "B"))
    }

    @Test
    fun `removing the last item collapses to null (no filter)`() {
        assertNull(SearchViewModel.toggleFilter(setOf("A"), "A"))
    }
}
