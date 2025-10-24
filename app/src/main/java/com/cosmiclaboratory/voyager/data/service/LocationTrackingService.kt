package com.cosmiclaboratory.voyager.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location as AndroidLocation
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import com.cosmiclaboratory.voyager.MainActivity
import com.cosmiclaboratory.voyager.R
import com.cosmiclaboratory.voyager.VoyagerApplication
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import com.cosmiclaboratory.voyager.domain.model.TrackingAccuracyMode
import com.cosmiclaboratory.voyager.domain.model.getEffectiveMinDistance
import com.cosmiclaboratory.voyager.domain.model.getEffectiveUpdateInterval
import com.cosmiclaboratory.voyager.utils.ApiCompatibilityUtils
import com.cosmiclaboratory.voyager.utils.LocationUtils
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.util.Date
import javax.inject.Inject
import androidx.work.WorkManager
import com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import com.cosmiclaboratory.voyager.utils.logLocationProcessing
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.domain.exception.*
import com.cosmiclaboratory.voyager.data.event.StateEventDispatcher
import com.cosmiclaboratory.voyager.data.event.LocationEvent
import com.cosmiclaboratory.voyager.data.event.TrackingEvent
import com.cosmiclaboratory.voyager.data.event.EventTypes
import com.cosmiclaboratory.voyager.utils.WorkManagerHelper
import com.cosmiclaboratory.voyager.utils.EnqueueResult
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases

@AndroidEntryPoint
class LocationTrackingService : Service() {
    
    
    @Inject
    lateinit var locationRepository: LocationRepository
    
    @Inject
    lateinit var preferencesRepository: PreferencesRepository
    
    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient
    
    @Inject
    lateinit var locationServiceManager: com.cosmiclaboratory.voyager.utils.LocationServiceManager
    
    @Inject
    lateinit var smartDataProcessor: com.cosmiclaboratory.voyager.data.processor.SmartDataProcessor
    
    @Inject
    lateinit var currentStateRepository: com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
    
    @Inject
    lateinit var logger: ProductionLogger
    
    @Inject
    lateinit var eventDispatcher: StateEventDispatcher
    
    @Inject
    lateinit var errorHandler: ErrorHandler
    
    @Inject
    lateinit var workManagerHelper: WorkManagerHelper
    
