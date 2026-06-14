package com.cosmiclaboratory.voyager.platform.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.cosmiclaboratory.voyager.capture.ActivityCapture
import com.cosmiclaboratory.voyager.capture.LocationCapture
import com.cosmiclaboratory.voyager.capture.StepCapture
import com.cosmiclaboratory.voyager.platform.coordinator.TrackingRuntimeCoordinator
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
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
    @Inject lateinit var accelCapture: com.cosmiclaboratory.voyager.capture.AccelCapture
    @Inject lateinit var coordinator: TrackingRuntimeCoordinator
    @Inject lateinit var notificationManager: VoyagerNotificationManager
    @Inject lateinit var settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startMutex = Mutex()
    private val capturesStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()

        // Channels are owned solely by VoyagerNotificationManager (createChannels is
        // idempotent); the foreground notification is its warm, tappable builder.
        notificationManager.createChannels()
        startForeground(
            VoyagerNotificationManager.NOTIFICATION_ID_TRACKING,
            notificationManager.showTrackingNotification(coordinator.runtimeState.value)
        )

        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            android.util.Log.w("LocationCaptureService", "No location permission — stopping self")
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isCrashRestart = intent == null
        serviceScope.launch {
            ensureCapturesStarted(isCrashRestart)
        }
        return START_STICKY
    }

    /**
     * Idempotent + serialized capture startup (H6 fix).
     *
     * Two onStartCommand paths can race: a null-intent crash restart from the system
     * and a user-initiated explicit start. Without the mutex+flag both paths can call
     * `locationCapture.start()` concurrently, producing duplicate FLP callbacks.
     */
    private suspend fun ensureCapturesStarted(isCrashRestart: Boolean) {
        startMutex.withLock {
            if (capturesStarted.get()) return@withLock
            if (isCrashRestart) {
                coordinator.restoreFromCrash()
            }
            val sessionId = coordinator.runtimeState.value.activeSessionId
            if (sessionId == null) {
                android.util.Log.w("LocationCaptureService", "No active session — stopping self")
                stopSelf()
                return@withLock
            }
            // Respect the user's capture toggles — a disabled sensor is not registered.
            val settings = settingsRepository.observeSettings().value
            locationCapture.start(sessionId)
            if (settings.activityRecognitionEnabled) activityCapture.start(sessionId)
            if (settings.stepCountingEnabled) stepCapture.start(sessionId)
            if (settings.motionDetectionEnabled) accelCapture.start()
            capturesStarted.set(true)

            // Refresh the notification now that the session is live. Matters for the
            // crash-restart path, where onCreate ran before the session was restored and
            // the notification would otherwise read as paused. startForeground is the
            // update mechanism that isn't subject to POST_NOTIFICATIONS suppression.
            withContext(Dispatchers.Main) {
                startForeground(
                    VoyagerNotificationManager.NOTIFICATION_ID_TRACKING,
                    notificationManager.showTrackingNotification(coordinator.runtimeState.value)
                )
            }
        }
    }

    override fun onDestroy() {
        // Stop captures synchronously before cancelling scope — these methods
        // call removeLocationUpdates/unregisterReceiver which are synchronous.
        locationCapture.stop()
        activityCapture.stop()
        stepCapture.stop()
        accelCapture.stop()
        capturesStarted.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
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
