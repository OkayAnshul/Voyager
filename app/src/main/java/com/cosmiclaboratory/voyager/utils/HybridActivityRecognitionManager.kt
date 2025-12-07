package com.cosmiclaboratory.voyager.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.cosmiclaboratory.voyager.domain.model.ActivityDetection
import com.cosmiclaboratory.voyager.domain.model.UserActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid Activity Recognition Manager
 *
 * Phase 2: Uses Google Play Services Activity Recognition API when available,
 * falls back to MotionDetectionManager when not available
 *
 * Features:
 * - Detects DRIVING, WALKING, STATIONARY, CYCLING, UNKNOWN
 * - Prevents false place detections while driving
 * - Adjusts GPS frequency based on activity
 * - Graceful fallback for devices without Google Play Services
 */
@Singleton
class HybridActivityRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val motionDetectionManager: MotionDetectionManager
) {
    private val _currentActivity = MutableStateFlow<ActivityDetection>(
        ActivityDetection(UserActivity.UNKNOWN, 0f)
    )
    val currentActivity: StateFlow<ActivityDetection> = _currentActivity.asStateFlow()

    private var activityRecognitionClient: ActivityRecognitionClient? = null
    private var pendingIntent: PendingIntent? = null
    private var isGooglePlayServicesAvailable = false

    companion object {
        private const val TAG = "HybridActivityRecognition"
        private const val DETECTION_INTERVAL_MS = 30000L // 30 seconds

        // Intent action for activity updates
        const val ACTION_ACTIVITY_UPDATE = "com.cosmiclaboratory.voyager.ACTIVITY_UPDATE"
    }

    init {
        checkGooglePlayServicesAvailability()
    }

    /**
     * Check if Google Play Services is available on this device
     */
    private fun checkGooglePlayServicesAvailability() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        isGooglePlayServicesAvailable = when (resultCode) {
            ConnectionResult.SUCCESS -> {
                Log.d(TAG, "Google Play Services available - will use Activity Recognition API")
                true
            }
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_DISABLED -> {
                Log.w(TAG, "Google Play Services not available (code: $resultCode) - using fallback")
                false
            }
            else -> {
                Log.w(TAG, "Google Play Services error (code: $resultCode) - using fallback")
                false
            }
        }
    }

    /**
     * Start activity recognition
     */
    fun startActivityRecognition() {
        if (isGooglePlayServicesAvailable) {
            startGoogleActivityRecognition()
        } else {
            startFallbackActivityRecognition()
        }
    }

    /**
     * Stop activity recognition
     */
    fun stopActivityRecognition() {
        if (isGooglePlayServicesAvailable) {
            stopGoogleActivityRecognition()
        } else {
            stopFallbackActivityRecognition()
        }
    }

    /**
     * Start Google Play Services Activity Recognition
     */
    private fun startGoogleActivityRecognition() {
        try {
            // Check for Activity Recognition permission (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    Log.w(TAG, "Activity Recognition permission not granted - using fallback")
                    startFallbackActivityRecognition()
                    return
                }
            }

            activityRecognitionClient = ActivityRecognition.getClient(context)

            // Create pending intent for activity updates
            val intent = Intent(context, ActivityRecognitionReceiver::class.java).apply {
                action = ACTION_ACTIVITY_UPDATE
            }
            pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Request activity updates
            activityRecognitionClient?.requestActivityUpdates(
                DETECTION_INTERVAL_MS,
                pendingIntent!!
            )?.addOnSuccessListener {
                Log.d(TAG, "Activity Recognition started successfully")
            }?.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start Activity Recognition", exception)
                startFallbackActivityRecognition()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Google Activity Recognition", e)
            startFallbackActivityRecognition()
        }
    }

    /**
     * Stop Google Play Services Activity Recognition
     */
    private fun stopGoogleActivityRecognition() {
        try {
            pendingIntent?.let { intent ->
                activityRecognitionClient?.removeActivityUpdates(intent)
                    ?.addOnSuccessListener {
                        Log.d(TAG, "Activity Recognition stopped")
                    }
                    ?.addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to stop Activity Recognition", exception)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Activity Recognition", e)
        } finally {
            activityRecognitionClient = null
            pendingIntent = null
        }
    }

    /**
     * Start fallback activity recognition using MotionDetectionManager
     */
    private fun startFallbackActivityRecognition() {
        Log.d(TAG, "Using fallback motion detection")
        motionDetectionManager.startMonitoring()
        // MotionDetectionManager will provide activity updates via its own mechanism
    }

    /**
     * Stop fallback activity recognition
     */
    private fun stopFallbackActivityRecognition() {
        motionDetectionManager.stopMonitoring()
    }

    /**
     * Handle activity update from Google Play Services
     * Called by ActivityRecognitionReceiver
     */
    fun handleActivityUpdate(detectedActivities: List<DetectedActivity>) {
        if (detectedActivities.isEmpty()) {
            Log.w(TAG, "Received empty activity list")
            return
        }

        // Get the most likely activity
        val mostLikely = detectedActivities.maxByOrNull { it.confidence } ?: return

        val activity = mapGoogleActivityToUserActivity(mostLikely.type)
        val confidence = mostLikely.confidence / 100f // Convert 0-100 to 0.0-1.0

        val detection = ActivityDetection(activity, confidence)
        _currentActivity.value = detection

        Log.d(TAG, "Activity detected: $activity (confidence: ${String.format("%.0f%%", confidence * 100)})")
    }

    /**
     * Map Google's DetectedActivity types to our UserActivity enum
     */
    private fun mapGoogleActivityToUserActivity(activityType: Int): UserActivity {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> UserActivity.DRIVING
            DetectedActivity.ON_BICYCLE -> UserActivity.CYCLING
            DetectedActivity.ON_FOOT,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING -> UserActivity.WALKING
            DetectedActivity.STILL -> UserActivity.STATIONARY
            DetectedActivity.TILTING,
            DetectedActivity.UNKNOWN -> UserActivity.UNKNOWN
            else -> UserActivity.UNKNOWN
        }
    }

    /**
     * Get current activity detection
     */
    fun getCurrentActivity(): ActivityDetection {
        return _currentActivity.value
    }

    /**
     * Check if user is currently moving (driving/cycling)
     */
    fun isUserMoving(confidenceThreshold: Float = 0.75f): Boolean {
        return _currentActivity.value.isMoving(confidenceThreshold)
    }

    /**
     * Check if user is stationary
     */
    fun isUserStationary(confidenceThreshold: Float = 0.75f): Boolean {
        return _currentActivity.value.isStationary(confidenceThreshold)
    }
}