    @Inject
    lateinit var placeDetectionUseCases: PlaceDetectionUseCases
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isTracking = false
    private var isPaused = false
    private var locationCount = 0
    private var currentPreferences: UserPreferences? = null
    private var locationRequest: LocationRequest? = null
    private var lastLocationTime: Long = 0
    private var lastSavedLocation: AndroidLocation? = null
    private var stationaryStartTime: Long = 0
    private var isInStationaryMode = false
    private var locationsSinceLastDetection = 0
    private var lastPlaceDetectionTime: Long = System.currentTimeMillis() - (6 * 60 * 60 * 1000L) // Start 6 hours ago to enable immediate detection
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var permissionCheckJob: Job? = null
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            
            locationResult.lastLocation?.let { androidLocation ->
                // CRITICAL FIX: Check if scope is active before launching
                if (serviceScope.isActive) {
                    serviceScope.launch {
                        try {
                            saveLocation(androidLocation)
                        } catch (e: CancellationException) {
                            // Expected during service shutdown, don't log as error
                            Log.d(TAG, "Location processing cancelled during service shutdown")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving location", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Service scope not active, skipping location save")
                }
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Observe preferences changes
        serviceScope.launch {
            preferencesRepository.getUserPreferences().collect { preferences ->
                currentPreferences = preferences
                if (isTracking) {
                    // Restart tracking with new preferences
                    restartLocationTracking()
                }
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                setServiceRunning(true)
                startLocationTracking()
            }
            ACTION_STOP_TRACKING -> {
                setServiceRunning(false)
                stopLocationTracking()
            }
            ACTION_PAUSE_TRACKING -> pauseLocationTracking()
            ACTION_RESUME_TRACKING -> resumeLocationTracking()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLocationTracking() {
        if (isTracking) return
        
        // Start foreground service immediately to avoid the "did not start in time" error
        try {
            if (hasNotificationPermission()) {
                val notification = createNotification(getString(R.string.location_tracking_starting))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.d(TAG, "Foreground service started with notification")
            } else {
                Log.w(TAG, "Starting service without notification due to missing permission")
                // Note: On Android 13+, this may cause the service to be killed by the system
                // But we'll let the location tracking continue as much as possible
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            // Don't stop the service - try to continue without notification
            Log.w(TAG, "Continuing service without foreground notification")
        }
        
        // Now check permissions after service is properly started
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted, stopping service")
            updateNotification(getString(R.string.location_permission_required))
            // Notify manager and give user time to see the notification before stopping
            locationServiceManager.notifyServiceStopped("Location permission not granted")
            serviceScope.launch {
                kotlinx.coroutines.delay(3000)
                stopSelf()
            }
            return
        }
        
        serviceScope.launch {
            try {
                // Get current preferences if not already loaded, with fallback to defaults
                if (currentPreferences == null) {
                    currentPreferences = try {
                        preferencesRepository.getCurrentPreferences()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load preferences, using defaults: ${e.message}")
                        UserPreferences() // Use default preferences as fallback
                    }
                }
                
                // Double-check preferences are not null before proceeding
                val preferences = currentPreferences ?: UserPreferences()
                
                // Create location request based on preferences
                locationRequest = createLocationRequest(preferences)
                
                try {
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest!!,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                    isTracking = true
                    
                    // CRITICAL FIX: Update AppStateManager directly instead of using events to prevent circular dependencies
                    serviceScope.launch {
                        try {
                            // Import the AppStateManager
                            val appStateManager = applicationContext.let { context ->
                                dagger.hilt.android.EntryPointAccessors
                                    .fromApplication(context, com.cosmiclaboratory.voyager.di.StateEntryPoint::class.java)
                                    .appStateManager()
                            }
                            appStateManager.updateTrackingStatus(
                                isActive = true, 
                                startTime = LocalDateTime.now(),
                                source = com.cosmiclaboratory.voyager.data.state.StateUpdateSource.LOCATION_SERVICE
                            )
                            Log.d(TAG, "CRITICAL: AppStateManager updated directly - tracking started")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update AppStateManager directly, fallback to event", e)
                            // Fallback to event dispatch only if direct update fails
                            val trackingEvent = TrackingEvent(
                                type = EventTypes.TRACKING_STARTED,
                                isActive = true,
                                reason = "User started location tracking",
                                source = "LocationTrackingService"
                            )
                            eventDispatcher.dispatchTrackingEvent(trackingEvent)
                        }
                    }
                    isPaused = false
                    updateNotification(getString(R.string.location_tracking_active))
                    Log.d(TAG, "Location tracking started successfully")
                    
                    // Notify the manager that service is running
                    locationServiceManager.notifyServiceStarted()
                    
                    // Update CurrentState to reflect tracking status
                    updateCurrentStateTrackingStatus(true)
                    
                    // Start monitoring permissions
                    startPermissionMonitoring()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception when requesting location updates: ${e.message}")
                    updateNotification(getString(R.string.location_permission_required))
                    locationServiceManager.notifyServiceStopped("Security exception: ${e.message}")
                    serviceScope.launch {
                        kotlinx.coroutines.delay(3000)
                        stopSelf()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error when starting location tracking: ${e.message}")
                    updateNotification(getString(R.string.location_tracking_error))
                    locationServiceManager.notifyServiceStopped("Error starting tracking: ${e.message}")
                    serviceScope.launch {
                        kotlinx.coroutines.delay(3000)
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical error in startLocationTracking: ${e.message}")
                updateNotification(getString(R.string.location_tracking_error))
                locationServiceManager.notifyServiceStopped("Critical error: ${e.message}")
                serviceScope.launch {
                    kotlinx.coroutines.delay(3000)
                    stopSelf()
                }
            }
        }
    }
    
    private fun stopLocationTracking() {
        if (!isTracking) return
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        
        // CRITICAL FIX: Update AppStateManager directly instead of using events to prevent circular dependencies
        serviceScope.launch {
            try {
                // Import the AppStateManager
                val appStateManager = applicationContext.let { context ->
                    dagger.hilt.android.EntryPointAccessors
                        .fromApplication(context, com.cosmiclaboratory.voyager.di.StateEntryPoint::class.java)
                        .appStateManager()
                }
                appStateManager.updateTrackingStatus(
                    isActive = false,
                    startTime = null,
                    source = com.cosmiclaboratory.voyager.data.state.StateUpdateSource.LOCATION_SERVICE
                )
                Log.d(TAG, "CRITICAL: AppStateManager updated directly - tracking stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update AppStateManager directly, fallback to event", e)
                // Fallback to event dispatch only if direct update fails
                val trackingEvent = TrackingEvent(
                    type = EventTypes.TRACKING_STOPPED,
                    isActive = false,
                    reason = "User stopped location tracking",
                    source = "LocationTrackingService"
                )
                eventDispatcher.dispatchTrackingEvent(trackingEvent)
            }
        }
        
        // Stop permission monitoring
        permissionCheckJob?.cancel()
        
        // Notify manager that service is stopping normally
        locationServiceManager.notifyServiceStopped("User stopped location tracking")
        
        // Update CurrentState to reflect tracking stopped
        updateCurrentStateTrackingStatus(false)
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private suspend fun saveLocation(androidLocation: AndroidLocation) {
        if (isPaused) return
        
        errorHandler.executeWithErrorHandling(
            operation = {
                val startTime = System.currentTimeMillis()
                
                // Smart location filtering to prevent spam when stationary
                if (!shouldSaveLocation(androidLocation)) {
                    logger.d(TAG, "Location filtered - accuracy=${androidLocation.accuracy}m")
                    return@executeWithErrorHandling Unit
                }
                
                val location = Location(
                    latitude = androidLocation.latitude,
                    longitude = androidLocation.longitude,
                    timestamp = ApiCompatibilityUtils.getCurrentDateTime(),
                    accuracy = androidLocation.accuracy,
                    speed = if (androidLocation.hasSpeed()) androidLocation.speed else null,
                    altitude = if (androidLocation.hasAltitude()) androidLocation.altitude else null,
                    bearing = if (androidLocation.hasBearing()) androidLocation.bearing else null
                )
                
                logger.logLocationProcessing(TAG, location.latitude, location.longitude, location.accuracy)
                
                // CRITICAL: Dispatch location event for real-time updates
                dispatchLocationEventSafely(location)
                
                withContext(Dispatchers.IO) {
                    // Use SmartDataProcessor for intelligent location processing
                    smartDataProcessor.processNewLocation(location)
                }
                
                locationCount++
                locationsSinceLastDetection++
                lastLocationTime = System.currentTimeMillis()
                lastSavedLocation = androidLocation
                
                val processingTime = System.currentTimeMillis() - startTime
                logger.perf(TAG, "LocationProcessing", processingTime)
                
                // Update stationary detection and adapt location request if needed
                val wasStationary = isInStationaryMode
                updateStationaryMode(androidLocation)
                
                // If stationary mode changed, update location request for better performance
                if (wasStationary != isInStationaryMode) {
                    updateLocationRequestForStationaryMode()
                }
                
                // Check if we should trigger automatic place detection
                checkForAutomaticPlaceDetection()
                
                // Update notification based on user preferences
                val updateFrequency = currentPreferences?.notificationUpdateFrequency ?: 10
                if (locationCount % updateFrequency == 0) {
                    val statusText = getString(R.string.locations_tracked, locationCount)
                    updateNotification(statusText)
                }
                
                Unit
            },
            context = ErrorContext(
                operation = "saveLocation",
                component = "LocationTrackingService",
                metadata = mapOf(
                    "latitude" to androidLocation.latitude.toString(),
                    "longitude" to androidLocation.longitude.toString(),
                    "accuracy" to androidLocation.accuracy.toString(),
                    "locationCount" to locationCount.toString()
                )
            )
        ).onFailure {
            // Update notification to show error but don't crash the service
            updateNotification(getString(R.string.location_tracking_error))
        }
    }
    
    /**
     * Smart filtering logic to determine if a location should be saved
     * Prevents spam from GPS drift when stationary
     * CRITICAL FIX: More permissive thresholds to ensure data collection
     */
    private fun shouldSaveLocation(newLocation: AndroidLocation): Boolean {
        val preferences = currentPreferences ?: return true
        
        // Always save the first location
        val lastLocation = lastSavedLocation ?: return true
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSave = currentTime - lastLocationTime
        
        // 1. Basic accuracy filtering with adaptive thresholds
        val maxAccuracyMeters = if (isInStationaryMode) {
            minOf(preferences.maxGpsAccuracyMeters, 100f) // More lenient when stationary
        } else {
            preferences.maxGpsAccuracyMeters
        }
        if (newLocation.accuracy > maxAccuracyMeters) {
            Log.d(TAG, "Location rejected: poor accuracy ${String.format("%.1f", newLocation.accuracy)}m > ${maxAccuracyMeters}m")
            return false
        }
        
        // 2. Calculate distance moved since last saved location
        val distanceMoved = LocationUtils.calculateDistance(
            lastLocation.latitude, lastLocation.longitude,
            newLocation.latitude, newLocation.longitude
        )
        
        // 3. Movement-based filtering with adaptive thresholds
        val minMovementThreshold = if (isInStationaryMode) {
            maxOf(newLocation.accuracy * 1.5, 10.0) // More lenient when stationary
        } else {
            maxOf(preferences.getEffectiveMinDistance().toDouble(), newLocation.accuracy.toDouble())
        }
        
        // 4. Time-based filtering with adaptive intervals
        val minTimeBetweenSaves = if (isInStationaryMode) {
            preferences.minTimeBetweenUpdatesSeconds * 1000L * 2 // Longer intervals when stationary
        } else {
            preferences.minTimeBetweenUpdatesSeconds * 1000L // Use user setting when moving
        }
        
        // 5. Speed validation - reject impossible speeds (GPS errors, now user-configurable)
        if (timeSinceLastSave > 1000) { // Only validate speed if at least 1 second has passed
            val speedMps = distanceMoved / (timeSinceLastSave / 1000.0) // meters per second
            val speedKmh = speedMps * 3.6 // convert m/s to km/h
            if (speedKmh > preferences.maxSpeedKmh) {
                Log.d(TAG, "Location rejected: impossible speed ${String.format("%.1f", speedKmh)}km/h > ${preferences.maxSpeedKmh}km/h")
                return false
            }
        }
        
        // 6. Enhanced decision logic - MUCH more permissive for debugging
        return when {
            // Force save if it's been too long (prevents data gaps)
            timeSinceLastSave > maxOf(minTimeBetweenSaves * 4, 600000L) -> {
                logger.d(TAG, "Location saved: long time gap (${timeSinceLastSave / 1000}s)")
                true
            }
            
            // Save if significant movement detected
            distanceMoved >= minMovementThreshold -> {
                logger.d(TAG, "Location saved: significant movement (${String.format("%.1f", distanceMoved)}m)")
                true
            }
            
            // Save if minimum time has passed AND some movement
            timeSinceLastSave >= minTimeBetweenSaves && distanceMoved >= 3.0 -> {
                logger.d(TAG, "Location saved: time + movement (${timeSinceLastSave / 1000}s, ${String.format("%.1f", distanceMoved)}m)")
                true
            }
            
            // Otherwise, don't save (likely GPS drift or stationary)
            else -> {
                Log.d(TAG, "Location filtered: stationary/drift (${String.format("%.1f", distanceMoved)}m, ${timeSinceLastSave / 1000}s)")
                false
            }
        }
    }
    
    /**
     * Update stationary mode detection based on movement patterns
     */
    private fun updateStationaryMode(newLocation: AndroidLocation) {
        val lastLocation = lastSavedLocation
        if (lastLocation == null) {
            stationaryStartTime = System.currentTimeMillis()
            return
        }
        
        val distanceMoved = LocationUtils.calculateDistance(
            lastLocation.latitude, lastLocation.longitude,
            newLocation.latitude, newLocation.longitude
        )
        
        val currentTime = System.currentTimeMillis()
        val stationaryThresholdMs = 5 * 60 * 1000L // 5 minutes
        val movementThreshold = 20.0 // 20 meters
        
        if (distanceMoved > movementThreshold) {
            // Significant movement detected - exit stationary mode
            isInStationaryMode = false
            stationaryStartTime = currentTime
        } else {
            // Check if we've been stationary long enough to enter stationary mode
            if (!isInStationaryMode && (currentTime - stationaryStartTime) > stationaryThresholdMs) {
                isInStationaryMode = true
            }
        }
    }
    
    /**
     * Update location request when stationary mode changes for better performance
     */
    private fun updateLocationRequestForStationaryMode() {
        if (!isTracking || currentPreferences == null) return
        
        serviceScope.launch {
            try {
                // Remove current location updates
                fusedLocationClient.removeLocationUpdates(locationCallback)
                
                // Create new location request with updated settings
                locationRequest = createLocationRequest(currentPreferences!!)
                
                // Restart location updates with new settings
                fusedLocationClient.requestLocationUpdates(
                    locationRequest!!,
                    locationCallback,
                    Looper.getMainLooper()
                )
                
                // Update notification to reflect mode change
                val modeText = if (isInStationaryMode) {
                    "Stationary mode - reduced frequency"
                } else {
                    "Active tracking"
                }
                updateNotification(modeText)
                
            } catch (e: SecurityException) {
                // Handle permission issues gracefully
                updateNotification(getString(R.string.location_tracking_error))
            }
        }
    }
    
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Notification permission not required on older Android versions
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted - notification channel creation skipped")
            return
        }
        
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification for location tracking service"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(status: String = getString(R.string.location_tracking_active)): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create action buttons
        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME_TRACKING
            }
            val resumePendingIntent = PendingIntent.getService(
                this,
                1,
                resumeIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                getString(R.string.notification_action_resume),
                resumePendingIntent
            )
        } else {
            val pauseIntent = Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE_TRACKING
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                2,
                pauseIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_pause),
                pausePendingIntent
            )
        }
        
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_delete,
            getString(R.string.notification_action_stop),
            stopPendingIntent
        )
        
        // Build detailed content text
        val contentText = buildString {
            append(status)
            if (locationCount > 0) {
                append(" • ${getString(R.string.locations_tracked, locationCount)}")
            }
            if (lastLocationTime > 0) {
                val timeStr = timeFormat.format(lastLocationTime)
                append(" • ${getString(R.string.last_update, timeStr)}")
            }
        }
        
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.location_tracking_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
        
        // CRITICAL FIX: Add color and priority based on tracking state with null safety
        try {
            if (isPaused) {
                notificationBuilder.setColor(resources.getColor(android.R.color.holo_orange_dark, null))
            } else {
                notificationBuilder.setColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
        } catch (e: Exception) {
            // If color setting fails, continue without color
            Log.w(TAG, "Failed to set notification color", e)
        }
        
        return try {
            notificationBuilder.build()
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to build notification", e)
            // Return a simple fallback notification
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Location Tracking")
                .setContentText("Service running")
                .setSmallIcon(R.drawable.ic_notification_location)
                .build()
        }
    }
    
    private fun updateNotification(status: String) {
        if (!hasNotificationPermission()) {
            Log.v(TAG, "Skipping notification update - permission not granted")
            return
        }
        
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification(status))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "CRITICAL: Service destroyed notification received")
        setServiceRunning(false)
        
