package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.enums.SamplingPreset
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verifies adaptive sampling honours settings: preset scaling, custom interval,
 * and the STILL/MOVING collapse (walk/drive no longer differ for sampling rate).
 */
class AdaptiveSamplingPolicyTest {

    private val settingsRepository = mockk<SettingsRepository>()
    // Sleep detection off by default so motion-rate tests aren't shadowed by the
    // sleep window when the test runs at night. Sleep tests opt back in explicitly.
    private val settingsFlow = MutableStateFlow(UserSettings(sleepDetectionEnabled = false))
    private lateinit var policy: AdaptiveSamplingPolicy

    @Before
    fun setup() {
        every { settingsRepository.observeSettings() } returns settingsFlow
        policy = AdaptiveSamplingPolicy(settingsRepository)
    }

    @Test
    fun `moving samples more often than still`() {
        policy.forceMotionState(AdaptiveSamplingPolicy.MotionState.STILL)
        val still = policy.getCurrentPolicy().intervalMs
        policy.forceMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
        val moving = policy.getCurrentPolicy().intervalMs
        assertTrue("Moving ($moving) must sample more often than still ($still)", moving < still)
    }

    @Test
    fun `walking and driving collapse to the same moving tier`() {
        policy.forceMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
        val walk = policy.getCurrentPolicy().intervalMs
        policy.forceMotionState(AdaptiveSamplingPolicy.MotionState.DRIVING)
        val drive = policy.getCurrentPolicy().intervalMs
        assertEquals("Walk/drive must not differ for sampling rate", walk, drive)
    }

    @Test
    fun `battery saver preset stretches intervals vs balanced`() {
        policy.forceMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
        settingsFlow.value = UserSettings(
            samplingPreset = SamplingPreset.BALANCED, sleepDetectionEnabled = false
        )
        val balanced = policy.getCurrentPolicy().intervalMs
        settingsFlow.value = UserSettings(
            samplingPreset = SamplingPreset.BATTERY_SAVER, sleepDetectionEnabled = false
        )
        val saver = policy.getCurrentPolicy().intervalMs
        assertTrue("Battery-saver preset must lengthen the interval", saver > balanced)
    }

    @Test
    fun `sleep window crossing midnight is detected correctly`() {
        // Window 23:00 -> 07:00 (crosses midnight).
        val asleep = UserSettings(
            sleepWindowStartHour = 23, sleepWindowStartMinute = 0,
            sleepWindowEndHour = 7, sleepWindowEndMinute = 0
        )
        assertTrue(policy.isWithinSleepWindow(asleep, java.time.LocalTime.of(2, 30)))
        assertTrue(policy.isWithinSleepWindow(asleep, java.time.LocalTime.of(23, 30)))
        assertFalse(policy.isWithinSleepWindow(asleep, java.time.LocalTime.of(12, 0)))
        assertFalse(policy.isWithinSleepWindow(asleep, java.time.LocalTime.of(7, 30)))
    }

    @Test
    fun `custom preset uses the configured interval`() {
        policy.forceMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
        settingsFlow.value = UserSettings(
            samplingPreset = SamplingPreset.CUSTOM,
            customSamplingIntervalMs = 25_000L,
            sleepDetectionEnabled = false
        )
        assertEquals(25_000L, policy.getCurrentPolicy().intervalMs)
    }
}
