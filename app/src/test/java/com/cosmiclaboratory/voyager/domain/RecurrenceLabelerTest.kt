package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.RecurrenceLabeler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceLabelerTest {

    @Test
    fun `high repeatability is part of your routine`() {
        assertEquals("Part of your routine", RecurrenceLabeler.label(0.8f, 0, 0))
    }

    @Test
    fun `five visits in a week is part of your routine even with low score`() {
        assertEquals("Part of your routine", RecurrenceLabeler.label(0.0f, 5, 5))
    }

    @Test
    fun `mid frequency reads as often`() {
        assertEquals("You come here often", RecurrenceLabeler.label(0.5f, 0, 0))
        assertEquals("You come here often", RecurrenceLabeler.label(0.0f, 3, 4))
    }

    @Test
    fun `monthly regulars are a regular spot`() {
        assertEquals("Regular spot", RecurrenceLabeler.label(0.0f, 1, 6))
    }

    @Test
    fun `one-off stop gets no label`() {
        assertNull(RecurrenceLabeler.label(0.0f, 1, 1))
    }
}
