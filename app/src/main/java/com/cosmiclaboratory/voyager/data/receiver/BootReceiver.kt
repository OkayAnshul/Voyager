package com.cosmiclaboratory.voyager.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.utils.LocationServiceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var locationServiceManager: LocationServiceManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Check if location tracking was previously enabled
                val prefs = context.getSharedPreferences("voyager_prefs", Context.MODE_PRIVATE)
                val wasTrackingEnabled = prefs.getBoolean("location_tracking_enabled", false)
                
                if (wasTrackingEnabled) {
                    // Restart location tracking service
                    try {
                        locationServiceManager.startLocationTracking()
                    } catch (e: Exception) {
                        // Handle any errors silently - user can manually restart if needed
                    }
                }
            }
        }
    }
}