package com.cosmiclaboratory.voyager.capture

import javax.inject.Inject
import javax.inject.Singleton

data class SamplingPolicy(
    val intervalMs: Long,
    val minDistanceM: Float,
    val accuracy: Int // Priority constant
)

@Singleton
class AdaptiveSamplingPolicy @Inject constructor() {

    enum class MotionState {
        STILL, WALKING, RUNNING, CYCLING, DRIVING, SLEEP, CHARGING,
        /** GPS completely off — significant motion sensor is the wake trigger.
         *  Entered after 3+ consecutive STILL samples. Exits via SignificantMotionDetector. */
        DORMANT
    }

    @Volatile
    private var currentMotionState: MotionState = MotionState.STILL
    @Volatile
    private var batterySaverMultiplier: Float = 1.0f

    fun getCurrentPolicy(): SamplingPolicy {
        val base = when (currentMotionState) {
            MotionState.STILL -> SamplingPolicy(90_000, 0f, 102) // BALANCED
            MotionState.WALKING -> SamplingPolicy(12_000, 5f, 100) // HIGH
            MotionState.RUNNING -> SamplingPolicy(6_000, 5f, 100)
            MotionState.CYCLING -> SamplingPolicy(10_000, 10f, 100)
            MotionState.DRIVING -> SamplingPolicy(7_000, 20f, 100)
            MotionState.SLEEP -> SamplingPolicy(300_000, 0f, 104) // LOW
            MotionState.CHARGING -> SamplingPolicy(15_000, 5f, 100)
            MotionState.DORMANT -> SamplingPolicy(0, 0f, -1) // GPS off — significant motion wake
        }
        return base.copy(intervalMs = (base.intervalMs * batterySaverMultiplier).toLong())
    }

    fun getCurrentMotionState(): MotionState = currentMotionState

    fun updateMotionState(state: MotionState) {
        currentMotionState = state
    }

    fun setBatterySaverMultiplier(multiplier: Float) {
        batterySaverMultiplier = multiplier
    }

    // ── Hysteresis for sampling state changes ──
    // Require 2 consecutive samples with the new state before actually switching,
    // to prevent thrashing location requests at motion boundaries.

    private var lastConfirmedState: MotionState? = null
    private var pendingState: MotionState? = null
    private var pendingCount = 0

    /**
     * Apply hysteresis before updating the motion state.
     * Returns true if the state actually changed, false if still pending.
     */
    fun updateMotionStateWithHysteresis(proposed: MotionState): Boolean {
        if (proposed == lastConfirmedState) {
            pendingState = null
            pendingCount = 0
            return false
        }
        if (proposed == pendingState) {
            pendingCount++
            if (pendingCount >= 3) {
                updateMotionState(proposed)
                lastConfirmedState = proposed
                pendingState = null
                pendingCount = 0
                return true
            }
        } else {
            pendingState = proposed
            pendingCount = 1
        }
        return false
    }

    /** Force-set the confirmed state (bypasses hysteresis). Used after displacement override or dormant exit. */
    fun forceMotionState(state: MotionState) {
        updateMotionState(state)
        lastConfirmedState = state
        pendingState = null
        pendingCount = 0
    }

    /** Get the last confirmed hysteresis state */
    fun getLastConfirmedState(): MotionState? = lastConfirmedState

    fun resetHysteresis() {
        lastConfirmedState = null
        pendingState = null
        pendingCount = 0
    }
}
