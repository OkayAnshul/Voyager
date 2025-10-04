package com.cosmiclaboratory.voyager.domain.usecase

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerManagementUseCases @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    
    companion object {
        private const val TAG = "WorkerManagementUseCases"
    }
    
    suspend fun initializeBackgroundWorkers() {
        try {
            Log.d(TAG, "Initializing background workers")
            
            // Wait a bit to ensure WorkManager is fully initialized
            kotlinx.coroutines.delay(500)
            
            val workManager = WorkManager.getInstance(context)
            val preferences = preferencesRepository.getUserPreferences().first()
            
            Log.d(TAG, "Initializing workers with preferences: " +
                "frequency=${preferences.placeDetectionFrequencyHours}h, " +
                "battery=${preferences.batteryRequirement}, " +
                "placeDetectionEnabled=${preferences.enablePlaceDetection}")
            
            if (preferences.enablePlaceDetection) {
                // Schedule periodic place detection with user preferences
                val placeDetectionWork = PlaceDetectionWorker.createPeriodicWorkRequest(preferences)
                
                workManager.enqueueUniquePeriodicWork(
                    PlaceDetectionWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE, // Replace to apply new user preferences
                    placeDetectionWork
                )
                
                // Also run an immediate one-time detection for existing data
                val immediateWork = PlaceDetectionWorker.createOneTimeWorkRequest(preferences)
                workManager.enqueue(immediateWork)
                
                Log.d(TAG, "Place detection workers scheduled successfully")
            } else {
                // Cancel place detection if disabled
                workManager.cancelUniqueWork(PlaceDetectionWorker.WORK_NAME)
                Log.d(TAG, "Place detection disabled, workers cancelled")
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
    
    fun cancelAllWorkers() {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(PlaceDetectionWorker.WORK_NAME)
            Log.d(TAG, "All background workers cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel workers", e)
        }
    }
}