package com.cosmiclaboratory.voyager.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.cosmiclaboratory.voyager.MainActivity
import com.cosmiclaboratory.voyager.R
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.utils.ApiCompatibilityUtils
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {
    
    @Inject
    lateinit var locationRepository: LocationRepository
    
    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isTracking = false
    private var locationCount = 0
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_UPDATE_INTERVAL
    ).apply {
        setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE_FOR_UPDATES)
        setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
        setWaitForAccurateLocation(false)
    }.build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            
            locationResult.lastLocation?.let { androidLocation ->
                serviceScope.launch {
                    saveLocation(androidLocation)
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startLocationTracking()
            ACTION_STOP_TRACKING -> stopLocationTracking()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startLocationTracking() {
        if (isTracking) return
        
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
            updateNotification("Tracking started")
        } catch (e: SecurityException) {
            stopSelf()
        }
    }
    
    private fun stopLocationTracking() {
        if (!isTracking) return
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private suspend fun saveLocation(androidLocation: AndroidLocation) {
        try {
            val location = Location(
                latitude = androidLocation.latitude,
                longitude = androidLocation.longitude,
                timestamp = ApiCompatibilityUtils.getCurrentDateTime(),
                accuracy = androidLocation.accuracy,
                speed = if (androidLocation.hasSpeed()) androidLocation.speed else null,
                altitude = if (androidLocation.hasAltitude()) androidLocation.altitude else null,
                bearing = if (androidLocation.hasBearing()) androidLocation.bearing else null
            )
            
            locationRepository.insertLocation(location)
            locationCount++
            
            // Update notification every 10 locations
            if (locationCount % 10 == 0) {
                updateNotification("Tracked $locationCount locations")
            }
        } catch (e: Exception) {
            // Log error but don't crash the service
            updateNotification("Error saving location")
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for location tracking service"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(status: String = "Active"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.location_tracking_notification_title))
            .setContentText("$status • Tap to view")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        serviceScope.cancel()
    }
    
    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        
        // Update every 30 seconds when moving
        private const val LOCATION_UPDATE_INTERVAL = 30000L
        
        // Only update if moved at least 10 meters
        private const val MIN_DISTANCE_CHANGE_FOR_UPDATES = 10f
    }
}