package com.cosmiclaboratory.voyager.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cosmiclaboratory.voyager.data.worker.GeofenceTransitionWorker
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject
    lateinit var visitRepository: VisitRepository
    
    @Inject
    lateinit var appStateManager: com.cosmiclaboratory.voyager.data.state.AppStateManager
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent?.hasError() == true) {
            // Log error but don't crash
            return
        }

        // Get the transition type
        val geofenceTransition = geofencingEvent?.geofenceTransition ?: return

        // Test that the reported transition is of interest
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            
            // Get the geofences that were triggered
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
            
            // CRITICAL FIX: Extract place IDs from request format "place_X"
            val placeIds = triggeringGeofences.mapNotNull { geofence ->
                geofence.requestId.removePrefix("place_").toLongOrNull()
            }
            
            val currentTime = LocalDateTime.now()
            
            // CRITICAL FIX: Handle visit management with proper state synchronization
            scope.launch {
                try {
                    when (geofenceTransition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> {
                            placeIds.forEach { placeId ->
                                Log.d(TAG, "User entered place: $placeId at $currentTime")
                                
                                // Start new visit at this place
                                val visitId = visitRepository.startVisit(placeId, currentTime)
                                
                                // Update app state to reflect current place
                                appStateManager.updateCurrentPlace(
                                    placeId = placeId,
                                    visitId = visitId,
                                    entryTime = currentTime,
                                    source = com.cosmiclaboratory.voyager.data.state.StateUpdateSource.SMART_PROCESSOR
                                )
                                
                                Log.d(TAG, "Started visit $visitId for place $placeId and updated app state")
                            }
                        }
                        
                        Geofence.GEOFENCE_TRANSITION_EXIT -> {
                            placeIds.forEach { placeId ->
                                Log.d(TAG, "User exited place: $placeId at $currentTime")
                                
                                // Complete any active visit at this place
                                val currentVisit = visitRepository.getCurrentVisit()
                                if (currentVisit?.placeId == placeId) {
                                    val completedVisit = currentVisit.complete(currentTime)
                                    visitRepository.updateVisit(completedVisit)
                                    
                                    // Update app state to clear current place
                                    appStateManager.updateCurrentPlace(
                                        placeId = null,
                                        visitId = null,
                                        entryTime = null,
                                        source = com.cosmiclaboratory.voyager.data.state.StateUpdateSource.SMART_PROCESSOR
                                    )
                                    
                                    Log.d(TAG, "Completed visit for place $placeId, duration: ${completedVisit.duration}ms and updated app state")
                                } else {
                                    Log.w(TAG, "No active visit found for place $placeId on exit")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling geofence transition", e)
                }
            }
            
            // Also enqueue work using WorkManager for additional processing (analytics, etc.)
            placeIds.forEach { placeId ->
                GeofenceTransitionWorker.enqueueGeofenceWork(
                    context = context,
                    transitionType = geofenceTransition,
                    placeId = placeId
                )
            }
        }
    }
}