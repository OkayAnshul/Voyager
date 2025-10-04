package com.cosmiclaboratory.voyager.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.cosmiclaboratory.voyager.domain.model.BatteryRequirement
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PlaceDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val placeDetectionUseCases: PlaceDetectionUseCases
) : CoroutineWorker(context, workerParams) {

    init {
        Log.d(TAG, "PlaceDetectionWorker created successfully with @AssistedInject constructor")
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting place detection work")
            
            // Detect new places from location clusters
            val newPlaces = placeDetectionUseCases.detectNewPlaces()
            Log.d(TAG, "Detected ${newPlaces.size} new places")
            
            // Improve existing place categorizations
            placeDetectionUseCases.improveExistingPlaces()
            Log.d(TAG, "Finished improving existing places")
            
            // Return success with detected places count
            val outputData = workDataOf(
                "detected_places_count" to newPlaces.size
            )
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Place detection failed", e)
            // Log error and retry if not at max retry count
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "place_detection_work"
        private const val TAG = "PlaceDetectionWorker"
        private const val MAX_RETRY_COUNT = 3
        
        fun createPeriodicWorkRequest(preferences: UserPreferences): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .apply {
                    when (preferences.batteryRequirement) {
                        BatteryRequirement.NOT_LOW -> setRequiresBatteryNotLow(true)
                        BatteryRequirement.CHARGING -> setRequiresCharging(true)
                        BatteryRequirement.ANY -> {
                            // No battery constraints
                        }
                    }
                }
                .build()

            val repeatIntervalHours = preferences.placeDetectionFrequencyHours.toLong()
            val flexIntervalHours = maxOf(1L, repeatIntervalHours / 6) // 1/6 of interval, min 1 hour

            return PeriodicWorkRequestBuilder<PlaceDetectionWorker>(
                repeatInterval = repeatIntervalHours,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = flexIntervalHours,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
        
        fun createOneTimeWorkRequest(preferences: UserPreferences): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .apply {
                    when (preferences.batteryRequirement) {
                        BatteryRequirement.NOT_LOW -> setRequiresBatteryNotLow(true)
                        BatteryRequirement.CHARGING -> setRequiresCharging(true)
                        BatteryRequirement.ANY -> {
                            // No battery constraints
                        }
                    }
                }
                .build()

            return OneTimeWorkRequestBuilder<PlaceDetectionWorker>()
                .setConstraints(constraints)
                .build()
        }
    }
}