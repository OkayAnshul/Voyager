package com.cosmiclaboratory.voyager.platform.worker

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic tests for [DiscoverPlacesWorker]'s confidence + cluster-radius helpers. */
class DiscoverPlacesWorkerTest {

    @Test
    fun `POI prior lifts confidence to the floor`() {
        // A weak cluster (low base) that lands on a named OSM POI is still trusted.
        assertEquals(
            DiscoverPlacesWorker.POI_PRIOR_CONFIDENCE,
            DiscoverPlacesWorker.resolvePlaceConfidence(
                baseConfidence = 0.3f, isPoiPrior = true, roughMode = false
            )
        )
    }

    @Test
    fun `POI prior never lowers an already-high confidence`() {
        assertEquals(
            0.95f,
            DiscoverPlacesWorker.resolvePlaceConfidence(
                baseConfidence = 0.95f, isPoiPrior = true, roughMode = false
            )
        )
    }

    @Test
    fun `without a POI prior confidence is the cluster baseline`() {
        assertEquals(
            0.6f,
            DiscoverPlacesWorker.resolvePlaceConfidence(
                baseConfidence = 0.6f, isPoiPrior = false, roughMode = false
            )
        )
    }

    @Test
    fun `rough mode caps confidence even with a POI prior`() {
        assertEquals(
            0.5f,
            DiscoverPlacesWorker.resolvePlaceConfidence(
                baseConfidence = 0.9f, isPoiPrior = true, roughMode = true
            )
        )
    }

    @Test
    fun `cluster radius widens in rough mode`() {
        assertEquals(80.0, DiscoverPlacesWorker.clusterRadiusFor(roughMode = false), 0.0)
        assertEquals(2_000.0, DiscoverPlacesWorker.clusterRadiusFor(roughMode = true), 0.0)
    }
}
