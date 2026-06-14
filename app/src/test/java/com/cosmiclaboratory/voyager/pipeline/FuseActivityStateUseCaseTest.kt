package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.model.AccelSignature
import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.usecase.FuseActivityStateUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Mutable so tests can toggle signal settings and assert fusion reacts. */
    private val settingsFlow = MutableStateFlow(UserSettings())
    private val useCase = FuseActivityStateUseCase(
        mockk<SettingsRepository>().apply {
            every { observeSettings() } returns settingsFlow
        }
    )

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
    fun `speed 3_0 to 4_5 mps with no corroboration falls back to IN_VEHICLE`() {
        // Stop-and-go highway traffic sits in 3.0–4.5 m/s (~11–16 km/h). Without AR
        // or step evidence, this band used to default to CYCLING — that was the
        // dominant misclassification. New default: IN_VEHICLE.
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 3.5f, stepRatePerMinute = null)
        assertEquals(ActivityType.IN_VEHICLE, result.activityType)
    }

    @Test
    fun `speed 3_0 to 4_5 mps preserves CYCLING when last was CYCLING`() {
        // First sample establishes CYCLING via clearly-cycling speed.
        useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        // Next sample in the ambiguous band stays CYCLING via hysteresis.
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 3.5f, stepRatePerMinute = null)
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    @Test
    fun `slow traffic speed with AR=CYCLING still classifies as CYCLING`() {
        // AR's vote at high confidence overrides the new IN_VEHICLE fallback,
        // ensuring genuine cyclists at 12 km/h aren't mislabelled.
        val result = useCase.fuse(
            arActivity = ActivityType.CYCLING, arConfidence = 80f,
            speedMps = 3.5f, stepRatePerMinute = null
        )
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
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = 110f)
        assertEquals(ActivityType.WALKING, result.activityType)
    }

    @Test
    fun `step rate in 80-100 spm band does not classify as WALKING alone`() {
        // 80–100 spm covers desk-shuffle territory and used to pull fusion toward
        // WALKING via stepConf=0.85. With no other signal, fused state should be UNKNOWN.
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = 90f)
        assertEquals(ActivityType.UNKNOWN, result.activityType)
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

    // ── Settings gating ──

    @Test
    fun `disabling speed heuristic drops speed-only classification`() {
        // 5 m/s alone classifies as CYCLING when the speed heuristic is on.
        settingsFlow.value = UserSettings(speedHeuristicEnabled = true)
        assertEquals(
            ActivityType.CYCLING,
            useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null).activityType
        )
        // With the speed heuristic disabled there is no signal left → UNKNOWN.
        settingsFlow.value = UserSettings(speedHeuristicEnabled = false)
        assertEquals(
            ActivityType.UNKNOWN,
            useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null).activityType
        )
    }

    @Test
    fun `arConfidenceThreshold gates whether AR contributes`() {
        // AR says IN_VEHICLE at 60% confidence; speed says WALKING.
        // Threshold 50 → AR counts and dominates.
        settingsFlow.value = UserSettings(arConfidenceThreshold = 50)
        assertEquals(
            ActivityType.IN_VEHICLE,
            useCase.fuse(arActivity = ActivityType.IN_VEHICLE, arConfidence = 60f,
                speedMps = 0.5f, stepRatePerMinute = null).activityType
        )
        // Threshold 80 → 60% AR is below it, AR ignored, speed wins → WALKING.
        settingsFlow.value = UserSettings(arConfidenceThreshold = 80)
        assertEquals(
            ActivityType.WALKING,
            useCase.fuse(arActivity = ActivityType.IN_VEHICLE, arConfidence = 60f,
                speedMps = 0.5f, stepRatePerMinute = null).activityType
        )
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

    // ── Vehicle context: slow traffic ≠ cycling (T4) ──

    @Test
    fun `slow traffic after driving stays IN_VEHICLE`() {
        // Clearly driving (>8.5 m/s) arms the vehicle context.
        useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 15.0f, stepRatePerMinute = null)
        // Then congestion drops to 5 m/s (cycling-speed band) with AR stale/absent —
        // this used to read as CYCLING; now it's slow traffic.
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.IN_VEHICLE, result.activityType)
    }

    @Test
    fun `walking steps clear vehicle context so later cycling speed reads as CYCLING`() {
        useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 15.0f, stepRatePerMinute = null) // arm
        useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = 110f) // walking → clear
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    @Test
    fun `AR on-bicycle clears vehicle context`() {
        useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 15.0f, stepRatePerMinute = null) // arm
        useCase.fuse(arActivity = ActivityType.ON_BICYCLE, arConfidence = 80f, speedMps = null, stepRatePerMinute = null) // clear
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    @Test
    fun `genuine cycling with no prior driving stays CYCLING`() {
        // No vehicle context ever armed → cycling speed classifies as cycling.
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    // ── Accelerometer signature fusion (C2) ──

    @Test
    fun `accel ON_FOOT votes walking when no other signal speaks`() {
        val result = useCase.fuse(
            arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = null,
            accelSignature = AccelSignature.ON_FOOT,
        )
        assertEquals(ActivityType.WALKING, result.activityType)
    }

    @Test
    fun `accel STILL votes still`() {
        val result = useCase.fuse(
            arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = null,
            accelSignature = AccelSignature.STILL,
        )
        assertEquals(ActivityType.STILL, result.activityType)
    }

    @Test
    fun `accel SMOOTH_MOTION stays silent so a cyclist is not pushed to vehicle`() {
        // Cycling-band speed + "riding" accel must remain CYCLING — SMOOTH_MOTION can't tell
        // cycling from driving, so it never votes.
        val result = useCase.fuse(
            arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null,
            accelSignature = AccelSignature.SMOOTH_MOTION,
        )
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    @Test
    fun `accel striding clears a previously armed vehicle context`() {
        useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 15.0f, stepRatePerMinute = null) // arm vehicle
        useCase.fuse(
            arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = null,
            accelSignature = AccelSignature.ON_FOOT, // striding clears it
        )
        val result = useCase.fuse(arActivity = null, arConfidence = 0f, speedMps = 5.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.CYCLING, result.activityType)
    }

    @Test
    fun `accel votes are gated by the motion-detection setting`() {
        settingsFlow.value = UserSettings(motionDetectionEnabled = false)
        val result = useCase.fuse(
            arActivity = null, arConfidence = 0f, speedMps = null, stepRatePerMinute = null,
            accelSignature = AccelSignature.ON_FOOT,
        )
        assertEquals(ActivityType.UNKNOWN, result.activityType)
    }
}
