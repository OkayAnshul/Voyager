package com.cosmiclaboratory.voyager.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver for Google Play Services Activity Recognition updates
 *
 * Phase 2: Receives activity detection results and forwards to HybridActivityRecognitionManager
 */
@AndroidEntryPoint
class ActivityRecognitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var activityRecognitionManager: HybridActivityRecognitionManager

    companion object {
        private const val TAG = "ActivityRecognitionRx"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) {
            Log.w(TAG, "Received null intent or context")
            return
        }

        if (intent.action != HybridActivityRecognitionManager.ACTION_ACTIVITY_UPDATE) {
            Log.w(TAG, "Received unexpected intent action: ${intent.action}")
            return
        }

        // Extract activity recognition result
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            if (result != null) {
                val detectedActivities = result.probableActivities
                Log.d(TAG, "Received ${detectedActivities.size} activity detections")

                // Forward to manager
                activityRecognitionManager.handleActivityUpdate(detectedActivities)
            } else {
                Log.w(TAG, "Activity recognition result was null")
            }
        } else {
            Log.w(TAG, "Intent does not contain activity recognition result")
        }
    }
}
