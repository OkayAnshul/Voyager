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
}
