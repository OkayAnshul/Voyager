package com.cosmiclaboratory.voyager.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import com.cosmiclaboratory.voyager.R
import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.ApiCompatibilityUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
@Deprecated("Use GeofenceTransitionWorker with WorkManager instead")
class GeofenceTransitionService : JobIntentService() {

    @Inject
    lateinit var placeRepository: PlaceRepository
    
    @Inject
    lateinit var visitRepository: VisitRepository
    
    override fun onHandleWork(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent?.hasError() == true) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            // Log error but don't crash
            return
        }

        // Get the transition type
        val geofenceTransition = geofencingEvent?.geofenceTransition

        // Test that the reported transition is of interest
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            
            // Get the geofences that were triggered
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
            
            // Handle work synchronously in JobIntentService
            runBlocking {
                handleGeofenceTransitions(geofenceTransition, triggeringGeofences)
            }
        }
    }
    
    private suspend fun handleGeofenceTransitions(
        transitionType: Int,
        triggeringGeofences: List<com.google.android.gms.location.Geofence>
    ) {
        val now = ApiCompatibilityUtils.getCurrentDateTime()
        val places = placeRepository.getAllPlaces().first()
        
        triggeringGeofences.forEach { geofence ->
            val placeId = geofence.requestId.toLongOrNull() ?: return@forEach
            val place = places.find { it.id == placeId } ?: return@forEach
            
            when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    // Create new visit entry
                    val visit = Visit(
                        placeId = placeId,
                        entryTime = now,
                        exitTime = null
                    )
                    visitRepository.insertVisit(visit)
                    
                    // Send notification
                    sendNotification("Arrived at ${place.name}", "Welcome back!")
                }
                
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    // Find active visit and close it
                    val activeVisits = visitRepository.getActiveVisits()
                    val activeVisit = activeVisits.find { it.placeId == placeId }
                    
                    if (activeVisit != null) {
                        visitRepository.endVisit(activeVisit.id, now)
                        
                        // Calculate duration for notification
                        val durationText = ApiCompatibilityUtils.calculateDurationText(activeVisit.entryTime, now)
                        
                        sendNotification("Left ${place.name}", "Visit duration: $durationText")
                    }
                }
            }
        }
    }
    
    private fun sendNotification(title: String, message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        // Create notification channel if needed
        val channelId = "geofence_notifications"
        val channel = NotificationChannel(
            channelId,
            "Place Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for entering and leaving places"
        }
        notificationManager.createNotificationChannel(channel)
        
        // Create notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    companion object {
        private const val JOB_ID = 1002
        
        /**
         * Convenience method to enqueue work for this service
         */
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, GeofenceTransitionService::class.java, JOB_ID, intent)
        }
    }
}