package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.usecase.DetectTripsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Recomputes detected trips from the visit history. Runs daily (~04:30) via
 * [WorkerScheduler], after the nightly rollups have settled.
 *
 * Detection runs for everyone — it is cheap, fully local, and means trips are already
 * populated the moment a user unlocks Pro. The Trips *screen* is the Pro gate.
 */
@HiltWorker
class TripDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val detectTrips: DetectTripsUseCase,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "trip_detection"
    }

    override suspend fun doWork(): Result = try {
        detectTrips.detectAndStore()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