        // Stop permission monitoring
        permissionCheckJob?.cancel()
        
        // Notify manager before stopping if we're still tracking
        if (isTracking) {
            locationServiceManager.notifyServiceStopped("Service destroyed")
        }
        
        stopLocationTracking()
        
        // CRITICAL FIX: Graceful scope cancellation to prevent JobCancellationException
        try {
            // Cancel scope but give time for ongoing operations to complete
            serviceScope.launch {
                delay(500) // Give 500ms for ongoing operations to finish
                serviceScope.cancel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during graceful scope cancellation", e)
            // Force cancel if graceful cancellation fails
            serviceScope.cancel()
        }
    }
    
    private fun startPermissionMonitoring() {
        permissionCheckJob?.cancel()
        
        // CRITICAL FIX: Check if scope is active before launching
        if (!serviceScope.isActive) {
            Log.w(TAG, "Service scope is not active, skipping permission monitoring")
            return
        }
        
        permissionCheckJob = serviceScope.launch {
            while (isActive && isTracking) {
                delay(60000) // Check every minute
                if (!hasRequiredPermissions()) {
                    Log.w(TAG, "Location permissions lost during service execution")
                    locationServiceManager.notifyServiceStopped("Location permissions were revoked")
                    stopSelf()
                    break
                }
            }
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // Check background location for Android 10+ only when targeting background usage
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
        
        return fineLocationGranted && coarseLocationGranted && backgroundLocationGranted
    }
    
    private fun createLocationRequest(preferences: UserPreferences): LocationRequest {
        // Adaptive configuration based on stationary mode
        val updateInterval = if (isInStationaryMode) {
            // Longer intervals when stationary to save battery
            maxOf(preferences.getEffectiveUpdateInterval() * 2, 60000L) // At least 1 minute
        } else {
            preferences.getEffectiveUpdateInterval()
        }
        
        val minDistance = if (isInStationaryMode) {
            // Higher distance threshold when stationary to prevent GPS drift
            maxOf(preferences.getEffectiveMinDistance() * 1.5f, 15f) // At least 15m
        } else {
            preferences.getEffectiveMinDistance()
        }
        
        val priority = if (isInStationaryMode) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY // Save battery when stationary
        } else {
            when (preferences.trackingAccuracyMode) {
                TrackingAccuracyMode.POWER_SAVE -> Priority.PRIORITY_LOW_POWER
                TrackingAccuracyMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
                TrackingAccuracyMode.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
            }
        }
        
        return LocationRequest.Builder(priority, updateInterval).apply {
            setMinUpdateDistanceMeters(minDistance)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true) // Wait for accurate locations to reduce GPS noise
            setMaxUpdateDelayMillis(updateInterval * 2) // Allow some delay for better accuracy
            
            // Set minimum update interval to prevent rapid-fire updates
            setMinUpdateIntervalMillis(maxOf(updateInterval / 2, 15000L)) // At least 15 seconds
        }.build()
    }
    
    private suspend fun restartLocationTracking() {
        if (!isTracking) return
        
        // Stop current tracking
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        // Create new location request with updated preferences
        currentPreferences?.let { preferences ->
            locationRequest = createLocationRequest(preferences)
            
            // Restart tracking with new settings
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest!!,
                    locationCallback,
                    Looper.getMainLooper()
                )
                updateNotification(getString(R.string.location_tracking_active))
            } catch (e: SecurityException) {
                locationServiceManager.notifyServiceStopped("Security exception during restart")
                stopSelf()
            }
        }
    }
    
    private fun pauseLocationTracking() {
        if (!isTracking || isPaused) return
        
        isPaused = true
        updateNotification(getString(R.string.tracking_paused_by_user))
    }
    
    private fun resumeLocationTracking() {
        if (!isTracking || !isPaused) return
        
        isPaused = false
        updateNotification(getString(R.string.location_tracking_active))
    }
    
    /**
     * Check if we should trigger automatic place detection based on:
     * 1. Number of new locations since last detection
     * 2. Time since last detection 
     * 3. User preferences
     */
    private fun checkForAutomaticPlaceDetection() {
        val preferences = currentPreferences ?: return
        
        if (!preferences.enablePlaceDetection) {
            Log.d(TAG, "Place detection disabled in preferences")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastDetection = currentTime - lastPlaceDetectionTime
        val hoursToMs = preferences.placeDetectionFrequencyHours * 60 * 60 * 1000L
        
        // CRITICAL FIX: More aggressive trigger logic for better place detection
        // 1. Enough new locations have been collected
        // 2. Enough time has passed since last detection
        // 3. First detection after service restart (when lastPlaceDetectionTime is very old)
        val shouldTriggerByCount = locationsSinceLastDetection >= preferences.autoDetectTriggerCount
        val shouldTriggerByTime = timeSinceLastDetection >= hoursToMs && locationsSinceLastDetection > 0
        val shouldTriggerFirstTime = timeSinceLastDetection > (24 * 60 * 60 * 1000L) && locationsSinceLastDetection >= 10 // First detection after 10 locations
        
        if (shouldTriggerByCount || shouldTriggerByTime || shouldTriggerFirstTime) {
            val triggerReason = when {
                shouldTriggerFirstTime -> "first detection"
                shouldTriggerByCount -> "location count"
                shouldTriggerByTime -> "time elapsed"
                else -> "unknown"
            }
            Log.d(TAG, "Triggering automatic place detection ($triggerReason): " +
                "newLocations=$locationsSinceLastDetection (trigger=${preferences.autoDetectTriggerCount}), " +
                "hours=${timeSinceLastDetection / (60 * 60 * 1000)} (trigger=${preferences.placeDetectionFrequencyHours})")
            
            serviceScope.launch {
                enqueueWorkWithRetry(preferences, currentTime)
            }
        }
    }
    
    /**
     * Enqueue WorkManager tasks using centralized WorkManagerHelper
     */
     private suspend fun enqueueWorkWithRetry(preferences: UserPreferences, currentTime: Long) {
        Log.d(TAG, "Enqueuing place detection work using WorkManagerHelper...")
        
        val result = workManagerHelper.enqueuePlaceDetectionWork(preferences, isOneTime = true)
        
        when (result) {
            is EnqueueResult.Success -> {
                // Success - reset counters
                locationsSinceLastDetection = 0
                lastPlaceDetectionTime = currentTime
                Log.d(TAG, "Place detection work enqueued successfully: ${result.workId}")
            }
            is EnqueueResult.Failed -> {
                Log.e(TAG, "WorkManager enqueue failed", result.exception)
                
                // FALLBACK: Try manual place detection as last resort
                try {
                    Log.w(TAG, "Attempting fallback manual place detection...")
                    val newPlaces = placeDetectionUseCases.detectNewPlaces()
                    Log.d(TAG, "Fallback manual place detection completed with ${newPlaces.size} places")
                    
                    // Reset counters on successful manual detection
                    locationsSinceLastDetection = 0
                    lastPlaceDetectionTime = currentTime
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "Fallback manual place detection also failed", fallbackException)
                }
            }
        }
    }
    
    /**
     * Dispatch location event with standardized error handling
     */
    private suspend fun dispatchLocationEventSafely(location: Location) {
        errorHandler.executeWithErrorHandling(
            operation = {
                val locationEvent = LocationEvent(
                    type = EventTypes.LOCATION_UPDATE,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    source = "LocationTrackingService"
                )
                eventDispatcher.dispatchLocationEvent(locationEvent)
                Unit
            },
            context = ErrorContext(
                operation = "dispatchLocationEvent",
                component = "LocationTrackingService"
            )
        ).onFailure {
            // Continue processing even if event dispatch fails
            Log.d(TAG, "Failed to dispatch location event but continuing processing")
        }
    }
    
    /**
     * Update CurrentState tracking status to sync with service state
     */
    private fun updateCurrentStateTrackingStatus(isActive: Boolean) {
        // CRITICAL FIX: Check if scope is active before launching to prevent JobCancellationException
        if (!serviceScope.isActive) {
            Log.w(TAG, "Service scope is not active, skipping state update")
            return
        }
        
        serviceScope.launch {
            try {
                errorHandler.executeWithErrorHandling(
                    operation = {
                        val startTime = if (isActive) LocalDateTime.now() else null
                        currentStateRepository.updateTrackingStatus(isActive, startTime)
                        Log.d(TAG, "Updated CurrentState tracking status: isActive=$isActive")
                        Unit
                },
                context = ErrorContext(
                    operation = "updateCurrentStateTrackingStatus",
                    component = "LocationTrackingService",
                    metadata = mapOf("isActive" to isActive.toString())
                )
                ).onFailure {
                    Log.d(TAG, "Failed to update CurrentState tracking status but continuing service operation")
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "CRITICAL ERROR: Failed to update tracking status", e)
                // Don't rethrow CancellationException as it's expected during service shutdown
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error updating tracking status", e)
            }
        }
    }
    
    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val ACTION_PAUSE_TRACKING = "ACTION_PAUSE_TRACKING"
        const val ACTION_RESUME_TRACKING = "ACTION_RESUME_TRACKING"
        
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        private const val TAG = "LocationTrackingService"
        
        // CRITICAL FIX: Static flag to track service running state reliably
        @Volatile
        private var serviceRunning = false
        
        /**
         * Reliable method to check if service is running
         * This replaces the deprecated getRunningServices() approach
         */
        @JvmStatic
        fun isServiceRunning(): Boolean = serviceRunning
        
        private fun setServiceRunning(running: Boolean) {
            serviceRunning = running
            Log.d(TAG, "Service running state: $running")
        }
    }
}