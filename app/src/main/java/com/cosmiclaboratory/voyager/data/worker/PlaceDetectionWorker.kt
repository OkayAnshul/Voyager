package com.cosmiclaboratory.voyager.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
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

    override suspend fun doWork(): Result {
        return try {
            // Detect new places from location clusters
            val newPlaces = placeDetectionUseCases.detectNewPlaces()
            
            // Improve existing place categorizations
            placeDetectionUseCases.improveExistingPlaces()
            
            // Return success with detected places count
            val outputData = workDataOf(
                "detected_places_count" to newPlaces.size
            )
            Result.success(outputData)
            
        } catch (e: Exception) {
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
        private const val MAX_RETRY_COUNT = 3
        
        fun createPeriodicWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<PlaceDetectionWorker>(
                repeatInterval = 6, // Run every 6 hours
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 1, // Allow 1 hour flex
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
        
        fun createOneTimeWorkRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            return OneTimeWorkRequestBuilder<PlaceDetectionWorker>()
                .setConstraints(constraints)
                .build()
        }
    }
}