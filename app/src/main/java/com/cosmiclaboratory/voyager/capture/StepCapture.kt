package com.cosmiclaboratory.voyager.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cosmiclaboratory.voyager.platform.scope.VoyagerApplicationScope
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.RawStepSampleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawStepSampleDao: RawStepSampleDao,
    private val applicationScope: VoyagerApplicationScope,
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private val resolver = StepDeltaResolver()
    @Volatile private var activeSessionId: Long = 0

    fun start(sessionId: Long) {
        activeSessionId = sessionId
        resolver.reset(System.currentTimeMillis())
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager?.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        // Flush the buffered tail (steps since the last batch) so a pause/resume or
        // end-of-day stop doesn't silently drop them. Persist before clearing the session.
        resolver.flush(System.currentTimeMillis())?.let(::persist)
        activeSessionId = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val outcome = resolver.onCount(event.values[0].toInt(), System.currentTimeMillis())
        if (outcome is StepDeltaResolver.Outcome.Emit) persist(outcome)
    }

    /**
     * Persist a step batch on the app-lifetime scope (not a per-session scope) so an
     * in-flight write is never cancelled by stop().
     */
    private fun persist(emit: StepDeltaResolver.Outcome.Emit) {
        val sessionId = activeSessionId
        if (sessionId == 0L) return
        applicationScope.scope.launch {
            rawStepSampleDao.insert(
                RawStepSampleEntity(
                    periodStart = emit.periodStart,
                    periodEnd = emit.periodEnd,
                    stepCount = emit.steps,
                    source = "STEP_SENSOR",
                    trackingSessionId = sessionId
                )
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
