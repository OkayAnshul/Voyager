package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.usecase.FuseActivityStateUseCase
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for FuseActivityStateUseCase — multi-signal activity fusion.
 *
 * The fuser combines AR, speed heuristic, and step rate into a single activity type.
 * These tests ensure the fusion logic correctly handles all input combinations
 * and doesn't produce UNKNOWN when real data is available.
 */
class FuseActivityStateUseCaseTest {

    private val useCase = FuseActivityStateUseCase()

    // ── Speed heuristic bands ──

    @Test
    fun `speed below 0_3 mps is STILL`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 0.1f, stepRatePerMinute = null)
        assertEquals(ActivityType.STILL, result.activityType)
    }

    @Test
    fun `speed 0_3 to 1_3 mps is WALKING`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 1.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.WALKING, result.activityType)
    }

    @Test
    fun `speed 2_1 to 3_0 mps is RUNNING`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 2.5f, stepRatePerMinute = null)
        assertEquals(ActivityType.RUNNING, result.activityType)
    }

    @Test
    fun `speed 4_5 to 6_5 mps is CYCLING`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    @Test
    fun `speed above 8_5 mps is IN_VEHICLE`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 15.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.IN_VEHICLE, result.activityType)
    }

    // ── Step rate overrides ──

    @Test
    fun `high step rate overrides to RUNNING`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = 160f)
        assertEquals(ActivityType.RUNNING, result.activityType)
    }

    @Test
    fun `moderate step rate indicates WALKING`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = 100f)
        assertEquals(ActivityType.WALKING, result.activityType)
    }

    @Test
    fun `very low step rate with low AR confidence overrides to STILL`() {
        // Default weights: AR=0.6, speed=0.25, step=0.15.
        // AR 10f → 10/100 * 0.6 = 0.06 for WALKING
        // Step 2f < 5 with AR=WALKING → STILL at 0.6 * 0.15 = 0.09 for STILL
        // STILL (0.09) > WALKING (0.06)
        val result = useCase.fuse(
            arActivity = ActivityType.WALKING, arConfidence = 10f,
            speedMps = null, stepRatePerMinute = 2f
        )
        assertEquals(ActivityType.STILL, result.activityType)
    }

    // ── AR integration ──

    @Test
    fun `high confidence AR dominates over low speed`() {
        val result = useCase.fuse(
            arActivity = ActivityType.IN_VEHICLE, arConfidence = 95f,
            speedMps = 0.5f, // slow speed says WALKING
            stepRatePerMinute = null
        )
        // AR at 95% confidence should dominate
        assertEquals(ActivityType.IN_VEHICLE, result.activityType)
    }

    @Test
    fun `low confidence AR loses to speed heuristic`() {
        val result = useCase.fuse(
            arActivity = ActivityType.STILL, arConfidence = 15f,
            speedMps = 5.0f, // clearly cycling speed
            stepRatePerMinute = null
        )
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    // ── Edge cases ──

    @Test
    fun `all null inputs produce UNKNOWN`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = null)
        assertEquals(ActivityType.UNKNOWN, result.activityType)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `null speed with valid AR uses AR`() {
        val result = useCase.fuse(
            arActivity = ActivityType.WALKING, arConfidence = 80f,
            speedMps = null, stepRatePerMinute = null
        )
        assertEquals(ActivityType.WALKING, result.activityType)
    }

    @Test
    fun `zero speed is STILL`() {
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 0.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.STILL, result.activityType)
    }

    // ── Confidence values ──

    @Test
    fun `confidence is positive when data available`() {
        val result = useCase.fuse(
            arActivity = ActivityType.WALKING, arConfidence = 80f,
            speedMps = 1.4f, stepRatePerMinute = 100f
        )
        assertTrue("Confidence should be positive", result.confidence > 0f)
        assertTrue("AR confidence should be normalized", result.arConfidence in 0f..1f)
    }
}
