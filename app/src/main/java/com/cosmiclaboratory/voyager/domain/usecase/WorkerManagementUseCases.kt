package com.cosmiclaboratory.voyager.domain.usecase

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.utils.WorkManagerHelper
import com.cosmiclaboratory.voyager.utils.EnqueueResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerManagementUseCases @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val workManagerHelper: WorkManagerHelper
) {
    
    companion object {
        private const val TAG = "WorkerManagementUseCases"
    }
    
    suspend fun initializeBackgroundWorkers() {
        try {
            Log.d(TAG, "Initializing background workers using WorkManagerHelper")
            
            val preferences = preferencesRepository.getUserPreferences().first()
            
            Log.d(TAG, "Initializing workers with preferences: " +
                "frequency=${preferences.placeDetectionFrequencyHours}h, " +
                "battery=${preferences.batteryRequirement}, " +
                "placeDetectionEnabled=${preferences.enablePlaceDetection}")
            
            if (preferences.enablePlaceDetection) {
                // Schedule periodic place detection with robust error handling
                val periodicResult = workManagerHelper.enqueuePlaceDetectionWork(preferences, isOneTime = false)
                when (periodicResult) {
                    is EnqueueResult.Success -> {
                        Log.d(TAG, "Periodic place detection worker scheduled successfully: ${periodicResult.workId}")
                    }
                    is EnqueueResult.Failed -> {
                        Log.e(TAG, "Failed to schedule periodic place detection worker", periodicResult.exception)
                    }
                }
                
                // Also run an immediate one-time detection for existing data
                val immediateResult = workManagerHelper.enqueuePlaceDetectionWork(preferences, isOneTime = true)
                when (immediateResult) {
                    is EnqueueResult.Success -> {
                        Log.d(TAG, "Immediate place detection worker scheduled successfully: ${immediateResult.workId}")
                    }
                    is EnqueueResult.Failed -> {
                        Log.e(TAG, "Failed to schedule immediate place detection worker", immediateResult.exception)
                    }
                }
                
            } else {
                // Cancel place detection if disabled
                val cancelled = workManagerHelper.cancelAllWorkers()
                if (cancelled) {
                    Log.d(TAG, "Place detection disabled, workers cancelled successfully")
                } else {
                    Log.w(TAG, "Place detection disabled, but worker cancellation failed")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize background workers", e)
            // Don't throw the exception - this is not critical for app functionality
        }
    }
    
    suspend fun updateWorkersForPreferences() {
        try {
            Log.d(TAG, "Updating workers for new preferences")
            initializeBackgroundWorkers() // Re-initialize with current preferences
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update workers for preferences", e)
        }
    }
    
    suspend fun cancelAllWorkers() {
        try {
            val cancelled = workManagerHelper.cancelAllWorkers()
            if (cancelled) {
                Log.d(TAG, "All background workers cancelled successfully")
            } else {
                Log.e(TAG, "Failed to cancel workers - WorkManager not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel workers", e)
        }
    }
    
    /**
     * Get current worker status for monitoring
     */
    suspend fun getWorkerStatus() = workManagerHelper.getWorkStatus()
    
    /**
     * Perform comprehensive health check on WorkManager
     */
    suspend fun performHealthCheck() = workManagerHelper.performHealthCheck()
    
}