package com.cosmiclaboratory.voyager.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.ActivityDetection
import com.cosmiclaboratory.voyager.domain.model.UserActivity
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Motion Detection Manager (Phase 8.4 + Phase 2 Enhancement)
 *
 * Manages accelerometer sensor to detect user motion during sleep hours.
 * This allows location tracking to resume if the user wakes up and starts moving.
 *
 * Phase 2: Enhanced with GPS speed-based activity recognition as fallback
 * for devices without Google Play Services
 *
 * Features:
 * - Lazy initialization (only when motion detection is enabled)
 * - Battery efficient (SENSOR_DELAY_NORMAL)
 * - Configurable sensitivity threshold
 * - Graceful handling of missing sensors
 * - GPS speed-based activity detection (DRIVING vs STATIONARY)
 */
@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : SensorEventListener {

    companion object {
        private const val TAG = "MotionDetectionManager"

        // Sensitivity thresholds (m/s²)
        private const val LOW_SENSITIVITY = 2.0f      // Only major movements
        private const val MEDIUM_SENSITIVITY = 1.0f   // Normal movements
        private const val HIGH_SENSITIVITY = 0.5f     // Sensitive to slight movements

        // Phase 2: Speed thresholds for activity detection (km/h)
        private const val DRIVING_SPEED_THRESHOLD = 20f  // > 20 km/h = likely driving
        private const val WALKING_SPEED_THRESHOLD = 8f   // 2-8 km/h = likely walking
        private const val STATIONARY_SPEED_THRESHOLD = 2f // < 2 km/h = stationary
    }

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private var isListening = false
    private var motionCallback: ((Boolean) -> Unit)? = null
    private var lastMotionTime = 0L
    private val motionCooldownMs = 5000L // 5 seconds between motion detections

    // Phase 2: Activity recognition state (fallback mode)
    private val _fallbackActivity = MutableStateFlow<ActivityDetection>(
        ActivityDetection(UserActivity.UNKNOWN, 0f)
    )
    val fallbackActivity: StateFlow<ActivityDetection> = _fallbackActivity.asStateFlow()
    private var lastSpeedUpdate = 0L
    private val speedSamples = mutableListOf<Float>() // Recent speed samples for smoothing
    private val maxSpeedSamples = 5

    /**
     * Check if motion detection is available on this device
     */
    fun isMotionDetectionAvailable(): Boolean {
        return accelerometer != null
    }

    /**
     * Start listening for motion events
     *
     * @param callback Called when motion is detected (true) or motion stops (false)
     */
    suspend fun startListening(callback: (Boolean) -> Unit) {
        if (!isMotionDetectionAvailable()) {
            Log.w(TAG, "Motion detection not available - no accelerometer sensor")
            return
        }

        val prefs = preferencesRepository.getUserPreferences().first()
        if (!prefs.sleepModeEnabled) {
            Log.d(TAG, "Motion detection not started - sleep mode disabled")
            return
        }

        if (isListening) {
            Log.d(TAG, "Motion detection already listening")
            return
        }

        // Update sensitivity threshold from preferences
        updateSensitivityThreshold()

        this.motionCallback = callback

        val success = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL // Battery efficient
        )

        if (success) {
            isListening = true
            Log.d(TAG, "Motion detection started with sensitivity threshold: $cachedThreshold m/s²")
        } else {
            Log.e(TAG, "Failed to register motion sensor listener")
        }
    }

    /**
     * Stop listening for motion events
     */
    fun stopListening() {
        if (!isListening) return

        sensorManager.unregisterListener(this)
        isListening = false
        motionCallback = null
        Log.d(TAG, "Motion detection stopped")
    }

    /**
     * Check if currently detecting motion
     */
    fun isMotionDetected(): Boolean {
        val timeSinceLastMotion = System.currentTimeMillis() - lastMotionTime
        return timeSinceLastMotion < motionCooldownMs
    }

    private var cachedThreshold: Float = MEDIUM_SENSITIVITY

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Calculate acceleration magnitude (excluding gravity)
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Remove gravity component
        val gravityX = x
        val gravityY = y
        val gravityZ = z - SensorManager.GRAVITY_EARTH

        // Calculate acceleration magnitude
        val accelerationMagnitude = sqrt(
            gravityX * gravityX +
            gravityY * gravityY +
            gravityZ * gravityZ
        )

        // Check if motion exceeds threshold
        if (accelerationMagnitude > cachedThreshold) {
            val now = System.currentTimeMillis()

            // Only trigger callback if outside cooldown period
            if (now - lastMotionTime > motionCooldownMs) {
                Log.d(TAG, "Motion detected! Magnitude: $accelerationMagnitude m/s² (threshold: $cachedThreshold)")
                lastMotionTime = now
                motionCallback?.invoke(true)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for accelerometer
    }

    /**
     * Update cached sensitivity threshold based on user preferences
     */
    private suspend fun updateSensitivityThreshold() {
        val prefs = preferencesRepository.getUserPreferences().first()
        cachedThreshold = when {
            prefs.motionSensitivityThreshold <= 0.3f -> HIGH_SENSITIVITY
            prefs.motionSensitivityThreshold <= 0.7f -> MEDIUM_SENSITIVITY
            else -> LOW_SENSITIVITY
        }
    }

    /**
     * Phase 2: Start monitoring for activity recognition (fallback mode)
     * Called when Google Play Services is unavailable
     */
    fun startMonitoring() {
        Log.d(TAG, "Started fallback activity monitoring (GPS speed-based)")
        // This mode relies on GPS speed updates from LocationTrackingService
        // No sensor registration needed
    }

    /**
     * Phase 2: Stop monitoring for activity recognition
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopped fallback activity monitoring")
        speedSamples.clear()
        _fallbackActivity.value = ActivityDetection(UserActivity.UNKNOWN, 0f)
    }

    /**
     * Phase 2: Update activity detection based on GPS speed
     * Called by LocationTrackingService when new location data is available
     *
     * @param speedKmh Current GPS speed in km/h
     * @param accuracy GPS accuracy in meters (higher accuracy = higher confidence)
     */
    fun updateActivityFromSpeed(speedKmh: Float, accuracy: Float) {
        val now = System.currentTimeMillis()

        // Add speed to samples for smoothing
        speedSamples.add(speedKmh)
        if (speedSamples.size > maxSpeedSamples) {
            speedSamples.removeAt(0)
        }

        // Calculate average speed for more stable detection
        val avgSpeed = speedSamples.average().toFloat()

        // Determine activity based on speed
        val (activity, baseConfidence) = when {
            avgSpeed >= DRIVING_SPEED_THRESHOLD -> {
                UserActivity.DRIVING to 0.8f
            }
            avgSpeed >= WALKING_SPEED_THRESHOLD -> {
                UserActivity.WALKING to 0.7f
            }
            avgSpeed >= STATIONARY_SPEED_THRESHOLD -> {
                UserActivity.WALKING to 0.5f // Could be slow walking
            }
            else -> {
                UserActivity.STATIONARY to 0.8f
            }
        }

        // Adjust confidence based on GPS accuracy
        // Better accuracy = higher confidence
        val accuracyFactor = when {
            accuracy <= 10f -> 1.0f  // Excellent GPS
            accuracy <= 20f -> 0.9f  // Good GPS
            accuracy <= 50f -> 0.7f  // Fair GPS
            else -> 0.5f              // Poor GPS
        }

        val finalConfidence = (baseConfidence * accuracyFactor).coerceIn(0f, 1f)

        // Update state
        _fallbackActivity.value = ActivityDetection(activity, finalConfidence, now)
        lastSpeedUpdate = now

        Log.d(TAG, "Fallback activity: $activity (speed: ${String.format("%.1f", avgSpeed)} km/h, " +
            "confidence: ${String.format("%.0f%%", finalConfidence * 100)})")
    }

    /**
     * Phase 2: Get current activity detection (for fallback mode)
     */
    fun getCurrentActivity(): ActivityDetection {
        // If speed data is stale (> 30 seconds), return UNKNOWN
        val dataAge = System.currentTimeMillis() - lastSpeedUpdate
        return if (dataAge > 30000) {
            ActivityDetection(UserActivity.UNKNOWN, 0f)
        } else {
            _fallbackActivity.value
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopListening()
        stopMonitoring()
    }
}
