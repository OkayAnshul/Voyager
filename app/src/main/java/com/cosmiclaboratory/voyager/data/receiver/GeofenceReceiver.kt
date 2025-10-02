package com.cosmiclaboratory.voyager.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.data.service.GeofenceTransitionService

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Forward geofence events to the modernized JobIntentService for processing
        val serviceIntent = Intent(context, GeofenceTransitionService::class.java).apply {
            putExtras(intent.extras ?: return)
        }
        
        // Enqueue work in the JobIntentService (modern replacement for startService)
        GeofenceTransitionService.enqueueWork(context, serviceIntent)
    }
}