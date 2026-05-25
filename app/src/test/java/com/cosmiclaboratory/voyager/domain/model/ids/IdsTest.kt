package com.cosmiclaboratory.voyager.domain.model.ids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Confirms the typed-id wrappers round-trip and compare by value. Type-distinctness
 * (a [VisitId] can't be passed where a [PlaceId] is expected) is enforced by the
 * compiler, so it can't be asserted at runtime.
 */
class IdsTest {

    @Test
    fun `wraps and unwraps the raw value`() {
        assertEquals(5L, PlaceId(5L).raw)
        assertEquals(7L, 7L.asPlaceId().raw)
        assertEquals(VisitId(3L), 3L.asVisitId())
        assertEquals(SegmentId(8L), 8L.asSegmentId())
        assertEquals(RouteId(2L), 2L.asRouteId())
    }

    @Test
    fun `equals by underlying value`() {
        assertEquals(PlaceId(9L), PlaceId(9L))
        assertNotEquals(PlaceId(1L), PlaceId(2L))
    }
}
