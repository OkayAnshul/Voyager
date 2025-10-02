package com.cosmiclaboratory.voyager.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.data.service.LocationTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    private val prefs = context.getSharedPreferences("voyager_prefs", Context.MODE_PRIVATE)
    
    init {
        updateServiceStatus()
    }
    
    fun startLocationTracking() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        context.startForegroundService(intent)
        _isServiceRunning.value = true
        
        // Save tracking state for boot receiver
        prefs.edit().putBoolean("location_tracking_enabled", true).apply()
    }
    
    fun stopLocationTracking() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
        _isServiceRunning.value = false
        
        // Save tracking state for boot receiver
        prefs.edit().putBoolean("location_tracking_enabled", false).apply()
    }
    
    fun isLocationServiceRunning(): Boolean {
        updateServiceStatus()
        return _isServiceRunning.value
    }
    
    private fun updateServiceStatus() {
        val isRunning = isServiceActuallyRunning()
        _isServiceRunning.value = isRunning
    }
    
    @Suppress("DEPRECATION")
    private fun isServiceActuallyRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            for (serviceInfo in runningServices) {
                if (LocationTrackingService::class.java.name == serviceInfo.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Handle potential security exceptions on newer Android versions
            // Fall back to stored state
        }
        return false
    }
    
    fun notifyServiceStarted() {
        _isServiceRunning.value = true
    }
    
    fun notifyServiceStopped() {
        _isServiceRunning.value = false
    }
}