package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.capture.AdaptiveSamplingPolicy
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.usecase.FuseActivityStateUseCase
import com.cosmiclaboratory.voyager.domain.usecase.VisitDetectionResult
import com.cosmiclaboratory.voyager.pipeline.stage.QualityScorer
import com.cosmiclaboratory.voyager.pipeline.stage.DedupSuppressor
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests verifying the pipeline stages work together correctly.
 * Tests the effectiveMotionState logic from PipelineConsumer without needing
 * the full DI graph — the logic is pure functions testable in isolation.
 *
 * These tests verify the scenarios that caused "data doesn't appear in UI":
 * 1. Walking user sees correct WALK segments (not trapped in VISIT)
 * 2. Stationary user sees VISIT segments
 * 3. Quality filter doesn't drop valid samples
 * 4. Dedup doesn't suppress real movement
 */
class PipelineIntegrationTest {

    // ── effectiveMotionState logic ──
    // Reproduces PipelineConsumer lines 158-164 as a pure function

    private fun effectiveMotionState(
        visitResult: VisitDetectionResult,
        originalActivity: ActivityType
    ): ActivityType = when (visitResult) {
        is VisitDetectionResult.Accumulating,
        is VisitDetectionResult.Confirmed -> ActivityType.STILL
        else -> originalActivity
    }

    @Test
    fun `CandidateStarted preserves original motion - critical for walking users`() {
        // This was the bug: CandidateStarted forced STILL, trapping walkers in VISIT segments
        val candidate = com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate(
            centroidLat = 0.0, centroidLng = 0.0,
            accumulationStartAt = 0, sampleCount = 1,
            maxDistanceFromCentroidM = 0.0, matchedPlaceId = null
        )
        val result = effectiveMotionState(
            VisitDetectionResult.CandidateStarted(candidate),
            ActivityType.WALKING
        )
        assertEquals("CandidateStarted must NOT override to STILL", ActivityType.WALKING, result)
    }

    @Test
    fun `Accumulating overrides to STILL for visit detection`() {
        val candidate = com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate(
            centroidLat = 0.0, centroidLng = 0.0,
            accumulationStartAt = 0, sampleCount = 5,
            maxDistanceFromCentroidM = 0.0, matchedPlaceId = null
        )
        val result = effectiveMotionState(
            VisitDetectionResult.Accumulating(candidate),
            ActivityType.WALKING
        )
        assertEquals(ActivityType.STILL, result)
    }

    @Test
    fun `Confirmed overrides to STILL`() {
        val result = effectiveMotionState(
            VisitDetectionResult.Confirmed(42L),
            ActivityType.WALKING
        )
        assertEquals(ActivityType.STILL, result)
    }

    @Test
    fun `Departed preserves original motion`() {
        val result = effectiveMotionState(VisitDetectionResult.Departed, ActivityType.IN_VEHICLE)
        assertEquals(ActivityType.IN_VEHICLE, result)
    }

    @Test
    fun `OverlapRejected preserves original motion`() {
        val result = effectiveMotionState(VisitDetectionResult.OverlapRejected, ActivityType.CYCLING)
        assertEquals(ActivityType.CYCLING, result)
    }

    // ── Walking user scenario: CandidateStarted/Departed oscillation ──

    @Test
    fun `walking user gets consistent WALKING motion through oscillation`() {
        val candidate = com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate(
            centroidLat = 0.0, centroidLng = 0.0,
            accumulationStartAt = 0, sampleCount = 1,
            maxDistanceFromCentroidM = 0.0, matchedPlaceId = null
        )

        // Simulate 10 samples of walking: CandidateStarted, Departed, CandidateStarted, Departed...
        val results = (0 until 10).map { i ->
            val visitResult = if (i % 2 == 0) {
                VisitDetectionResult.CandidateStarted(candidate)
            } else {
                VisitDetectionResult.Departed
            }
            effectiveMotionState(visitResult, ActivityType.WALKING)
        }

        // ALL should be WALKING — never STILL
        results.forEachIndexed { index, activity ->
            assertEquals("Sample $index should be WALKING", ActivityType.WALKING, activity)
        }
    }

    // ── Quality filter tests ──

