package com.cosmiclaboratory.voyager.platform.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cosmiclaboratory.voyager.capture.ActivityCapture
import com.cosmiclaboratory.voyager.capture.LocationCapture
import com.cosmiclaboratory.voyager.capture.StepCapture
import com.cosmiclaboratory.voyager.platform.coordinator.TrackingRuntimeCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin foreground service — sole responsibility is keeping the process alive
 * and forwarding capture callbacks. No business logic here.
 */
@AndroidEntryPoint
class LocationCaptureService : Service() {

    @Inject lateinit var locationCapture: LocationCapture
    @Inject lateinit var activityCapture: ActivityCapture
    @Inject lateinit var stepCapture: StepCapture
    @Inject lateinit var coordinator: TrackingRuntimeCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            android.util.Log.w("LocationCaptureService", "No location permission — stopping self")
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Null intent = process died and system restarted service
            serviceScope.launch {
                coordinator.restoreFromCrash()
                // Resume captures after crash restore so samples flow again
                val sessionId = coordinator.runtimeState.value.activeSessionId
                if (sessionId != null) {
                    locationCapture.start(sessionId)
                    activityCapture.start(sessionId)
                    stepCapture.start(sessionId)
                }
            }
        } else {
            serviceScope.launch {
                val sessionId = coordinator.runtimeState.value.activeSessionId
                if (sessionId == null) {
                    android.util.Log.w("LocationCaptureService", "No active session — stopping self")
                    stopSelf()
                    return@launch
                }
                locationCapture.start(sessionId)
                activityCapture.start(sessionId)
                stepCapture.start(sessionId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Stop captures synchronously before cancelling scope — these methods
        // call removeLocationUpdates/unregisterReceiver which are synchronous.
        locationCapture.stop()
        activityCapture.stop()
        stepCapture.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing location tracking notification"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voyager")
            .setContentText("Tracking your journey")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tracking_status"

        fun start(context: Context) {
            val intent = Intent(context, LocationCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationCaptureService::class.java))
        }
    }
}
