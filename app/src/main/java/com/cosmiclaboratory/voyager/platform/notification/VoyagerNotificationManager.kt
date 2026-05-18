package com.cosmiclaboratory.voyager.platform.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cosmiclaboratory.voyager.R
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central notification manager for all Voyager notification channels and builders.
 * Call [createChannels] once from Application.onCreate().
 */
@Singleton
class VoyagerNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Channel IDs
        const val CHANNEL_TRACKING_STATUS = "tracking_status"
        const val CHANNEL_TRACKING_ALERTS = "tracking_alerts"
        const val CHANNEL_INSIGHTS_DAILY = "insights_daily"
        const val CHANNEL_INSIGHTS_WEEKLY = "insights_weekly"
        const val CHANNEL_USER_ACTIONS = "user_actions"
        const val CHANNEL_SYSTEM_HEALTH = "system_health"
        const val CHANNEL_EMERGENCY_ALERTS = "emergency_alerts"

        // Notification IDs
        const val NOTIFICATION_ID_TRACKING = 1001
        const val NOTIFICATION_ID_VISIT_CONFIRMATION = 2000 // offset by visitId
        const val NOTIFICATION_ID_INSIGHT = 3001
        const val NOTIFICATION_ID_HEALTH = 4001
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Creates all notification channels. Safe to call multiple times;
     * the system ignores channels that already exist.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_TRACKING_STATUS,
                "Tracking Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification while location tracking is active"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_TRACKING_ALERTS,
                "Tracking Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Permission degradation, GPS signal loss, and tracking interruptions"
            },
            NotificationChannel(
                CHANNEL_INSIGHTS_DAILY,
                "Daily Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily summary of places visited and distances traveled"
            },
            NotificationChannel(
                CHANNEL_INSIGHTS_WEEKLY,
                "Weekly Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Weekly travel patterns and statistics"
            },
            NotificationChannel(
                CHANNEL_USER_ACTIONS,
                "Visit Confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Confirm, rename, or dismiss detected visits"
            },
            NotificationChannel(
                CHANNEL_SYSTEM_HEALTH,
                "System Health",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Worker failures, data integrity issues, and diagnostics"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_EMERGENCY_ALERTS,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_MIN   // silent for v1
            ).apply {
                description = "Reserved for future critical alerts"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        notificationManager.createNotificationChannels(channels)
    }

    /**
     * Builds the ongoing foreground-service notification for location tracking.
     */
    fun showTrackingNotification(state: TrackingRuntimeState): Notification {
        val title = "Voyager Tracking"
        val text = when {
            state.isTracking -> "Tracking your journey"
            else -> "Tracking idle"
        }

        val pauseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAUSE_TRACKING
        }
        val pausePending = PendingIntent.getBroadcast(
            context, 0, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_TRACKING
        }
        val stopPending = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_TRACKING_STATUS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Pause", pausePending)
            .addAction(0, "Stop", stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Shows a notification prompting the user to confirm, rename, or dismiss a detected visit.
     */
    fun showVisitConfirmation(visitId: Long, placeName: String) {
        val confirmIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CONFIRM_VISIT
            putExtra(NotificationActionReceiver.EXTRA_VISIT_ID, visitId)
        }
        val confirmPending = PendingIntent.getBroadcast(
            context, visitId.toInt() * 10 + 1, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val renameIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RENAME_PLACE
            putExtra(NotificationActionReceiver.EXTRA_VISIT_ID, visitId)
        }
        val renamePending = PendingIntent.getBroadcast(
            context, visitId.toInt() * 10 + 2, renameIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_VISIT
            putExtra(NotificationActionReceiver.EXTRA_VISIT_ID, visitId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, visitId.toInt() * 10 + 3, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_USER_ACTIONS)
            .setContentTitle("Visit detected")
            .setContentText("Were you at $placeName?")
            .setSmallIcon(R.drawable.ic_notification_location)
            .setAutoCancel(true)
            .addAction(0, "Confirm", confirmPending)
            .addAction(0, "Rename", renamePending)
            .addAction(0, "Dismiss", dismissPending)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_VISIT_CONFIRMATION + visitId.toInt(),
            notification
        )
    }

    /**
     * Shows a daily or weekly insight notification.
     */
    fun showInsight(title: String, body: String) {
        // Tapping the recap opens the app.
        val launchIntent = Intent(context, com.cosmiclaboratory.voyager.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPending = PendingIntent.getActivity(
            context, 2, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_INSIGHTS_DAILY)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        notificationManager.notify(NOTIFICATION_ID_INSIGHT, notification)
    }

    /**
     * Shows a system-health alert (worker failures, integrity issues).
     */
    fun showHealthAlert(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM_HEALTH)
            .setContentTitle("Voyager Health")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HEALTH, notification)
    }
}
