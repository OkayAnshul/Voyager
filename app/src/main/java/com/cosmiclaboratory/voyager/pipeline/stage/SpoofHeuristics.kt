package com.cosmiclaboratory.voyager.pipeline.stage

import com.cosmiclaboratory.voyager.domain.util.LocationUtils

/**
 * Heuristic location-spoofing detection that complements the OS mock-provider flag.
 *
 * `Location.isFromMockProvider` only catches apps that go through Android's official mock
 * location API (Developer options → "Select mock location app"). Rooted-device injectors and
 * many spoofing tools feed coordinates *without* setting that flag (T15). The most reliable
 * tell they leave is **teleportation**: they emit fixes at the normal sampling cadence but at
 * coordinates that jump impossibly far between consecutive samples.
 *
 * This is a pure, stateless predicate over two fixes; [com.cosmiclaboratory.voyager.pipeline.PipelineConsumer]
 * holds the last-accepted fix and applies it. It is deliberately conservative — it only fires
 * on movement no real consumer device could produce, so the rare false positives it does drop
 * (gross GPS glitches) are samples we wouldn't want anyway.
 */
object SpoofHeuristics {

    /** Above this implied ground speed, moving between two fixes is physically impossible for a
     *  consumer device. ~340 m/s ≈ Mach 1 — well above a commercial jet's ~250 m/s cruise, so
     *  real flights never trip it; only teleport-style spoofing (or a gross glitch) exceeds it. */
    const val MAX_PLAUSIBLE_SPEED_MPS = 340.0

    /** Sub-second / non-positive deltas are too noisy to judge speed from. */
    const val MIN_TIME_DELTA_MS = 1_000L

    /** Beyond this delta the two fixes straddle a tracking gap (the user could have legitimately
     *  travelled while untracked), so speed between them is meaningless — don't judge it. */
    const val MAX_TIME_DELTA_MS = 600_000L // 10 min

    /** Minimum jump distance before implausible-speed gating applies — keeps high-accuracy
     *  jitter at rest from ever being mistaken for a teleport. */
    const val MIN_JUMP_DISTANCE_M = 1_000.0

    data class Fix(val lat: Double, val lng: Double, val capturedAt: Long)

    /**
     * True when travelling [prev] → [curr] within continuous tracking would require a physically
     * impossible speed — a strong signal of a non-API spoofer (or a gross GPS glitch).
     *
     * Returns false (cannot judge) when the time delta is sub-second, non-positive, or large
     * enough to be a tracking gap, and for jumps shorter than [MIN_JUMP_DISTANCE_M].
     */
    fun isImplausibleJump(prev: Fix, curr: Fix): Boolean {
        val dtMs = curr.capturedAt - prev.capturedAt
        if (dtMs < MIN_TIME_DELTA_MS || dtMs > MAX_TIME_DELTA_MS) return false
        val distanceM = LocationUtils.calculateDistance(prev.lat, prev.lng, curr.lat, curr.lng)
        if (distanceM < MIN_JUMP_DISTANCE_M) return false
        val speedMps = distanceM / (dtMs / 1000.0)
        return speedMps > MAX_PLAUSIBLE_SPEED_MPS
    }
}
