package com.cosmiclaboratory.voyager.platform.coordinator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSnapshotTest {

    @Test
    fun `accuracyTag is fine whenever fine is granted, regardless of background or AR`() {
        // Regression: a fully-permissioned sample used to be mislabelled "none".
        val full = PermissionSnapshot(
            hasFineLocation = true, hasCoarseLocation = true, hasBackgroundLocation = true,
            hasActivityRecognition = true, hasNotifications = true, isBatteryOptimizationExempt = true
        )
        assertEquals("fine", full.accuracyTag)

        val fineForegroundOnly = PermissionSnapshot(hasFineLocation = true, hasCoarseLocation = true)
        assertEquals("fine", fineForegroundOnly.accuracyTag)
    }

    @Test
    fun `accuracyTag is coarse only when coarse-without-fine`() {
        val coarse = PermissionSnapshot(hasFineLocation = false, hasCoarseLocation = true)
        assertEquals("coarse", coarse.accuracyTag)
    }

    @Test
    fun `accuracyTag is none with no location grant`() {
        assertEquals("none", PermissionSnapshot().accuracyTag)
    }

    @Test
    fun `isApproximateLocationOnly is true only for coarse-without-fine`() {
        assertTrue(PermissionSnapshot(hasCoarseLocation = true).isApproximateLocationOnly)
        assertFalse(PermissionSnapshot(hasFineLocation = true, hasCoarseLocation = true).isApproximateLocationOnly)
        assertFalse(PermissionSnapshot().isApproximateLocationOnly)
    }

    @Test
    fun `isComplete requires every permission`() {
        val complete = PermissionSnapshot(
            hasFineLocation = true, hasCoarseLocation = true, hasBackgroundLocation = true,
            hasActivityRecognition = true, hasNotifications = true, isBatteryOptimizationExempt = true
        )
        assertTrue(complete.isComplete)
        assertEquals(0, complete.missingCount)
        assertFalse(complete.copy(isBatteryOptimizationExempt = false).isComplete)
        assertEquals(1, complete.copy(isBatteryOptimizationExempt = false).missingCount)
    }
}
