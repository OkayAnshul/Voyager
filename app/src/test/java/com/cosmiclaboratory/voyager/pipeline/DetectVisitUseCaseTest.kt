package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import com.cosmiclaboratory.voyager.domain.usecase.DetectVisitUseCase
import com.cosmiclaboratory.voyager.domain.usecase.VisitDetectionResult
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitWriteGuard
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for DetectVisitUseCase — the visit detection state machine.
 *
 * Critical scenarios:
 * - Candidate creation when no candidate exists
 * - Accumulation within radius
 * - Departure when leaving radius
 * - Confirmation after dwell threshold
 * - Re-confirmation prevention (the loop bug fix)
 * - Overlap rejection
 */
class DetectVisitUseCaseTest {

    private val stateStore = mockk<TimelineStateStore>(relaxed = true)
    private val visitDao = mockk<VisitDao>(relaxed = true)
    private val visitEvidenceDao = mockk<VisitEvidenceDao>(relaxed = true)
    private val placeDao = mockk<PlaceDao>(relaxed = true)

    private lateinit var useCase: DetectVisitUseCase

    private val dayKey = "2026-04-07"

    // San Francisco coordinates
    private val baseLat = 37.7749
    private val baseLng = -122.4194

    private val emptyState = TrackingRuntimeState(
        activeSessionId = 1L,
        currentSegmentId = null,
        pendingVisitCandidate = null,
        lastConfirmedVisitId = null,
        lastAcceptedSampleId = null,
        lastAcceptedAt = null,
        stateVersion = 1,
        lastPipelineLatencyMs = null
    )

    @Before
    fun setup() {
        // Real guard wrapping the mocked DAO — stubs on visitDao flow through unchanged.
        val visitWriteGuard = VisitWriteGuard(visitDao)
        useCase = DetectVisitUseCase(stateStore, visitDao, visitWriteGuard, visitEvidenceDao, placeDao)
    }

    private fun sample(
        lat: Double = baseLat,
        lng: Double = baseLng,
        capturedAt: Long = System.currentTimeMillis(),
        accuracyM: Float = 10f,
        sampleId: Long = 1L
    ) = RawSample(
        sampleId = sampleId,
        capturedAt = capturedAt,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        provider = "gps",
        permissionSnapshot = "FINE",
        trackingSessionId = 1L,
        localTimeZone = "America/Los_Angeles",
        geohash = "9q8y"
    )

    // ── Scenario 1: No candidate → CandidateStarted ──

    @Test
    fun `first sample with no candidate creates CandidateStarted`() = runTest {
        coEvery { stateStore.getState() } returns emptyState

        val result = useCase.processSample(sample(), dayKey)

        assertTrue(result is VisitDetectionResult.CandidateStarted)
        coVerify { stateStore.setPendingVisitCandidate(any()) }
    }

    // ── Scenario 2: Within radius → Accumulating ──

