package com.cosmiclaboratory.voyager.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives geofence ENTER/EXIT transitions from the platform.
 * Delegates to GeofenceEventHandler for pipeline integration.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var geofenceEventHandler: GeofenceEventHandler

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        for (geofence in event.triggeringGeofences ?: emptyList()) {
            val placeId = PlaceGeofenceManager.parsePlaceId(geofence.requestId) ?: continue
            geofenceEventHandler.onGeofenceTransition(placeId, transition)
        }
    }
}

/**
 * Handles geofence transitions by updating the adaptive sampling policy.
 * Injected as a singleton so pipeline components can also listen for geofence hints.
 */
@javax.inject.Singleton
class GeofenceEventHandler @Inject constructor(
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val locationCapture: LocationCapture
) {
    var lastEnteredPlaceId: Long? = null
        private set
    var lastEnteredAt: Long = 0
        private set

    fun onGeofenceTransition(placeId: Long, transition: Int) {
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                lastEnteredPlaceId = placeId
                lastEnteredAt = System.currentTimeMillis()
                // Boost sampling for quick visit confirmation
                adaptiveSamplingPolicy.updateMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
                locationCapture.updateSamplingPolicy()
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                lastEnteredPlaceId = null
                lastEnteredAt = 0
                // Boost sampling to capture departure route
                adaptiveSamplingPolicy.updateMotionState(AdaptiveSamplingPolicy.MotionState.WALKING)
                locationCapture.updateSamplingPolicy()
            }
        }
    }
}
