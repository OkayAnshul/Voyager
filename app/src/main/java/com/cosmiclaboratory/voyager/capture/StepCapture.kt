package com.cosmiclaboratory.voyager.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.RawStepSampleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawStepSampleDao: RawStepSampleDao
) : SensorEventListener {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var sensorManager: SensorManager? = null
    private var lastStepCount: Int? = null
    private var lastReadTime: Long = 0
    private var activeSessionId: Long = 0

    fun start(sessionId: Long) {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        activeSessionId = sessionId
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager?.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            lastReadTime = System.currentTimeMillis()
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        scope.cancel()
        lastStepCount = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentCount = event.values[0].toInt()
            val now = System.currentTimeMillis()

            val previous = lastStepCount
            if (previous == null) {
                // First event after start — set baseline, nothing to report yet
                lastStepCount = currentCount
                return
            }

            val delta = currentCount - previous
            // Handle device reboot: step counter resets to 0, causing negative delta.
            // Reset baseline and skip this batch — we lose at most one batch window.
            if (delta < 0) {
                lastStepCount = currentCount
                lastReadTime = now
                return
            }
            // Batch every 5 seconds to balance responsiveness vs DB writes.
            // IMPORTANT: Do NOT update lastStepCount within the batch window —
            // that would reset the baseline and lose accumulated steps.
            // Only update lastStepCount when a batch is actually saved.
            if (delta > 0 && now - lastReadTime > 5_000) {
                val periodStart = lastReadTime
                val sessionId = activeSessionId
                lastReadTime = now
                lastStepCount = currentCount
                scope.launch {
                    rawStepSampleDao.insert(
                        RawStepSampleEntity(
                            periodStart = periodStart,
                            periodEnd = now,
                            stepCount = delta,
                            source = "STEP_SENSOR",
                            trackingSessionId = sessionId
                        )
                    )
                }
            }
            // Within batch window or delta == 0: preserve baseline, do NOT update lastStepCount
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
