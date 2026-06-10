package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DormantModeManagerTest {

    private val adaptiveSamplingPolicy = mockk<AdaptiveSamplingPolicy>(relaxed = true)
    private val locationCapture = mockk<LocationCapture>(relaxed = true)
    private val significantMotionDetector = mockk<SignificantMotionDetector>(relaxed = true)
    private val logger = mockk<ProductionLogger>(relaxed = true)

    private fun manager(
        motionDetectionEnabled: Boolean = true,
        sensorAvailable: Boolean = true,
    ): DormantModeManager {
        val settings = mockk<UserSettings>(relaxed = true)
        every { settings.motionDetectionEnabled } returns motionDetectionEnabled
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.observeSettings() } returns MutableStateFlow(settings)
        every { significantMotionDetector.isAvailable } returns sensorAvailable
        return DormantModeManager(
            adaptiveSamplingPolicy, locationCapture, significantMotionDetector,
            settingsRepository, logger
        )
    }

    private fun DormantModeManager.feedStill(times: Int) {
        repeat(times) { onActivityUpdate(ActivityType.STILL) }
    }

    @Test
    fun `enters dormant after three consecutive still samples`() {
        val m = manager()
        m.onActivityUpdate(ActivityType.STILL)
        m.onActivityUpdate(ActivityType.STILL)
        assertFalse("needs 3 consecutive", m.isDormant)
        val changed = m.onActivityUpdate(ActivityType.STILL)
        assertTrue(changed)
        assertTrue(m.isDormant)
    }

    @Test
    fun `does not enter dormant without a significant-motion sensor`() {
        // Regression guard (D1): no wake path → entering dormant would kill tracking.
        val m = manager(sensorAvailable = false)
        m.feedStill(5)
        assertFalse(m.isDormant)
    }

    @Test
    fun `does not enter dormant when motion detection is disabled`() {
        val m = manager(motionDetectionEnabled = false)
        m.feedStill(5)
        assertFalse(m.isDormant)
    }

    @Test
    fun `exits dormant on motion and records exit time`() {
        val m = manager()
        m.feedStill(3)
        assertTrue(m.isDormant)
        val changed = m.onActivityUpdate(ActivityType.WALKING)
        assertTrue(changed)
        assertFalse(m.isDormant)
        assertTrue(m.dormantExitedAt > 0)
    }

    @Test
    fun `non-still before threshold resets the counter`() {
        val m = manager()
        m.onActivityUpdate(ActivityType.STILL)
        m.onActivityUpdate(ActivityType.STILL)
        m.onActivityUpdate(ActivityType.WALKING) // resets the streak
        m.onActivityUpdate(ActivityType.STILL)
        m.onActivityUpdate(ActivityType.STILL)
        assertFalse("only 2 still since reset", m.isDormant)
        m.onActivityUpdate(ActivityType.STILL)
        assertTrue(m.isDormant)
    }

    @Test
    fun `reset clears dormant state`() {
        val m = manager()
        m.feedStill(3)
        assertTrue(m.isDormant)
        m.reset()
        assertFalse(m.isDormant)
        assertTrue(m.dormantExitedAt == 0L)
    }
}