    @Test
    fun `sample within radius accumulates`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 100_000, // >90s ago (past accumulation grace period)
            sampleCount = 5,
            maxDistanceFromCentroidM = 5.0,
            matchedPlaceId = null
        )
        coEvery { stateStore.getState() } returns emptyState.copy(pendingVisitCandidate = candidate)

        // Sample 5 meters away (well within 40m radius at 10m accuracy)
        val result = useCase.processSample(
            sample(lat = baseLat + 0.00004, capturedAt = now), dayKey
        )

        assertTrue("Expected Accumulating, got $result", result is VisitDetectionResult.Accumulating)
    }

    // ── Scenario 3: Beyond radius → Departed ──

    @Test
    fun `sample beyond radius triggers Departed after exit hysteresis`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 60_000,
            sampleCount = 5,
            maxDistanceFromCentroidM = 5.0,
            matchedPlaceId = null
        )
        coEvery { stateStore.getState() } returns emptyState.copy(pendingVisitCandidate = candidate)

        // Need 3 consecutive outside samples to pass exit hysteresis
        val farLat = baseLat + 0.002
        useCase.processSample(sample(lat = farLat, capturedAt = now - 2000), dayKey)
        useCase.processSample(sample(lat = farLat, capturedAt = now - 1000), dayKey)
        val result = useCase.processSample(sample(lat = farLat, capturedAt = now), dayKey)

        assertTrue("Expected Departed, got $result", result is VisitDetectionResult.Departed)
        coVerify { stateStore.setPendingVisitCandidate(null) }
    }

    // ── Scenario 4: Dwell >= 5 min → Confirmed ──

    @Test
    fun `dwell exceeding 5 minutes triggers Confirmed`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 310_000, // 5 min 10 sec ago
            sampleCount = 20,
            maxDistanceFromCentroidM = 10.0,
            matchedPlaceId = null
        )
        coEvery { stateStore.getState() } returns emptyState.copy(pendingVisitCandidate = candidate)
        coEvery { visitDao.insertIfNotOverlapping(any()) } returns 42L

        val result = useCase.processSample(
            sample(lat = baseLat + 0.00001, capturedAt = now), dayKey
        )

        assertTrue("Expected Confirmed, got $result", result is VisitDetectionResult.Confirmed)
        assertEquals(42L, (result as VisitDetectionResult.Confirmed).visitId)
        coVerify { stateStore.setLastConfirmedVisitId(42L) }
        coVerify { visitEvidenceDao.upsert(any()) }
    }

    // ── Scenario 5: Re-confirmation prevention ──

    @Test
    fun `already confirmed visit returns Accumulating instead of re-confirming`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 600_000, // 10 min ago
            sampleCount = 40,
            maxDistanceFromCentroidM = 10.0,
            matchedPlaceId = null
        )
        // lastConfirmedVisitId is set → visit was already confirmed
        coEvery { stateStore.getState() } returns emptyState.copy(
            pendingVisitCandidate = candidate,
            lastConfirmedVisitId = 42L
        )

        val result = useCase.processSample(
            sample(lat = baseLat + 0.00001, capturedAt = now), dayKey
        )

        // Should return Accumulating, NOT Confirmed
        assertTrue("Expected Accumulating (re-confirm prevention), got $result",
            result is VisitDetectionResult.Accumulating)
        // Should NOT attempt to insert a visit
        coVerify(exactly = 0) { visitDao.insertIfNotOverlapping(any()) }
    }

    // ── Scenario 6: Overlap rejection ──

    @Test
    fun `overlapping visit returns OverlapRejected`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 310_000,
            sampleCount = 20,
            maxDistanceFromCentroidM = 10.0,
            matchedPlaceId = null
        )
        coEvery { stateStore.getState() } returns emptyState.copy(pendingVisitCandidate = candidate)
        coEvery { visitDao.insertIfNotOverlapping(any()) } returns -1L // overlap

        val result = useCase.processSample(
            sample(lat = baseLat + 0.00001, capturedAt = now), dayKey
        )

        assertTrue("Expected OverlapRejected, got $result", result is VisitDetectionResult.OverlapRejected)
        coVerify { stateStore.setPendingVisitCandidate(null) }
    }

    // ── Scenario 7: Departure updates visit with departure time ──

    @Test
    fun `departure from confirmed visit updates departure time`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 600_000,
            sampleCount = 40,
            maxDistanceFromCentroidM = 10.0,
            matchedPlaceId = null
        )
        val existingVisit = VisitEntity(
            visitId = 42L,
            placeId = 0,
            arrivalAt = now - 600_000,
            departureAt = null,
            dwellMs = null,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = dayKey,
            centroidLat = baseLat,
            centroidLng = baseLng
        )
        coEvery { stateStore.getState() } returns emptyState.copy(
            pendingVisitCandidate = candidate,
            lastConfirmedVisitId = 42L
        )
        coEvery { visitDao.getById(42L) } returns existingVisit

        // Need 3 consecutive outside samples to pass exit hysteresis
        val farLat = baseLat + 0.002
        useCase.processSample(sample(lat = farLat, capturedAt = now - 2000), dayKey)
        useCase.processSample(sample(lat = farLat, capturedAt = now - 1000), dayKey)
        val result = useCase.processSample(sample(lat = farLat, capturedAt = now), dayKey)

        assertTrue(result is VisitDetectionResult.Departed)
        coVerify { visitDao.update(match { it.departureAt != null && it.dwellMs != null }) }
        coVerify { stateStore.setLastConfirmedVisitId(null) }
    }

    // ── Scenario 8: Adaptive radius based on accuracy ──

    @Test
    fun `poor accuracy uses wider radius`() = runTest {
        val now = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = now - 100_000, // >90s ago (past accumulation grace period)
            sampleCount = 5,
            maxDistanceFromCentroidM = 5.0,
            matchedPlaceId = null
        )
        coEvery { stateStore.getState() } returns emptyState.copy(pendingVisitCandidate = candidate)

        // 70 meters away — beyond 40m (good GPS) but within 80m (poor GPS)
        val offset = 0.00063 // ~70 meters
        val poorAccuracySample = sample(
            lat = baseLat + offset,
            capturedAt = now,
            accuracyM = 50f // poor accuracy → 80m radius
        )

        val result = useCase.processSample(poorAccuracySample, dayKey)
        assertTrue("Expected Accumulating with wide radius, got $result",
            result is VisitDetectionResult.Accumulating)
    }

    // ── Scenario 9: Walking user — exit hysteresis prevents premature departure ──

    @Test
    fun `walking user accumulates outside samples before departing`() = runTest {
        val baseTime = System.currentTimeMillis()
        val candidate = PendingVisitCandidate(
            centroidLat = baseLat,
            centroidLng = baseLng,
            accumulationStartAt = baseTime - 60_000,
            sampleCount = 5,
            maxDistanceFromCentroidM = 5.0,
            matchedPlaceId = null
        )
        coEvery { stateStore.getState() } returns emptyState.copy(pendingVisitCandidate = candidate)

        // Send 3 consecutive outside samples — exit hysteresis requires 3
        val farLat = baseLat + 0.001 // ~111m away — beyond 40m radius
        val result1 = useCase.processSample(sample(lat = farLat, capturedAt = baseTime), dayKey)
        val result2 = useCase.processSample(sample(lat = farLat, capturedAt = baseTime + 15_000), dayKey)
        val result3 = useCase.processSample(sample(lat = farLat, capturedAt = baseTime + 30_000), dayKey)

        // First 2 outside samples: still accumulating (hysteresis not met)
        assertTrue("Expected Accumulating, got $result1", result1 is VisitDetectionResult.Accumulating)
        assertTrue("Expected Accumulating, got $result2", result2 is VisitDetectionResult.Accumulating)
        // 3rd outside sample: departure confirmed
        assertTrue("Expected Departed, got $result3", result3 is VisitDetectionResult.Departed)
    }
}
