package com.cosmiclaboratory.voyager.capture

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.storage.database.dao.RawActivitySampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.RawActivitySampleEntity
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawActivitySampleDao: RawActivitySampleDao
) {
    private var pendingIntent: PendingIntent? = null
    @Volatile private var activeSessionId: Long = 0
    /** Timestamp of the last AR transition received. Used to detect stale registrations. */
    @Volatile
    var lastTransitionAt: Long = System.currentTimeMillis()
        private set

    /** Called by ActivityTransitionReceiver when a transition is received. */
    fun onTransitionReceived() {
        lastTransitionAt = System.currentTimeMillis()
    }

    /**
     * Re-register for activity transitions if none received within [timeoutMs].
     * Android battery optimization can silently kill the PendingIntent receiver,
     * causing 17-22 hour gaps in AR data (observed in real phone data).
     */
    @Suppress("MissingPermission")
    fun reRegisterIfStale(timeoutMs: Long = 1_800_000L) { // 30 minutes
        if (activeSessionId == 0L) return
        val silenceMs = System.currentTimeMillis() - lastTransitionAt
        if (silenceMs > timeoutMs) {
            val sessionId = activeSessionId
            stop()
            start(sessionId)
        }
    }

    @Suppress("MissingPermission")
    fun start(sessionId: Long) {
        // Unregister old PI to prevent leaking AR registrations on repeated start()
        pendingIntent?.let {
            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(it)
        }
        activeSessionId = sessionId

        val transitions = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE
        ).flatMap { activity ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }

        val request = ActivityTransitionRequest(transitions)
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(request, pendingIntent!!)
    }

    fun stop() {
        pendingIntent?.let {
            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(it)
        }
        pendingIntent = null
        activeSessionId = 0
    }

    suspend fun recordActivitySample(
        activityType: String,
        confidence: Int,
        source: String,
        transition: String?
    ) {
        // Discard late-arriving broadcasts after stop() — sessionId 0 means no active session
        if (activeSessionId == 0L) return

        rawActivitySampleDao.insert(
            RawActivitySampleEntity(
                activityType = activityType,
                confidence = confidence,
                source = source,
                transition = transition,
                capturedAt = System.currentTimeMillis(),
                trackingSessionId = activeSessionId
            )
        )
    }
}
