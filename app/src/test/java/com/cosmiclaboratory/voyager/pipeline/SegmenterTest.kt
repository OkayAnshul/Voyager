package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.usecase.FusedMotionState
import com.cosmiclaboratory.voyager.pipeline.stage.Segmenter
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import androidx.room.withTransaction
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

/**
 * Tests for Segmenter — the motion segmentation state machine.
 *
 * Critical scenarios:
 * - Segment creation on first sample
 * - Same-type samples accumulate in one segment
 * - Activity type change triggers debounced transition
 * - Periodic 5-minute flush
 * - In-progress snapshot accuracy
 * - closeCurrentSegment on stop/pause
 * - Debounce prevents oscillation at boundaries
 */
class SegmenterTest {

    private val database = mockk<VoyagerDatabase>()
    private val movementSegmentDao = mockk<MovementSegmentDao>(relaxed = true)
    private val segmentEvidenceDao = mockk<SegmentEvidenceDao>(relaxed = true)
    private val routeDao = mockk<RouteDao>(relaxed = true)
    private val stateStore = mockk<TimelineStateStore>(relaxed = true)

    private lateinit var segmenter: Segmenter
    private val dayKey = "2026-04-07"
    private val baseLat = 37.7749
    private val baseLng = -122.4194

    @Before
    fun setup() {
        // Mock the Room withTransaction extension — executes the block directly.
        // withTransaction compiles to a static method where arg(0) = receiver, arg(1) = block.
        mockkStatic("androidx.room.RoomDatabaseKt")
        @Suppress("UNCHECKED_CAST")
        coEvery { database.withTransaction<Long>(any()) } coAnswers {
            val block = args[1] as (suspend () -> Long)
            block()
        }
        coEvery { movementSegmentDao.insert(any()) } returns 100L
        coEvery { routeDao.insert(any()) } returns 200L

        segmenter = Segmenter(database, movementSegmentDao, segmentEvidenceDao, routeDao, stateStore)
    }

    @After
    fun teardown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun sample(
        capturedAt: Long,
        lat: Double = baseLat,
        lng: Double = baseLng,
        sampleId: Long = 1L,
        speedMps: Float? = null,
        accuracyM: Float = 10f
    ) = RawSample(
        sampleId = sampleId,
        capturedAt = capturedAt,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        speedMps = speedMps,
        provider = "gps",
        permissionSnapshot = "FINE",
        trackingSessionId = 1L,
        localTimeZone = "America/Los_Angeles",
        geohash = "9q8y"
    )

    private fun motion(activity: ActivityType, confidence: Float = 0.8f) = FusedMotionState(
        activityType = activity,
        confidence = confidence,
        arConfidence = 0.8f,
        speedConfidence = 0.7f,
        stepConfidence = 0.6f
    )

    // ── Scenario 1: First sample opens a segment ──

    @Test
    fun `first sample creates in-progress segment`() = runTest {
        val now = System.currentTimeMillis()
        segmenter.processSample(sample(now), motion(ActivityType.WALKING), dayKey)

        val snapshot = segmenter.getInProgressSnapshot()
        assertNotNull("Snapshot should exist after first sample", snapshot)
        assertEquals("WALK", snapshot!!.segmentType)
    }

    // ── Scenario 2: Same activity accumulates ──

