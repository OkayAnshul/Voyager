package com.cosmiclaboratory.voyager.domain.time

import android.os.SystemClock

/**
 * Two clocks. Don't mix them.
 *
 *  - [wallClockMs] is wall-clock time. Can jump backwards on NTP sync / user time change.
 *    Use for *persisting* timestamps (someone needs to render this as a date).
 *
 *  - [monotonicNs] is monotonic. Never jumps backwards. Pauses during deep sleep.
 *    Use for *measuring durations* between two events in the same process.
 *
 * Injecting a [Clock] (instead of calling System.currentTimeMillis() everywhere) also
 * makes unit tests deterministic — just override the implementation.
 */
interface Clock {
    fun wallClockMs(): Long
    fun monotonicNs(): Long
}

object SystemDefaultClock : Clock {
    override fun wallClockMs(): Long = System.currentTimeMillis()
    override fun monotonicNs(): Long = SystemClock.elapsedRealtimeNanos()
}
