package com.cosmiclaboratory.voyager.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.R
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.ApiCompatibilityUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

@HiltWorker
class GeofenceTransitionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val preferencesRepository: PreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val transitionType = inputData.getInt(KEY_TRANSITION_TYPE, -1)
            val placeId = inputData.getLong(KEY_PLACE_ID, -1L)

            if (transitionType == -1 || placeId == -1L) {
                return Result.failure(
                    Data.Builder()
                        .putString("error", "Invalid transition type or place ID")
                        .build()
                )
            }

            // Test that the reported transition is of interest
            if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
                transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
                
                handleGeofenceTransition(transitionType, placeId)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure(
                Data.Builder()
                    .putString("error", e.message ?: "Unknown error")
                    .build()
            )
        }
    }

    private suspend fun handleGeofenceTransition(
        transitionType: Int,
        placeId: Long
    ) {
        val now = ApiCompatibilityUtils.getCurrentDateTime()
        val places = placeRepository.getAllPlaces().first()
        val preferences = preferencesRepository.getCurrentPreferences()

        val place = places.find { it.id == placeId } ?: return

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                // Create new visit entry
                val visit = Visit(
                    placeId = placeId,
                    entryTime = now,
                    exitTime = null
                )
                visitRepository.insertVisit(visit)

                // Send notification if enabled
                if (preferences.enableArrivalNotifications) {
                    sendNotification("Arrived at ${place.name}", "Welcome back!")
                }
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                // Find active visit and close it
                val activeVisits = visitRepository.getActiveVisits()
                val activeVisit = activeVisits.find { it.placeId == placeId }

                if (activeVisit != null) {
                    visitRepository.endVisit(activeVisit.id, now)

                    // Send notification if enabled
                    if (preferences.enableDepartureNotifications) {
                        // Calculate duration for notification
                        val durationText = ApiCompatibilityUtils.calculateDurationText(activeVisit.entryTime, now)
                        sendNotification("Left ${place.name}", "Visit duration: $durationText")
                    }
                }
            }
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel if needed
        val channelId = "geofence_notifications"
        val channel = NotificationChannel(
            channelId,
            "Place Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for entering and leaving places"
        }
        notificationManager.createNotificationChannel(channel)

        // Create notification
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }


    companion object {
        private const val KEY_TRANSITION_TYPE = "transition_type"
        private const val KEY_PLACE_ID = "place_id"

        /**
         * Enqueue geofence transition work using WorkManager
         */
        fun enqueueGeofenceWork(
            context: Context,
            transitionType: Int,
            placeId: Long
        ) {
            val inputData = Data.Builder()
                .putInt(KEY_TRANSITION_TYPE, transitionType)
                .putLong(KEY_PLACE_ID, placeId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GeofenceTransitionWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }

        /**
         * Simplified version that processes basic geofence data
         */
        fun enqueueSimpleGeofenceWork(
            context: Context,
            transitionType: Int,
            triggeringGeofenceIds: List<String>
        ) {
            triggeringGeofenceIds.forEach { geofenceId ->
                val placeId = geofenceId.toLongOrNull()
                if (placeId != null) {
                    enqueueGeofenceWork(context, transitionType, placeId)
                }
            }
        }
    }
}