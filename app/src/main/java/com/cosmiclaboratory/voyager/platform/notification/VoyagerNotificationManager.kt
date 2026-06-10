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
        const val NOTIFICATION_ID_TRACKING_ALERT = 4002
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
     *
     * This is the one notification a user sees *all day*, so it is deliberately warm and
     * privacy-forward — it reassures ("stays on this phone") and frames the value ("your
     * journey / timeline") instead of reading like surveillance ("tracking"). Tapping opens
     * the app; the Pause / Stop actions keep the user in control.
     */
    fun showTrackingNotification(state: TrackingRuntimeState): Notification {
        val (title, text) = trackingCopy(state.isTracking)

        // Tap the notification → open the app.
        val openIntent = Intent(context, com.cosmiclaboratory.voyager.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(
            context, 3, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification_location)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openPending)
            .addAction(0, "Pause", pausePending)
            .addAction(0, "Stop", stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Warm, time-of-day copy for the all-day tracking notification. Privacy first — it should
     * feel like a companion keeping your memories, never like something watching you.
     */
    private fun trackingCopy(isTracking: Boolean): Pair<String, String> {
        if (!isTracking) {
            return "Voyager is paused" to
                "Your timeline is on hold. Tap Resume whenever you're ready — everything stays on this phone."
        }
        return when (java.time.LocalTime.now().hour) {
            in 5..10 -> "Good morning ☀️" to
                "Voyager is quietly saving today's journey — only on this phone."
            in 11..16 -> "Out and about?" to
                "Capturing the places you go, just for you. Nothing ever leaves your device."
            in 17..21 -> "Winding down 🌙" to
                "Today's map is safe on your phone — private, and yours to keep."
            else -> "Resting easy" to
                "Your timeline stays right here on your device. Sleep tight ✨"
        }
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
     * Shows an actionable tracking alert — e.g. an automatic battery-budget downgrade or
     * permission degradation. Posts on the tracking-alerts channel so a change to *how*
     * Voyager tracks is never made silently behind the user's back. Tapping opens the app.
     */
    fun showTrackingAlert(title: String, message: String) {
        val launchIntent = Intent(context, com.cosmiclaboratory.voyager.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPending = PendingIntent.getActivity(
            context, 4, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TRACKING_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification_location)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPending)
            .build()

        notificationManager.notify(NOTIFICATION_ID_TRACKING_ALERT, notification)
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
