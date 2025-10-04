package com.cosmiclaboratory.voyager.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.cosmiclaboratory.voyager.data.service.LocationTrackingService
import com.cosmiclaboratory.voyager.data.state.AppStateManager
import com.cosmiclaboratory.voyager.data.state.StateUpdateSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val currentStateRepository: com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository,
    private val appStateManager: AppStateManager
) {
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    private val _serviceError = MutableStateFlow<String?>(null)
    val serviceError: StateFlow<String?> = _serviceError.asStateFlow()
    
    private val prefs = context.getSharedPreferences("voyager_user_preferences", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var healthCheckJob: Job? = null
    private var lastKnownServiceState = false
    
    init {
        updateServiceStatus()
        startServiceHealthMonitoring()
    }
    
    fun startLocationTracking() {
        try {
            clearError()
            
            // CRITICAL: Check permissions BEFORE attempting to start service
            if (!hasAllRequiredPermissions()) {
                val missingPermissions = getMissingPermissions()
                val errorMsg = "Missing required permissions: ${missingPermissions.joinToString(", ")}"
                Log.e(TAG, errorMsg)
                handleServiceFailure(errorMsg)
                return
            }
            
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_START_TRACKING
            }
            context.startForegroundService(intent)
            
            // Don't immediately set to true - wait for service confirmation
            // The service will call notifyServiceStarted() when it's actually running
            
            // Save tracking state for boot receiver
            prefs.edit().putBoolean("location_tracking_enabled", true).apply()
            
            // Start monitoring service startup
            scope.launch {
                delay(5000) // Wait 5 seconds for service to start
                if (!isServiceActuallyRunning()) {
                    // Service failed to start
                    handleServiceFailure("Service failed to start within 5 seconds")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking", e)
            handleServiceFailure("Failed to start location tracking: ${e.message}")
        }
    }
    
    fun stopLocationTracking() {
        try {
            clearError()
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP_TRACKING
            }
            context.startService(intent)
            
            // Update state immediately for stop action
            _isServiceRunning.value = false
            
            // Save tracking state for boot receiver
            prefs.edit().putBoolean("location_tracking_enabled", false).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location tracking", e)
            // Even if stopping fails, consider it stopped
            _isServiceRunning.value = false
            prefs.edit().putBoolean("location_tracking_enabled", false).apply()
        }
    }
    
    fun isLocationServiceRunning(): Boolean {
        updateServiceStatus()
        return _isServiceRunning.value
    }
    
    private fun updateServiceStatus() {
        val isRunning = isServiceActuallyRunning()
        val previousState = _isServiceRunning.value
        _isServiceRunning.value = isRunning
        
        // Detect unexpected service stops
        if (previousState && !isRunning && lastKnownServiceState) {
            handleServiceFailure("Location tracking stopped unexpectedly")
        }
        
        lastKnownServiceState = isRunning
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
            Log.w(TAG, "Unable to check service status: ${e.message}")
        }
        return false
    }
    
    fun notifyServiceStarted() {
        Log.d(TAG, "CRITICAL: Service started notification received")
        _isServiceRunning.value = true
        lastKnownServiceState = true
        clearError()
        
        // CRITICAL: Sync with unified state manager for consistency
        syncTrackingStatusWithStateManager(true)
    }
    
    fun notifyServiceStopped(reason: String? = null) {
        Log.d(TAG, "CRITICAL: Service stopped notification received: $reason")
        _isServiceRunning.value = false
        lastKnownServiceState = false
        
        // Update preferences to reflect actual state
        prefs.edit().putBoolean("location_tracking_enabled", false).apply()
        
        // CRITICAL: Sync with unified state manager for consistency
        syncTrackingStatusWithStateManager(false)
        
        // If service stopped due to error, record it
        reason?.let { 
            handleServiceFailure(it)
        }
    }
    
    private fun startServiceHealthMonitoring() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(30000) // Check every 30 seconds
                updateServiceStatus()
            }
        }
    }
    
    private fun handleServiceFailure(error: String) {
        Log.e(TAG, "Service failure: $error")
        _serviceError.value = error
        _isServiceRunning.value = false
        prefs.edit().putBoolean("location_tracking_enabled", false).apply()
    }
    
    private fun clearError() {
        _serviceError.value = null
    }
    
    fun clearServiceError() {
        clearError()
    }
    
    private fun hasAllRequiredPermissions(): Boolean {
        val status = PermissionManager.getPermissionStatus(context)
        // Location service can work with background location - notifications are optional
        return status in setOf(
            com.cosmiclaboratory.voyager.utils.PermissionStatus.LOCATION_FULL_ACCESS,
            com.cosmiclaboratory.voyager.utils.PermissionStatus.ALL_GRANTED
        )
    }
    
    private fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        // Check essential location permissions only - notifications are optional
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add("Fine Location")
        }
        
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add("Coarse Location")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add("Background Location")
            }
        }
        
        // Note: Notifications are optional for location service functionality
        // Service will handle notification permission gracefully
        
        return missing
    }
    
    /**
     * Sync tracking status with Unified State Manager for consistency
     * CRITICAL: Single source of truth prevents race conditions
     */
    private fun syncTrackingStatusWithStateManager(isActive: Boolean) {
        scope.launch {
            try {
                val startTime = if (isActive) java.time.LocalDateTime.now() else null
                
                // CRITICAL: Update unified state manager first
                val stateResult = appStateManager.updateTrackingStatus(
                    isActive = isActive,
                    startTime = startTime,
                    source = StateUpdateSource.LOCATION_SERVICE
                )
                
                when (stateResult) {
                    is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Success -> {
                        Log.d(TAG, "CRITICAL SUCCESS: Synced unified state manager - isActive=$isActive, version=${stateResult.stateVersion}")
                        
                        // Also update CurrentState repository for database persistence
                        currentStateRepository.updateTrackingStatus(isActive, startTime)
                    }
                    is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Failed -> {
                        Log.e(TAG, "CRITICAL ERROR: State manager rejected tracking sync - ${stateResult.reason}")
                        // Continue with database update but log the issue
                        currentStateRepository.updateTrackingStatus(isActive, startTime)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR: Failed to sync tracking status with state manager", e)
                // Fallback to original repository update
                try {
                    val fallbackStartTime = if (isActive) java.time.LocalDateTime.now() else null
                    currentStateRepository.updateTrackingStatus(isActive, fallbackStartTime)
                } catch (e2: Exception) {
                    Log.e(TAG, "CRITICAL ERROR: Fallback sync also failed", e2)
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "LocationServiceManager"
    }
}