package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.RetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    private val now = 1_000_000_000_000L
    private val day = RetentionPolicy.MS_PER_DAY

    @Test
    fun `a positive window cuts off that many days before now`() {
        assertEquals(now - 90 * day, RetentionPolicy.cutoffMs(now, 90))
        assertEquals(now - 365 * day, RetentionPolicy.cutoffMs(now, 365))
    }

    @Test
    fun `a zero window cuts off at now`() {
        assertEquals(now, RetentionPolicy.cutoffMs(now, 0))
    }

    @Test
    fun `a negative window means keep forever — null cutoff, never a future cutoff`() {
        // The whole point: -1 must NOT become now + 1 day (which would delete everything).
        assertNull(RetentionPolicy.cutoffMs(now, -1))
        assertTrue(RetentionPolicy.keepsForever(-1))
        assertFalse(RetentionPolicy.keepsForever(0))
        assertFalse(RetentionPolicy.keepsForever(365))
    }

    @Test
    fun `raw retention is unchanged for a small database`() {
        assertEquals(90, RetentionPolicy.effectiveRawRetentionDays(configuredDays = 90, dbSizeMb = 100))
    }

    @Test
    fun `raw retention trims moderately past 500 MB`() {
        // min(60, 90) = 60
        assertEquals(60, RetentionPolicy.effectiveRawRetentionDays(configuredDays = 90, dbSizeMb = 600))
        // already tighter than the cap → unchanged
        assertEquals(45, RetentionPolicy.effectiveRawRetentionDays(configuredDays = 45, dbSizeMb = 600))
    }

    @Test
    fun `raw retention trims aggressively past 1 GB`() {
        // min(30, 90) = 30
        assertEquals(30, RetentionPolicy.effectiveRawRetentionDays(configuredDays = 90, dbSizeMb = 2000))
    }

    @Test
    fun `the size trim never lengthens a window shorter than the cap`() {
        // 20 days configured, huge DB → stays 20 (trim only shortens).
        assertEquals(20, RetentionPolicy.effectiveRawRetentionDays(configuredDays = 20, dbSizeMb = 5000))
    }

    @Test
    fun `a forever raw setting survives the size trim`() {
        // The user's explicit "keep forever" wins even when the DB is huge.
        assertEquals(-1, RetentionPolicy.effectiveRawRetentionDays(configuredDays = -1, dbSizeMb = 5000))
        assertNull(RetentionPolicy.cutoffMs(now, RetentionPolicy.effectiveRawRetentionDays(-1, 5000)))
    }
}
