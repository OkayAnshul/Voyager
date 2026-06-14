package com.cosmiclaboratory.voyager.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cosmiclaboratory.voyager.domain.model.AccelSignature
import com.cosmiclaboratory.voyager.domain.usecase.AccelSignatureClassifier
import com.cosmiclaboratory.voyager.platform.scope.VoyagerApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Burst-samples the accelerometer to maintain a current [AccelSignature] (C2 capture slice).
 *
 * The accelerometer is cheap but continuous listening still costs battery, so this samples a
 * short [WINDOW_MS] window once per [CYCLE_MS] (≈7% duty cycle) and is otherwise unregistered.
 * Each window is classified by the pure [AccelSignatureClassifier]; [latestSignature] exposes
 * the result with a freshness bound so the fuser ignores stale readings. No-op (and
 * [isAvailable] = false) on devices without an accelerometer.
 */
@Singleton
class AccelCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val applicationScope: VoyagerApplicationScope,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val lock = Any()
    private val window = ArrayList<Triple<Float, Float, Float>>(MAX_SAMPLES)
    @Volatile private var collecting = false
    @Volatile private var latest: AccelSignature? = null
    @Volatile private var latestAt = 0L
    private var loop: Job? = null

    fun isAvailable(): Boolean = accelSensor != null

    /** The most recent signature if sampled within [STALENESS_MS], else null (don't act on stale). */
    fun latestSignature(nowMs: Long = System.currentTimeMillis()): AccelSignature? =
        latest?.takeIf { nowMs - latestAt <= STALENESS_MS }

    fun start() {
        if (accelSensor == null || loop != null) return
        loop = applicationScope.scope.launch {
            while (isActive) {
                burst()
                delay(CYCLE_MS - WINDOW_MS)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        sensorManager?.unregisterListener(this)
        collecting = false
        latest = null
        latestAt = 0L
    }

    /** Collect one window of samples, then classify and publish it. */
    private suspend fun burst() {
        synchronized(lock) { window.clear() }
        collecting = true
        sensorManager?.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        delay(WINDOW_MS)
        sensorManager?.unregisterListener(this)
        collecting = false
        val snapshot = synchronized(lock) { window.toList() }
        AccelSignatureClassifier.classifyWindow(snapshot)?.let {
            latest = it
            latestAt = System.currentTimeMillis()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!collecting || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        synchronized(lock) {
            if (window.size < MAX_SAMPLES) {
                window.add(Triple(event.values[0], event.values[1], event.values[2]))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val WINDOW_MS = 2_000L   // sample for 2 s …
        const val CYCLE_MS = 30_000L   // … once every 30 s
        const val STALENESS_MS = 60_000L
        const val MAX_SAMPLES = 200
    }
}
