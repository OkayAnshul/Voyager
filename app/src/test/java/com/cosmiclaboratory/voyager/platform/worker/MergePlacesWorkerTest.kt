package com.cosmiclaboratory.voyager.platform.worker

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic test for the footprint-aware merge distance (T5). */
class MergePlacesWorkerTest {

    @Test
    fun `merge limit is the base radius for a small place`() {
        // An 80m place's footprint (100m) is under the 200m base, so 200m wins.
        assertEquals(200.0, MergePlacesWorker.mergeDistanceLimitM(confirmedRadiusM = 80f), 0.0)
    }

    @Test
    fun `merge limit expands to a large venue's footprint`() {
        // A 400m campus: a same-named fragment up to 420m from its centroid still merges.
        assertEquals(420.0, MergePlacesWorker.mergeDistanceLimitM(confirmedRadiusM = 400f), 0.0)
    }
}
