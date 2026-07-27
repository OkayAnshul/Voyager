package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.model.enums.TrackingTier
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifies the workout recorder accumulates, persists, and manages the tracking tier. */
class WorkoutRecorderTest {

    private val activityDao = mockk<ActivityDao>()
    private val settings = mockk<SettingsRepository>()
    private val settingsFlow = MutableStateFlow(UserSettings(trackingTier = TrackingTier.BALANCED))
    private lateinit var recorder: WorkoutRecorder

    @Before
    fun setUp() {
        every { settings.observeSettings() } returns settingsFlow
        coEvery { settings.updateSetting(any(), any()) } returns Result.success(Unit)
        recorder = WorkoutRecorder(activityDao, settings)
    }

    @Test
    fun `records a workout, persists it, and restores the prior tier`() = runTest {
        val slot = slot<ActivityEntity>()
        coEvery { activityDao.insert(capture(slot)) } returns 42L

        recorder.start(WorkoutType.RUN, nowMs = 0L)
        assertTrue(recorder.isRecording)
        coVerify { settings.updateSetting("tracking_tier", "WORKOUT") }

        recorder.onLocation(0.0, 0.0, 0L)
        recorder.onLocation(0.001, 0.0, 100_000L) // ~111 m
        assertTrue(recorder.liveStats.value!!.distanceMeters > 100.0)

        val activity = recorder.stop()
        assertFalse(recorder.isRecording)
        assertNotNull(activity)
        assertEquals(WorkoutType.RUN, activity!!.type)
        assertEquals(42L, activity.id)
        assertTrue(activity.distanceMeters > 100.0)
        assertEquals("RUN", slot.captured.activityType)
        // Prior tier restored on stop.
        coVerify { settings.updateSetting("tracking_tier", "BALANCED") }
    }

    @Test
    fun `a workout with too few points is discarded`() = runTest {
        recorder.start(WorkoutType.WALK, nowMs = 0L)
        recorder.onLocation(0.0, 0.0, 0L) // only one fix
        val activity = recorder.stop()
        assertNull(activity)
        coVerify(exactly = 0) { activityDao.insert(any()) }
    }

    @Test
    fun `a glitch fix inflates neither the live nor the persisted distance`() = runTest {
        val slot = slot<ActivityEntity>()
        coEvery { activityDao.insert(capture(slot)) } returns 7L

        recorder.start(WorkoutType.RUN, nowMs = 0L)
        recorder.onLocation(0.0, 0.0, 0L)
        recorder.onLocation(0.001, 0.0, 10_000L)   // ~111 m, plausible
        val afterClean = recorder.liveStats.value!!.distanceMeters
        recorder.onLocation(0.01, 0.0, 10_100L)    // ~1 km in 100 ms — glitch
        val afterGlitch = recorder.liveStats.value!!.distanceMeters
        assertEquals("glitch leg must not add to live distance", afterClean, afterGlitch, 0.001)

        recorder.stop()
        // The authoritative summarize now agrees with the live (plausible-only) distance.
        assertEquals(afterClean, slot.captured.distanceMeters, 0.001)
    }

    @Test
    fun `a low-accuracy fix draws on the route but adds no distance`() = runTest {
        recorder.start(WorkoutType.RUN, nowMs = 0L)
        recorder.onLocation(0.0, 0.0, 0L, accuracyM = 5f)
        // ~111 m of travel, but this fix's accuracy (80 m) is worse than the 30 m gate.
        recorder.onLocation(0.001, 0.0, 10_000L, accuracyM = 80f)
        assertEquals("poor-accuracy leg must not accumulate distance",
            0.0, recorder.liveStats.value!!.distanceMeters, 0.001)
        // The point is still on the drawn route for the live map.
        assertEquals(2, recorder.liveRoute.value.size)
    }

    @Test
    fun `altitude drives elevation gain and is persisted in the stream`() = runTest {
        val slot = slot<ActivityEntity>()
        coEvery { activityDao.insert(capture(slot)) } returns 9L

        recorder.start(WorkoutType.HIKE, nowMs = 0L)
        recorder.onLocation(0.0, 0.0, 0L, altitudeM = 100.0, accuracyM = 5f)
        recorder.onLocation(0.001, 0.0, 10_000L, altitudeM = 110.0, accuracyM = 5f) // +10 m climb
        assertTrue("live elevation gain should reflect the climb",
            recorder.liveStats.value!!.elevationGainM >= 9.0)

        recorder.stop()
        assertEquals(10.0, slot.captured.elevationGainM, 0.5)
        assertTrue("altitude stream persisted", slot.captured.encodedAltitudes.isNotEmpty())
        assertTrue("time stream persisted", slot.captured.encodedTimes.isNotEmpty())
    }

    @Test
    fun `auto-pause keeps a mid-run stop out of moving time`() = runTest {
        recorder.start(WorkoutType.RUN, nowMs = 0L)
        recorder.onLocation(0.0, 0.0, 0L, accuracyM = 5f)
        recorder.onLocation(0.001, 0.0, 10_000L, accuracyM = 5f)   // ~11 m/s — moving
        recorder.onLocation(0.001, 0.0, 70_000L, accuracyM = 5f)   // stood still 60 s — stopped
        val stats = recorder.liveStats.value!!
        assertTrue("a stationary last leg reads as paused", stats.isPaused)
        assertEquals("only the moving leg counts toward moving time", 10_000L, stats.movingTimeMs)
        assertTrue("elapsed still includes the stop", stats.durationMs >= 70_000L)
    }

    @Test
    fun `manual pause freezes distance until resumed`() = runTest {
        recorder.start(WorkoutType.RUN, nowMs = 0L)
        recorder.onLocation(0.0, 0.0, 0L, accuracyM = 5f)
        recorder.pause()
        recorder.onLocation(0.001, 0.0, 10_000L, accuracyM = 5f)   // moved, but paused
        assertEquals(0.0, recorder.liveStats.value!!.distanceMeters, 0.001)
        assertTrue(recorder.liveStats.value!!.isPaused)
        recorder.resume()
        recorder.onLocation(0.002, 0.0, 20_000L, accuracyM = 5f)   // counts from the resume point
        assertTrue(recorder.liveStats.value!!.distanceMeters > 100.0)
    }
}
