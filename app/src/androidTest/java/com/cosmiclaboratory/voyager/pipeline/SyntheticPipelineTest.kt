package com.cosmiclaboratory.voyager.pipeline

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.time.SystemDefaultClock
import com.cosmiclaboratory.voyager.domain.usecase.FusedMotionState
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.pipeline.stage.Segmenter
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real [Segmenter] (the core segment-producing, DB-writing pipeline stage)
 * with a synthetic day's worth of samples against an in-memory database and asserts
 * the pipeline's invariants hold — the safety net for any pipeline refactor (H2).
 */
@RunWith(AndroidJUnit4::class)
class SyntheticPipelineTest {

    private lateinit var db: VoyagerDatabase
    private lateinit var segmenter: Segmenter
    private val dayKey = "2026-05-25"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, VoyagerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val stateStore = TimelineStateStore(
            db.currentRuntimeStateDao(), db.healthLogDao(), SystemDefaultClock
        )
        segmenter = Segmenter(
            db, db.movementSegmentDao(), db.segmentEvidenceDao(), db.routeDao(), stateStore
        )
    }

    // Intentionally no db.close(): TimelineStateStore writes on a fire-and-forget
    // scope, so closing the in-memory DB here races those writes. The DB is released
    // on GC after the short test run.

    private fun sample(t: Long, lat: Double, lng: Double, acc: Float = 8f) = RawSample(
        capturedAt = t, lat = lat, lng = lng, accuracyM = acc,
        provider = "fused", permissionSnapshot = "fine", trackingSessionId = 1L,
        localTimeZone = "UTC", geohash = GeohashEncoder.encode(lat, lng)
    )

    private fun motion(type: ActivityType) = FusedMotionState(
        activityType = type,
        confidence = 0.85f,
        arConfidence = 0.85f,
        speedConfidence = if (type == ActivityType.STILL) 0f else 0.7f,
        stepConfidence = 0f
    )

    private fun assertInvariants(segments: List<MovementSegmentEntity>) {
        assertThat(segments).isNotEmpty()
        for (s in segments) {
            assertThat(s.endAt).isAtLeast(s.startAt)                // no negative duration
            assertThat(s.distanceM.isNaN()).isFalse()              // no NaN distance
            assertThat(s.distanceM.isInfinite()).isFalse()
            assertThat(s.distanceM).isAtLeast(0.0)
            assertThat(s.confidence.isNaN()).isFalse()
            assertThat(s.confidence).isAtLeast(0f)
            assertThat(s.confidence).isAtMost(1f)
        }
        // Sequential, non-overlapping in time.
        val sorted = segments.sortedBy { it.startAt }
        for (i in 1 until sorted.size) {
            assertThat(sorted[i].startAt).isAtLeast(sorted[i - 1].endAt)
        }
    }

    @Test
    fun syntheticDay_producesValidNonOverlappingSegments() = runBlocking {
        val base = 1_700_000_000_000L
        var t = base
        val home = 25.4380 to 81.8470
        val work = 25.4521 to 81.8330

        repeat(60) { segmenter.processSample(sample(t, home.first, home.second), motion(ActivityType.STILL), dayKey); t += 60_000 }
        repeat(30) { i -> segmenter.processSample(sample(t, home.first + i * 0.0005, home.second), motion(ActivityType.WALKING), dayKey); t += 30_000 }
        repeat(120) { segmenter.processSample(sample(t, work.first, work.second), motion(ActivityType.STILL), dayKey); t += 60_000 }
        repeat(20) { i -> segmenter.processSample(sample(t, work.first - i * 0.002, work.second), motion(ActivityType.IN_VEHICLE), dayKey); t += 30_000 }
        segmenter.closeCurrentSegment(dayKey)

        assertInvariants(db.movementSegmentDao().getByDayKey(dayKey))
    }

    @Test
    fun concurrentFeed_doesNotCorruptSegments() = runBlocking {
        // 4 coroutines feed disjoint time windows at once — exercises the Segmenter mutex.
        val base = 1_700_000_000_000L
        val jobs = (0 until 4).map { worker ->
            async(Dispatchers.Default) {
                var t = base + worker * 50L * 60_000L
                repeat(50) { i ->
                    val lat = 25.40 + worker * 0.01 + i * 0.0003
                    segmenter.processSample(sample(t, lat, 81.80), motion(ActivityType.WALKING), dayKey)
                    t += 60_000
                }
            }
        }
        jobs.awaitAll()
        segmenter.closeCurrentSegment(dayKey)

        // Concurrency must not corrupt rows: every persisted segment is still valid.
        val segments = db.movementSegmentDao().getByDayKey(dayKey)
        assertThat(segments).isNotEmpty()
        for (s in segments) {
            assertThat(s.endAt).isAtLeast(s.startAt)
            assertThat(s.distanceM.isNaN()).isFalse()
            assertThat(s.distanceM).isAtLeast(0.0)
        }
    }
}
