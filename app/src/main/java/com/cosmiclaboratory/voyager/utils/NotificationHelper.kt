package com.cosmiclaboratory.voyager.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cosmiclaboratory.voyager.MainActivity
import com.cosmiclaboratory.voyager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for managing app notifications
 *
 * Notification Channels:
 * - Daily Summary: Daily insights about user's places and visits
 * - Location Tracking: Persistent foreground service notification
 * - Place Alerts: Entry/exit notifications for specific places
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        // Notification Channels
        const val CHANNEL_DAILY_SUMMARY = "daily_summary"
        const val CHANNEL_LOCATION_TRACKING = "location_tracking"
        const val CHANNEL_PLACE_ALERTS = "place_alerts"
        const val CHANNEL_PLACE_REVIEW = "place_review"

        // Notification IDs
        const val NOTIFICATION_ID_DAILY_SUMMARY = 1001
        const val NOTIFICATION_ID_LOCATION_TRACKING = 1002
        const val NOTIFICATION_ID_PLACE_ALERT = 1003
        const val NOTIFICATION_ID_PLACE_DETECTED = 1004
        const val NOTIFICATION_ID_REVIEW_SUMMARY = 1005
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create all notification channels (Android O+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DAILY_SUMMARY,
                    "Daily Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily insights about your places and activities"
                    enableVibration(false)
                    setShowBadge(true)
                },

                NotificationChannel(
                    CHANNEL_LOCATION_TRACKING,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows when location tracking is active"
                    enableVibration(false)
                    setShowBadge(false)
                },

                NotificationChannel(
                    CHANNEL_PLACE_ALERTS,
                    "Place Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when you arrive or leave places"
                    enableVibration(true)
                    setShowBadge(true)
                },

                NotificationChannel(
                    CHANNEL_PLACE_REVIEW,
                    "Place Review",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for reviewing detected places"
                    enableVibration(false)
                    setShowBadge(true)
                }
            )

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    /**
     * Show daily summary notification
     */
    fun showDailySummaryNotification(
        title: String,
        message: String,
        expandedText: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Add expanded text if provided
        expandedText?.let {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(it)
            )
        }

        notificationManager.notify(NOTIFICATION_ID_DAILY_SUMMARY, builder.build())
    }

    /**
     * Show place alert notification
     */
    fun showPlaceAlertNotification(
        placeName: String,
        isEntry: Boolean
    ) {
        val message = if (isEntry) {
            "You arrived at $placeName"
        } else {
            "You left $placeName"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PLACE_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle("Place Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_PLACE_ALERT, builder.build())
    }

    /**
     * Show place detected notification (Week 6)
     * Shows when a new place is detected, with different variants for auto-accepted vs needs review
     */
    fun showPlaceDetectedNotification(
        placeName: String,
        category: String,
        confidence: Float,
        autoAccepted: Boolean,
        placeReviewId: Long? = null
    ) {
        val title = if (autoAccepted) {
            "New Place Detected"
        } else {
            "Review Needed"
        }

        val message = if (autoAccepted) {
            "$placeName ($category) - Auto-approved"
        } else {
            "$placeName ($category) - ${String.format("%.0f%%", confidence * 100)} confidence"
        }

        // Create intent to open Place Review screen
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "place_review")
            placeReviewId?.let { putExtra("review_id", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PLACE_REVIEW)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Add action buttons for review notifications
        if (!autoAccepted && placeReviewId != null) {
            // TODO: Add approve/edit/dismiss action buttons when broadcast receivers are implemented
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\nTap to review and confirm this place.")
            )
        }

        notificationManager.notify(NOTIFICATION_ID_PLACE_DETECTED, builder.build())
    }

    /**
     * Show daily review summary notification (Week 6)
     * Shows a summary of pending reviews at end of day
     */
    fun showDailyReviewSummaryNotification(
        pendingCount: Int,
        highPriorityCount: Int,
        placeSummary: String
    ) {
        if (pendingCount == 0) return // Don't show if no reviews pending

        val title = "Review Your Places"
        val message = "$pendingCount place${if (pendingCount > 1) "s" else ""} waiting for review"

        val expandedText = buildString {
            append("$pendingCount pending review${if (pendingCount > 1) "s" else ""}")
            if (highPriorityCount > 0) {
                append("\n$highPriorityCount high priority")
            }
            append("\n\n$placeSummary")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "place_review")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PLACE_REVIEW)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
            )

        notificationManager.notify(NOTIFICATION_ID_REVIEW_SUMMARY, builder.build())
    }

    /**
     * Cancel daily summary notification
     */
    fun cancelDailySummary() {
        notificationManager.cancel(NOTIFICATION_ID_DAILY_SUMMARY)
    }

    /**
     * Cancel all notifications
     */
    fun cancelAll() {
        notificationManager.cancelAll()
    }

    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
}
