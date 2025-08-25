package com.cosmiclaboratory.voyager.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-cost wake trigger using TYPE_SIGNIFICANT_MOTION hardware sensor.
 * When DORMANT (GPS off, user stationary), this sensor fires once on real movement
 * without any battery cost — the detection runs in hardware.
 *
 * TYPE_SIGNIFICANT_MOTION is a one-shot sensor: it fires once then auto-deregisters.
 * After handling the trigger, the caller must re-register if they want another event.
 */
@Singleton
class SignificantMotionDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private var listener: TriggerEventListener? = null
    private var onMotionDetected: (() -> Unit)? = null
    @Volatile
    var isListening: Boolean = false
        private set

    val isAvailable: Boolean get() = sensor != null

    fun startListening(callback: () -> Unit) {
        val s = sensor ?: return
        val sm = sensorManager ?: return
        if (isListening) return

        onMotionDetected = callback
        listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                isListening = false
                onMotionDetected?.invoke()
            }
        }
        val registered = sm.requestTriggerSensor(listener, s)
        isListening = registered
    }

    fun stopListening() {
        if (!isListening) return
        listener?.let { sensorManager?.cancelTriggerSensor(it, sensor) }
        listener = null
        onMotionDetected = null
        isListening = false
    }
}
