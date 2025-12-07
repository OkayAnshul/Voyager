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
import com.cosmiclaboratory.voyager.data.state.StateUpdateResult
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.domain.exception.*
import com.cosmiclaboratory.voyager.domain.validation.ValidationService
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
    private val appStateManager: AppStateManager,
    private val errorHandler: ErrorHandler,
    private val validationService: ValidationService
) {
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    private val _serviceError = MutableStateFlow<String?>(null)
    val serviceError: StateFlow<String?> = _serviceError.asStateFlow()
    
    private val prefs = context.getSharedPreferences("voyager_user_preferences", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var healthCheckJob: Job? = null
    private var lastKnownServiceState = false
    private var lastServiceStateChangeTime = 0L
    private var serviceStopGracePeriodMs = 5000L // 5 second grace period before reporting service stopped
    
    init {
        updateServiceStatus()
        startServiceHealthMonitoring()
    }
    
    fun startLocationTracking() {
        scope.launch {
            errorHandler.executeWithErrorHandling(
                operation = {
                    clearError()
                    
                    // CRITICAL: Check permissions BEFORE attempting to start service
                    if (!hasAllRequiredPermissions()) {
                        val missingPermissions = getMissingPermissions()
                        throw LocationTrackingException.PermissionDeniedException(
                            missingPermissions
                        )
                    }
                    
                    // Update app state manager before starting service
                    val stateResult = appStateManager.updateTrackingStatus(
                        isActive = true,
                        startTime = java.time.LocalDateTime.now(),
                        source = StateUpdateSource.LOCATION_SERVICE
                    )
                    
                    if (stateResult !is com.cosmiclaboratory.voyager.data.state.StateUpdateResult.Success) {
                        throw SystemException.StateManagementException(
                            "Failed to update tracking status in app state"
                        )
                    }
                    
                    val intent = Intent(context, LocationTrackingService::class.java).apply {
                        action = LocationTrackingService.ACTION_START_TRACKING
                    }
                    context.startForegroundService(intent)
                    
                    // Save tracking state for boot receiver
                    prefs.edit().putBoolean("location_tracking_enabled", true).apply()
                    
                    // Start monitoring service startup
                    monitorServiceStartup()
                    
                    Log.d(TAG, "Location tracking start initiated successfully")
                    Unit
                },
                context = ErrorContext(
                    operation = "startLocationTracking",
                    component = "LocationServiceManager"
                )
            ).onFailure { error ->
                Log.e(TAG, "Failed to start location tracking: ${error.message}")
                handleServiceFailure("Failed to start location tracking: ${error.message}")
            }
        }
    }
    
    private suspend fun monitorServiceStartup() {
        delay(5000) // Wait 5 seconds for service to start
        if (!isServiceActuallyRunning()) {
            // Service failed to start - update app state
            appStateManager.updateTrackingStatus(
                isActive = false,
                startTime = null,
                source = StateUpdateSource.LOCATION_SERVICE
            )
            handleServiceFailure("Service failed to start within 5 seconds")
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
        val currentTime = System.currentTimeMillis()

        // Track state changes
        if (previousState != isRunning) {
            lastServiceStateChangeTime = currentTime
        }

        _isServiceRunning.value = isRunning

        // Detect unexpected service stops with grace period
        if (previousState && !isRunning && lastKnownServiceState) {
            val timeSinceChange = currentTime - lastServiceStateChangeTime

            // Only report failure after grace period to avoid false positives during transitions
            if (timeSinceChange >= serviceStopGracePeriodMs) {
                handleServiceFailure("Location tracking stopped unexpectedly")
            } else {
                Log.d(TAG, "Service state change detected, waiting ${serviceStopGracePeriodMs - timeSinceChange}ms grace period")
            }
        }

        lastKnownServiceState = isRunning
    }
    
    /**
     * Enhanced service detection with multiple fallback mechanisms
     * CRITICAL FIX: getRunningServices is deprecated and unreliable on newer Android
     */
    private fun isServiceActuallyRunning(): Boolean {
        try {
            // Method 1: Check the location service static flag (most reliable)
            if (LocationTrackingService.isServiceRunning()) {
                Log.d(TAG, "✅ Service status: RUNNING (via service flag)")
                return true
            }

            // Method 2: Try deprecated getRunningServices as fallback
            @Suppress("DEPRECATION")
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            try {
                val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                for (serviceInfo in runningServices) {
                    if (LocationTrackingService::class.java.name == serviceInfo.service.className) {
                        Log.d(TAG, "✅ Service status: RUNNING (via ActivityManager)")
                        return true
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "⚠️ Cannot use getRunningServices due to security restrictions (Android 8+)")
            }

            // Method 3: Check app state manager (synchronous check)
            val appState = appStateManager.appState.value
            if (appState.locationTracking.isActive) {
                Log.d(TAG, "⚠️ Service status: ASSUMED RUNNING (via app state)")
                return true
            }

            // Method 4: Check stored preferences state (lowest priority)
            val storedState = prefs.getBoolean("location_tracking_enabled", false)
            if (storedState) {
                Log.d(TAG, "⚠️ Service status: ASSUMED RUNNING (via stored preferences)")
                return true
            }

            Log.d(TAG, "❌ Service status: NOT RUNNING (all checks failed)")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status", e)
            // If we can't determine status, assume it's not running for safety
            return false
        }
    }
    
    fun notifyServiceStarted() {
        Log.d(TAG, "CRITICAL: Service started notification received")
        _isServiceRunning.value = true
        
        // ENHANCED: Synchronize with app state manager
        scope.launch {
            try {
                val syncResult = appStateManager.updateTrackingStatus(
                    isActive = true,
                    startTime = null, // Service provides its own start time
                    source = StateUpdateSource.LOCATION_SERVICE
                )
                
                if (syncResult is StateUpdateResult.Success) {
                    Log.d(TAG, "CRITICAL: AppStateManager updated directly - tracking started")
                } else {
                    Log.w(TAG, "WARNING: Failed to sync app state with service start: ${syncResult}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Exception while syncing app state with service start", e)
            }
        }
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