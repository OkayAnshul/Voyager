package com.cosmiclaboratory.voyager.platform

import com.cosmiclaboratory.voyager.platform.coordinator.PermissionSnapshot
import com.cosmiclaboratory.voyager.platform.worker.DiscoverPlacesWorker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for rough-timeline mode: detecting the approximate-location grant and the
 * city-scale clustering radius it switches [DiscoverPlacesWorker] to.
 */
class RoughModeTest {

    @Test
    fun `coarse-only grant is approximate location only`() {
        val snapshot = PermissionSnapshot(hasFineLocation = false, hasCoarseLocation = true)
        assertThat(snapshot.isApproximateLocationOnly).isTrue()
    }

    @Test
    fun `precise grant is not approximate only`() {
        val snapshot = PermissionSnapshot(hasFineLocation = true, hasCoarseLocation = true)
        assertThat(snapshot.isApproximateLocationOnly).isFalse()
    }

    @Test
    fun `no location grant is not approximate only`() {
        assertThat(PermissionSnapshot().isApproximateLocationOnly).isFalse()
    }

    @Test
    fun `rough mode clusters at a city scale, precise mode stays tight`() {
        val precise = DiscoverPlacesWorker.clusterRadiusFor(roughMode = false)
        val rough = DiscoverPlacesWorker.clusterRadiusFor(roughMode = true)

        assertThat(precise).isEqualTo(80.0)
        assertThat(rough).isEqualTo(2_000.0)
        assertThat(rough).isGreaterThan(precise)
    }
}
