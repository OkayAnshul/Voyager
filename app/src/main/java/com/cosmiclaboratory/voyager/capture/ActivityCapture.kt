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
     *
     * [start] resets the staleness clock, so after a re-register we wait a fresh
     * [timeoutMs] before trying again — otherwise, with no transition following, the
     * watchdog would thrash stop()/start() on every tick (lastTransitionAt stayed old).
     */
    @Suppress("MissingPermission")
    fun reRegisterIfStale(timeoutMs: Long = AR_STALE_TIMEOUT_MS) {
        if (!shouldReRegister(System.currentTimeMillis(), lastTransitionAt, activeSessionId, timeoutMs)) return
        val sessionId = activeSessionId
        stop()
        start(sessionId)
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

        // Anchor the staleness window to (re)registration time, not the singleton's
        // construction time — so the 30-min self-heal is measured from when AR was
        // actually armed, and a re-register resets the clock.
        lastTransitionAt = System.currentTimeMillis()
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

    companion object {
        /** Re-register if no AR transition is seen for this long — battery optimization
         *  can silently kill the PendingIntent receiver. */
        const val AR_STALE_TIMEOUT_MS = 1_800_000L // 30 minutes

        /** Pure staleness decision, extracted for testability. Re-register only while a
         *  session is active and the silence has exceeded [timeoutMs]. */
        internal fun shouldReRegister(
            now: Long,
            lastTransitionAt: Long,
            activeSessionId: Long,
            timeoutMs: Long,
        ): Boolean = activeSessionId != 0L && (now - lastTransitionAt) > timeoutMs
    }
}
