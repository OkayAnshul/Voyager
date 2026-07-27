package com.cosmiclaboratory.voyager.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the barometric pressure sensor to maintain a current altitude estimate.
 *
 * Phone GPS altitude is noisy (±10–30 m) and is what Strava logs on a phone; a pressure sensor
 * tracks *relative* elevation change far more steadily, so a run's cumulative ascent/descent is
 * much closer to truth. [latestAltitudeM] feeds the workout recorder when present; the recorder
 * falls back to GPS altitude otherwise. No-op (and [isAvailable] = false) on devices without a
 * barometer — many Androids have one, most don't guarantee it.
 *
 * The sensor is low-rate and cheap; it listens continuously while [start]ed and is otherwise
 * unregistered. Altitude is derived against the standard sea-level pressure — only *changes*
 * matter for gain/loss, so the absolute datum need not be calibrated.
 */
@Singleton
class BaroCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    @Volatile private var latestAltitude: Double? = null
    @Volatile private var latestAt = 0L
    @Volatile private var listening = false

    fun isAvailable(): Boolean = pressureSensor != null

    /** The most recent barometric altitude if sampled within [STALENESS_MS], else null. */
    fun latestAltitudeM(nowMs: Long = System.currentTimeMillis()): Double? =
        latestAltitude?.takeIf { nowMs - latestAt <= STALENESS_MS }

    fun start() {
        if (pressureSensor == null || listening) return
        sensorManager?.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        listening = true
    }

    fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
        latestAltitude = null
        latestAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val hPa = event.values[0]
        latestAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa).toDouble()
        latestAt = System.currentTimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val STALENESS_MS = 15_000L
    }
}
