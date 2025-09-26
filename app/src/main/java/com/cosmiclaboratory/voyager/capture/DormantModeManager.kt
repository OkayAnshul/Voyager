package com.cosmiclaboratory.voyager.capture

import com.cosmiclaboratory.voyager.domain.model.enums.ActivityType
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages DORMANT mode lifecycle — entering/exiting GPS-off state based on
 * sustained stillness, and waking via SignificantMotionDetector.
 *
 * Does NOT persist state to TimelineStateStore — the caller (PipelineConsumer)
 * handles that since stateStore.update is suspend and wake callbacks are not.
 */
@Singleton
class DormantModeManager @Inject constructor(
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val locationCapture: LocationCapture,
    private val significantMotionDetector: SignificantMotionDetector,
    private val logger: ProductionLogger
) {
    private var consecutiveStillCount = 0
    var isDormant = false
        private set
    @Volatile var dormantExitedAt: Long = 0
        private set

    companion object {
        /** 3 samples at 90s interval = 4.5 minutes of confirmed stillness */
        private const val DORMANT_ENTRY_THRESHOLD = 3
        /** Grace period after exit — watchdog ignores silence during this window */
        const val DORMANT_EXIT_GRACE_MS = 120_000L
    }

    /**
     * Call after each fused activity result. Enters DORMANT after sustained STILL,
     * exits when motion detected. Returns true if dormant state changed.
     */
    fun onActivityUpdate(activityType: ActivityType): Boolean {
        if (activityType == ActivityType.STILL) {
            consecutiveStillCount++
            if (consecutiveStillCount >= DORMANT_ENTRY_THRESHOLD && !isDormant) {
                enterDormant()
                return true
            }
        } else {
            consecutiveStillCount = 0
            if (isDormant) {
                exitDormant()
                return true
            }
        }
        return false
    }

    /** Current motion state name for state persistence by caller.
     *  Reports DORMANT when dormant, STILL when not (since dormant mode is only
     *  entered after sustained stillness — WALKING was incorrect and caused wrong
     *  sampling rate on restart). */
    val currentMotionStateName: String
        get() = if (isDormant) AdaptiveSamplingPolicy.MotionState.DORMANT.name
        else AdaptiveSamplingPolicy.MotionState.STILL.name

    private fun enterDormant() {
        isDormant = true
        adaptiveSamplingPolicy.updateMotionState(AdaptiveSamplingPolicy.MotionState.DORMANT)
        locationCapture.updateSamplingPolicy()
        significantMotionDetector.startListening { wakeFromDormant() }
        logger.d("DormantModeManager", "Entering DORMANT — GPS off, significant motion wake armed")
    }

    private fun wakeFromDormant() {
        logger.d("DormantModeManager", "Significant motion detected — exiting DORMANT")
        exitDormant()
    }

    private fun exitDormant() {
        isDormant = false
        consecutiveStillCount = 0
        dormantExitedAt = System.currentTimeMillis()
        significantMotionDetector.stopListening()
        // Resume with STILL — the pipeline will transition to the correct state
        // once real fused activity data arrives. Starting at STILL (90s interval)
        // avoids the old WALKING bug that caused aggressive 12s sampling on wake.
        adaptiveSamplingPolicy.updateMotionState(AdaptiveSamplingPolicy.MotionState.STILL)
        locationCapture.updateSamplingPolicy()
    }

    fun reset() {
        consecutiveStillCount = 0
        isDormant = false
        dormantExitedAt = 0
        significantMotionDetector.stopListening()
    }
}