    @Test
    fun `consecutive same-activity samples accumulate without DB write`() = runTest {
        val baseTime = System.currentTimeMillis()

        repeat(5) { i ->
            segmenter.processSample(
                sample(baseTime + i * 15_000L, sampleId = i.toLong()),
                motion(ActivityType.WALKING),
                dayKey
            )
        }

        // No DB writes yet (no segment closed)
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) }

        val snapshot = segmenter.getInProgressSnapshot()
        assertNotNull(snapshot)
        assertEquals("WALK", snapshot!!.segmentType)
    }

    // ── Scenario 3: Activity change with debounce ──

    @Test
    fun `activity change requires 5 consecutive samples to transition`() = runTest {
        val baseTime = System.currentTimeMillis()

        // Start with WALKING
        segmenter.processSample(sample(baseTime, sampleId = 1), motion(ActivityType.WALKING), dayKey)
        segmenter.processSample(sample(baseTime + 15_000, sampleId = 2), motion(ActivityType.WALKING), dayKey)

        // Switch to STILL — need 5 consecutive
        segmenter.processSample(sample(baseTime + 30_000, sampleId = 3), motion(ActivityType.STILL), dayKey)
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) } // Not yet

        segmenter.processSample(sample(baseTime + 45_000, sampleId = 4), motion(ActivityType.STILL), dayKey)
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) } // Not yet

        segmenter.processSample(sample(baseTime + 60_000, sampleId = 5), motion(ActivityType.STILL), dayKey)
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) } // Not yet

        segmenter.processSample(sample(baseTime + 75_000, sampleId = 6), motion(ActivityType.STILL), dayKey)
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) } // Not yet

        segmenter.processSample(sample(baseTime + 90_000, sampleId = 7), motion(ActivityType.STILL), dayKey)
        // Now the WALK segment should have been closed and written to DB
        coVerify(exactly = 1) { movementSegmentDao.insert(match { it.segmentType == "WALK" }) }

        // New segment is DWELL (STILL maps to DWELL; VISIT is only set by PipelineConsumer for confirmed visits)
        val snapshot = segmenter.getInProgressSnapshot()
        assertNotNull(snapshot)
        assertEquals("DWELL", snapshot!!.segmentType)
    }

    // ── Scenario 4: Debounce resets on activity oscillation ──

    @Test
    fun `oscillating activity never triggers transition`() = runTest {
        val baseTime = System.currentTimeMillis()

        // Start WALKING
        segmenter.processSample(sample(baseTime, sampleId = 1), motion(ActivityType.WALKING), dayKey)

        // Oscillate: STILL, WALKING, STILL, WALKING — never 3 consecutive STILL
        segmenter.processSample(sample(baseTime + 15_000, sampleId = 2), motion(ActivityType.STILL), dayKey)
        segmenter.processSample(sample(baseTime + 30_000, sampleId = 3), motion(ActivityType.WALKING), dayKey)
        segmenter.processSample(sample(baseTime + 45_000, sampleId = 4), motion(ActivityType.STILL), dayKey)
        segmenter.processSample(sample(baseTime + 60_000, sampleId = 5), motion(ActivityType.WALKING), dayKey)

        // No segment transition should have occurred
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) }

        // Still in WALK segment
        val snapshot = segmenter.getInProgressSnapshot()
        assertEquals("WALK", snapshot!!.segmentType)
    }

    // ── Scenario 5: Periodic 5-minute flush ──

    @Test
    fun `segment flushes after 5 minutes`() = runTest {
        val baseTime = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000L

        // Add samples over 5+ minutes
        segmenter.processSample(sample(baseTime, sampleId = 1), motion(ActivityType.WALKING), dayKey)
        segmenter.processSample(sample(baseTime + 60_000, sampleId = 2), motion(ActivityType.WALKING), dayKey)

        // Next sample is past 5 minutes — triggers flush
        segmenter.processSample(
            sample(baseTime + fiveMinutes + 1000, sampleId = 3),
            motion(ActivityType.WALKING),
            dayKey
        )

        // WALK segment should have been flushed
        coVerify(exactly = 1) { movementSegmentDao.insert(match { it.segmentType == "WALK" }) }

        // New WALK segment started with sample 3
        val snapshot = segmenter.getInProgressSnapshot()
        assertNotNull(snapshot)
        assertEquals("WALK", snapshot!!.segmentType)
    }

    // ── Scenario 6: closeCurrentSegment on stop ──

    @Test
    fun `closeCurrentSegment persists in-flight data`() = runTest {
        val baseTime = System.currentTimeMillis()

        segmenter.processSample(sample(baseTime, sampleId = 1), motion(ActivityType.WALKING), dayKey)
        segmenter.processSample(
            sample(baseTime + 15_000, sampleId = 2, lat = baseLat + 0.0005),
            motion(ActivityType.WALKING),
            dayKey
        )

        // Simulate stop → flush
        val segmentId = segmenter.closeCurrentSegment(dayKey)
        assertNotNull("closeCurrentSegment should return segment ID", segmentId)
        coVerify { movementSegmentDao.insert(match { it.segmentType == "WALK" }) }

        // After close, no in-progress segment
        assertNull(segmenter.getInProgressSnapshot())
    }

    // ── Scenario 7: closeCurrentSegment with no data returns null ──

    @Test
    fun `closeCurrentSegment with no accumulation returns null`() = runTest {
        val result = segmenter.closeCurrentSegment(dayKey)
        assertNull(result)
        coVerify(exactly = 0) { movementSegmentDao.insert(any()) }
    }

    // ── Scenario 8: DWELL segments don't create routes ──

    @Test
    fun `DWELL segments skip route creation`() = runTest {
        val baseTime = System.currentTimeMillis()

        segmenter.processSample(sample(baseTime, sampleId = 1), motion(ActivityType.STILL), dayKey)
        segmenter.processSample(sample(baseTime + 15_000, sampleId = 2), motion(ActivityType.STILL), dayKey)
        segmenter.closeCurrentSegment(dayKey)

        coVerify { movementSegmentDao.insert(match { it.segmentType == "DWELL" }) }
        coVerify(exactly = 0) { routeDao.insert(any()) }
    }

    // ── Scenario 9: Movement segments DO create routes ──

    @Test
    fun `WALK segments create encoded polyline route`() = runTest {
        val baseTime = System.currentTimeMillis()

        segmenter.processSample(
            sample(baseTime, sampleId = 1, lat = baseLat),
            motion(ActivityType.WALKING), dayKey
        )
        segmenter.processSample(
            sample(baseTime + 15_000, sampleId = 2, lat = baseLat + 0.001),
            motion(ActivityType.WALKING), dayKey
        )
        segmenter.closeCurrentSegment(dayKey)

        coVerify { routeDao.insert(match { it.transportMode == "WALK" }) }
    }

    // ── Scenario 10: In-progress snapshot returns null when locked ──

    @Test
    fun `getInProgressSnapshot returns null before any sample`() {
        assertNull(segmenter.getInProgressSnapshot())
    }

    // ── Scenario 11: Walking → Driving transition with debounce ──

    @Test
    fun `walking to driving transition flushes walk segment`() = runTest {
        val baseTime = System.currentTimeMillis()

        // Walking
        segmenter.processSample(sample(baseTime, sampleId = 1), motion(ActivityType.WALKING), dayKey)
        segmenter.processSample(sample(baseTime + 15_000, sampleId = 2), motion(ActivityType.WALKING), dayKey)

        // 5 consecutive IN_VEHICLE samples to trigger transition
        segmenter.processSample(sample(baseTime + 30_000, sampleId = 3), motion(ActivityType.IN_VEHICLE), dayKey)
        segmenter.processSample(sample(baseTime + 45_000, sampleId = 4), motion(ActivityType.IN_VEHICLE), dayKey)
        segmenter.processSample(sample(baseTime + 60_000, sampleId = 5), motion(ActivityType.IN_VEHICLE), dayKey)
        segmenter.processSample(sample(baseTime + 75_000, sampleId = 6), motion(ActivityType.IN_VEHICLE), dayKey)
        segmenter.processSample(sample(baseTime + 90_000, sampleId = 7), motion(ActivityType.IN_VEHICLE), dayKey)

        // WALK segment closed
        coVerify { movementSegmentDao.insert(match { it.segmentType == "WALK" }) }

        // Now in DRIVE segment
        val snapshot = segmenter.getInProgressSnapshot()
        assertEquals("DRIVE", snapshot!!.segmentType)
    }

    // ── Scenario 12: Activity type mapping ──

    @Test
    fun `all activity types map to correct segment types`() = runTest {
        val types = mapOf(
            ActivityType.STILL to "DWELL",
            ActivityType.WALKING to "WALK",
            ActivityType.RUNNING to "RUN",
            ActivityType.CYCLING to "CYCLE",
            ActivityType.ON_BICYCLE to "CYCLE",
            ActivityType.IN_VEHICLE to "DRIVE",
            ActivityType.UNKNOWN to "UNKNOWN_MOTION"
        )

        for ((activity, expectedSegment) in types) {
            // Fresh segmenter for each
            val seg = Segmenter(database, movementSegmentDao, segmentEvidenceDao, routeDao, stateStore)
            val now = System.currentTimeMillis()
            seg.processSample(sample(now), motion(activity), dayKey)
            val snapshot = seg.getInProgressSnapshot()
            assertEquals("$activity should map to $expectedSegment", expectedSegment, snapshot?.segmentType)
        }
    }
}
