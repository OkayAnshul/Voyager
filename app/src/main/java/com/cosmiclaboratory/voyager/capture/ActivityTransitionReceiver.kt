package com.cosmiclaboratory.voyager.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.platform.scope.VoyagerApplicationScope
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var activityCapture: ActivityCapture

    @Inject
    lateinit var applicationScope: VoyagerApplicationScope

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            val pendingResult = goAsync()
            activityCapture.onTransitionReceived()

            // H8 fix: use the singleton application scope instead of creating a new
            // CoroutineScope on every broadcast. Activity transitions can fire at high
            // rate; per-broadcast scopes are never cancelled and leak.
            applicationScope.scope.launch {
                try {
                    for (event in result.transitionEvents) {
                        val activityType = mapActivityType(event.activityType)
                        val transition = if (event.transitionType == 0) "ENTER" else "EXIT"
                        val confidence = estimateConfidence(event.activityType, event.transitionType)
                        activityCapture.recordActivitySample(
                            activityType = activityType,
                            confidence = confidence,
                            source = "TRANSITION_API",
                            transition = transition
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /** Estimate confidence based on activity type reliability and transition direction.
     *  ENTER transitions are more reliable than EXIT (known start vs uncertain end). */
    private fun estimateConfidence(activityType: Int, transitionType: Int): Int {
        val base = when (activityType) {
            DetectedActivity.STILL -> 90
            DetectedActivity.IN_VEHICLE -> 85
            DetectedActivity.WALKING -> 80
            DetectedActivity.RUNNING -> 75
            DetectedActivity.ON_BICYCLE -> 70
            else -> 50
        }
        return if (transitionType == 0) base else (base * 0.85).toInt() // EXIT is less reliable
    }

    private fun mapActivityType(type: Int): String = when (type) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        DetectedActivity.TILTING -> "TILTING"
        else -> "UNKNOWN"
    }
}