    @Test
    fun `quality filter accepts normal GPS accuracy`() {
        val scorer = QualityScorer(AdaptiveSamplingPolicy())
        val sample = RawSample(
            sampleId = 1, capturedAt = System.currentTimeMillis(),
            lat = 37.7749, lng = -122.4194, accuracyM = 15f,
            provider = "gps", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val result = scorer.score(sample)
        assertFalse("Normal accuracy should not be discarded", result.shouldDiscard)
    }

    @Test
    fun `quality filter rejects accuracy over 200m`() {
        val scorer = QualityScorer(AdaptiveSamplingPolicy())
        val sample = RawSample(
            sampleId = 1, capturedAt = System.currentTimeMillis(),
            lat = 37.7749, lng = -122.4194, accuracyM = 250f,
            provider = "network", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val result = scorer.score(sample)
        assertTrue("250m accuracy should be discarded", result.shouldDiscard)
    }

    @Test
    fun `quality filter rejects mock locations`() {
        val scorer = QualityScorer(AdaptiveSamplingPolicy())
        val sample = RawSample(
            sampleId = 1, capturedAt = System.currentTimeMillis(),
            lat = 37.7749, lng = -122.4194, accuracyM = 5f,
            isMock = true,
            provider = "gps", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val result = scorer.score(sample)
        assertTrue("Mock locations should be discarded", result.shouldDiscard)
    }

    // ── Dedup filter tests ──

    @Test
    fun `dedup suppresses identical location within 2 seconds`() {
        val dedup = DedupSuppressor()
        val now = System.currentTimeMillis()
        val s1 = RawSample(
            sampleId = 1, capturedAt = now,
            lat = 37.7749, lng = -122.4194, accuracyM = 10f,
            provider = "gps", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val s2 = s1.copy(sampleId = 2, capturedAt = now + 500) // 0.5s later, same location

        assertFalse("First sample should pass", dedup.shouldSuppress(s1))
        assertTrue("Duplicate within 2s should be suppressed", dedup.shouldSuppress(s2))
    }

    @Test
    fun `dedup suppresses same location within noise floor and 30 seconds`() {
        val dedup = DedupSuppressor()
        val now = System.currentTimeMillis()
        val s1 = RawSample(
            sampleId = 1, capturedAt = now,
            lat = 37.7749, lng = -122.4194, accuracyM = 10f,
            provider = "gps", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val s2 = s1.copy(sampleId = 2, capturedAt = now + 3000) // 3s later, same location

        assertFalse(dedup.shouldSuppress(s1))
        // With accuracy-aware dedup, same location within noise floor (10m) and 30s is suppressed
        assertTrue("Same location within noise floor should be suppressed", dedup.shouldSuppress(s2))
    }

    @Test
    fun `dedup allows same location after 30 seconds`() {
        val dedup = DedupSuppressor()
        val now = System.currentTimeMillis()
        val s1 = RawSample(
            sampleId = 1, capturedAt = now,
            lat = 37.7749, lng = -122.4194, accuracyM = 10f,
            provider = "gps", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val s2 = s1.copy(sampleId = 2, capturedAt = now + 31_000) // 31s later

        assertFalse(dedup.shouldSuppress(s1))
        assertFalse("Same location after 30s should pass", dedup.shouldSuppress(s2))
    }

    @Test
    fun `dedup allows moved location within 2 seconds`() {
        val dedup = DedupSuppressor()
        val now = System.currentTimeMillis()
        val s1 = RawSample(
            sampleId = 1, capturedAt = now,
            lat = 37.7749, lng = -122.4194, accuracyM = 10f,
            provider = "gps", permissionSnapshot = "FINE",
            trackingSessionId = 1, localTimeZone = "UTC", geohash = "9q8y"
        )
        val s2 = s1.copy(sampleId = 2, capturedAt = now + 1000, lat = 37.7755) // 60m+ away

        assertFalse(dedup.shouldSuppress(s1))
        assertFalse("Moved location should not be suppressed even within 2s", dedup.shouldSuppress(s2))
    }

    // ── Full-day scenario: motion fusion produces correct types ──

    @Test
    fun `outdoor walking produces WALKING not UNKNOWN`() {
        val fuser = FuseActivityStateUseCase()
        // Outdoor walking: GPS speed available (~1.0 m/s), no AR, no steps
        val result = fuser.fuse(arActivity = null, arConfidence = 0f, speedMps = 1.0f, stepRatePerMinute = null)
        assertEquals("Walking speed should produce WALKING", ActivityType.WALKING, result.activityType)
    }

    @Test
    fun `driving produces IN_VEHICLE not UNKNOWN`() {
        val fuser = FuseActivityStateUseCase()
        val result = fuser.fuse(arActivity = null, arConfidence = 0f, speedMps = 15.0f, stepRatePerMinute = null)
        assertEquals(ActivityType.IN_VEHICLE, result.activityType)
    }

    @Test
    fun `stationary with no sensors produces STILL`() {
        val fuser = FuseActivityStateUseCase()
        val result = fuser.fuse(arActivity = null, arConfidence = 0f, speedMps = 0.1f, stepRatePerMinute = null)
        assertEquals(ActivityType.STILL, result.activityType)
    }
}
