package com.cosmiclaboratory.voyager.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.cosmiclaboratory.voyager.VoyagerApplication
import com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker
import com.cosmiclaboratory.voyager.data.worker.FallbackPlaceDetectionWorker
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized WorkManager operations with robust error handling and verification
 * 
 * This utility class provides:
 * - Robust WorkManager initialization verification
 * - Comprehensive retry logic with exponential backoff
 * - Centralized error handling and logging
 * - Fallback mechanisms when WorkManager fails
 * - Health monitoring for worker status
 */
@Singleton
class WorkManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "WorkManagerHelper"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_DELAY_MS = 1000L
    }
    
    /**
     * Verify WorkManager is properly initialized with HiltWorkerFactory
     */
    suspend fun verifyWorkManagerInitialization(): Boolean {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                delay(attempt * BASE_DELAY_MS) // Progressive delay: 0s, 1s, 2s, 3s, 4s
                
                // Verify HiltWorkerFactory is properly injected
                val app = context.applicationContext as? VoyagerApplication
                if (app?.verifyWorkManagerInitialization() == true) {
                    Log.d(TAG, "WorkManager verification PASSED on attempt ${attempt + 1}")
                    return true
                }
                
                Log.w(TAG, "WorkManager verification attempt ${attempt + 1} failed - retrying...")
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager verification attempt ${attempt + 1} exception", e)
            }
        }
        
        Log.e(TAG, "WorkManager verification FAILED after all attempts")
        return false
    }
    
    /**
     * Safely get WorkManager instance with verification
     */
    suspend fun getWorkManagerSafely(): WorkManager? {
        return try {
            if (verifyWorkManagerInitialization()) {
                WorkManager.getInstance(context)
            } else {
                Log.e(TAG, "Cannot get WorkManager - initialization verification failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WorkManager instance", e)
            null
        }
    }
    
    /**
     * Enqueue PlaceDetectionWorker with comprehensive retry logic and fallback support
     */
    suspend fun enqueuePlaceDetectionWork(
        preferences: UserPreferences,
        isOneTime: Boolean = true
    ): EnqueueResult {
        var lastException: Exception? = null
        
        // First try the main HiltWorker
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                delay(attempt * BASE_DELAY_MS) // Progressive delay
                
                val workManager = getWorkManagerSafely()
                if (workManager == null) {
                    Log.w(TAG, "WorkManager not available on attempt ${attempt + 1}")
                    return@repeat
                }
                
                val workRequest = if (isOneTime) {
                    PlaceDetectionWorker.createOneTimeWorkRequest(preferences)
                } else {
                    PlaceDetectionWorker.createPeriodicWorkRequest(preferences)
                }
                
                Log.d(TAG, "Attempting to enqueue PlaceDetectionWorker (attempt ${attempt + 1}, type=${if (isOneTime) "OneTime" else "Periodic"})...")
                
                if (isOneTime) {
                    workManager.enqueue(workRequest)
                } else {
                    workManager.enqueueUniquePeriodicWork(
                        PlaceDetectionWorker.WORK_NAME,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest as PeriodicWorkRequest
                    )
                }
                
                Log.d(TAG, "PlaceDetectionWorker enqueued successfully on attempt ${attempt + 1}")
                
                // CRITICAL FIX: Monitor worker execution to detect failures
                val workId = workRequest.id
                monitorWorkerAndFallbackIfNeeded(workId, preferences, isOneTime)
                
                return EnqueueResult.Success(workId)
                
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Attempt ${attempt + 1} failed for PlaceDetectionWorker", e)
            }
        }
        
        Log.e(TAG, "All $MAX_RETRY_ATTEMPTS attempts failed for PlaceDetectionWorker", lastException)
        
        // FALLBACK: Try the non-Hilt fallback worker
        return enqueueFallbackWorker(preferences, isOneTime)
    }
    
    /**
     * Enqueue fallback worker that doesn't use Hilt dependency injection
     */
    private suspend fun enqueueFallbackWorker(
        preferences: UserPreferences,
        isOneTime: Boolean = true
    ): EnqueueResult {
        return try {
            Log.w(TAG, "Attempting fallback worker as last resort...")
            
            val workManager = WorkManager.getInstance(context) // Direct instance without verification
            
            val workRequest = if (isOneTime) {
                FallbackPlaceDetectionWorker.createOneTimeWorkRequest(preferences)
            } else {
                FallbackPlaceDetectionWorker.createPeriodicWorkRequest(preferences)
            }
            
            if (isOneTime) {
                workManager.enqueue(workRequest)
            } else {
                workManager.enqueueUniquePeriodicWork(
                    FallbackPlaceDetectionWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest as PeriodicWorkRequest
                )
            }
            
            Log.w(TAG, "Fallback worker enqueued successfully")
            EnqueueResult.Success(workRequest.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Even fallback worker failed", e)
            EnqueueResult.Failed(e)
        }
    }
    
    /**
     * Monitor work progress with error handling
     */
    fun getWorkInfoFlow(workId: java.util.UUID): Flow<WorkInfo?>? {
        return try {
            WorkManager.getInstance(context).getWorkInfoByIdFlow(workId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get work info flow for $workId", e)
            null
        }
    }
    
    /**
     * Cancel all workers safely (both main and fallback)
     */
    suspend fun cancelAllWorkers(): Boolean {
        return try {
            val workManager = getWorkManagerSafely()
            if (workManager != null) {
                // Cancel both main and fallback workers
                workManager.cancelUniqueWork(PlaceDetectionWorker.WORK_NAME)
                workManager.cancelUniqueWork(FallbackPlaceDetectionWorker.WORK_NAME)
                Log.d(TAG, "All background workers (main and fallback) cancelled successfully")
                true
            } else {
                // Try direct cancellation without verification
                try {
                    val directWorkManager = WorkManager.getInstance(context)
                    directWorkManager.cancelUniqueWork(PlaceDetectionWorker.WORK_NAME)
                    directWorkManager.cancelUniqueWork(FallbackPlaceDetectionWorker.WORK_NAME)
                    Log.d(TAG, "All background workers cancelled via direct access")
                    true
                } catch (directException: Exception) {
                    Log.e(TAG, "Cannot cancel workers - WorkManager not available", directException)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel workers", e)
            false
        }
    }
    
    /**
     * Get work status for monitoring (checks both main and fallback workers)
     */
    suspend fun getWorkStatus(): WorkStatus {
        return try {
            val workManager = getWorkManagerSafely()
            if (workManager == null) {
                return WorkStatus.Unavailable("WorkManager not initialized")
            }
            
            // Check main worker first
            val mainWorkInfos = workManager.getWorkInfosForUniqueWork(PlaceDetectionWorker.WORK_NAME).get()
            
            // Check fallback worker
            val fallbackWorkInfos = workManager.getWorkInfosForUniqueWork(FallbackPlaceDetectionWorker.WORK_NAME).get()
            
            val allWorkInfos = mainWorkInfos + fallbackWorkInfos
            
            when {
                allWorkInfos.isEmpty() -> WorkStatus.NotScheduled
                allWorkInfos.any { it.state == WorkInfo.State.RUNNING } -> {
                    val runningInfo = allWorkInfos.first { it.state == WorkInfo.State.RUNNING }
                    val isFallback = runningInfo.outputData.getBoolean("fallback_worker", false)
                    WorkStatus.Running(if (isFallback) "fallback" else "main")
                }
                allWorkInfos.any { it.state == WorkInfo.State.ENQUEUED } -> {
                    val enqueuedInfo = allWorkInfos.first { it.state == WorkInfo.State.ENQUEUED }
                    val isFallback = enqueuedInfo.outputData.getBoolean("fallback_worker", false)
                    WorkStatus.Enqueued(if (isFallback) "fallback" else "main")
                }
                allWorkInfos.any { it.state == WorkInfo.State.FAILED } -> {
                    val failedInfo = allWorkInfos.first { it.state == WorkInfo.State.FAILED }
                    WorkStatus.Failed(failedInfo.outputData.getString("error") ?: "Unknown error")
                }
                allWorkInfos.any { it.state == WorkInfo.State.SUCCEEDED } -> {
                    val successInfo = allWorkInfos.first { it.state == WorkInfo.State.SUCCEEDED }
                    val isFallback = successInfo.outputData.getBoolean("fallback_worker", false)
                    WorkStatus.Completed(if (isFallback) "fallback" else "main")
                }
                else -> WorkStatus.Unknown
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get work status", e)
            WorkStatus.Unavailable("Exception: ${e.message}")
        }
    }
    
    /**
     * Monitor worker execution and automatically trigger fallback if main worker fails
     * ENHANCED: Multiple rapid checks to catch immediate failures
     */
    private fun monitorWorkerAndFallbackIfNeeded(
        workId: java.util.UUID,
        preferences: UserPreferences,
        isOneTime: Boolean
    ) {
        // Launch monitoring in a separate coroutine with enhanced rapid checking
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workManager = getWorkManagerSafely()
                if (workManager == null) {
                    Log.w(TAG, "Cannot monitor worker $workId - WorkManager unavailable, triggering fallback")
                    enqueueFallbackWorker(preferences, isOneTime)
                    return@launch
                }
                
                // ENHANCED: Multiple rapid checks to catch immediate failures
                val checkIntervals = listOf(500L, 1000L, 2000L, 5000L, 10000L) // 0.5s, 1s, 2s, 5s, 10s
                
                for ((index, delayMs) in checkIntervals.withIndex()) {
                    delay(delayMs)
                    
                    try {
                        val workInfo = workManager.getWorkInfoById(workId).get(3, TimeUnit.SECONDS)
                        Log.d(TAG, "Worker $workId check ${index + 1}: state=${workInfo.state}")
                        
                        when (workInfo.state) {
                            WorkInfo.State.FAILED -> {
                                Log.e(TAG, "CRITICAL: PlaceDetectionWorker $workId FAILED after ${delayMs}ms - triggering automatic fallback")
                                
                                // Check for constructor/instantiation failures
                                val outputData = workInfo.outputData
                                val failureReason = outputData.getString("failure_reason") ?: "Unknown failure"
                                val errorMessage = outputData.getString("error") ?: ""
                                
                                Log.e(TAG, "Failure reason: $failureReason")
                                Log.e(TAG, "Error message: $errorMessage")
                                
                                if (failureReason.contains("NoSuchMethodException") || 
                                    failureReason.contains("Could not instantiate") ||
                                    errorMessage.contains("Could not create Worker")) {
                                    Log.e(TAG, "CRITICAL: Constructor/DI failure detected - using fallback worker immediately")
                                }
                                
                                enqueueFallbackWorker(preferences, isOneTime)
                                return@launch
                            }
                            
                            WorkInfo.State.CANCELLED -> {
                                Log.w(TAG, "CRITICAL: PlaceDetectionWorker $workId was CANCELLED after ${delayMs}ms - triggering fallback")
                                enqueueFallbackWorker(preferences, isOneTime)
                                return@launch
                            }
                            
                            WorkInfo.State.RUNNING -> {
                                Log.d(TAG, "✅ PlaceDetectionWorker $workId is RUNNING after ${delayMs}ms - monitoring successful")
                                return@launch // Worker is running successfully, stop monitoring
                            }
                            
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d(TAG, "✅ PlaceDetectionWorker $workId SUCCEEDED after ${delayMs}ms - monitoring successful")
                                return@launch // Worker completed successfully
                            }
                            
                            WorkInfo.State.ENQUEUED -> {
                                Log.w(TAG, "⏳ PlaceDetectionWorker $workId still ENQUEUED after ${delayMs}ms")
                                if (index == checkIntervals.size - 1) {
                                    Log.e(TAG, "CRITICAL: Worker stuck in ENQUEUED state for too long - triggering fallback")
                                    enqueueFallbackWorker(preferences, isOneTime)
                                    return@launch
                                }
                                // Continue to next check
                            }
                            
                            WorkInfo.State.BLOCKED -> {
                                Log.w(TAG, "⚠️ PlaceDetectionWorker $workId is BLOCKED - constraints not met")
                                if (index >= 2) { // After 2 seconds of being blocked, consider fallback
                                    Log.e(TAG, "Worker blocked for too long - triggering fallback")
                                    enqueueFallbackWorker(preferences, isOneTime)
                                    return@launch
                                }
                            }
                        }
                        
                    } catch (timeoutException: java.util.concurrent.TimeoutException) {
                        Log.e(TAG, "CRITICAL: Timeout getting work info for $workId on check ${index + 1} - triggering fallback")
                        enqueueFallbackWorker(preferences, isOneTime)
                        return@launch
                    }
                }
                
                Log.w(TAG, "Worker monitoring completed without definitive state - no fallback needed")
                
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Exception during worker monitoring for $workId", e)
                Log.e(TAG, "Triggering fallback as safety measure")
                enqueueFallbackWorker(preferences, isOneTime)
            }
        }
    }
    
    /**
     * Perform comprehensive WorkManager health check
     */
    suspend fun performHealthCheck(): HealthCheckResult {
        val results = mutableMapOf<String, Boolean>()
        val errors = mutableListOf<String>()
        
        // Check 1: Basic initialization
        try {
            val initialized = verifyWorkManagerInitialization()
            results["initialization"] = initialized
            if (!initialized) errors.add("WorkManager initialization failed")
        } catch (e: Exception) {
            results["initialization"] = false
            errors.add("Initialization check exception: ${e.message}")
        }
        
        // Check 2: Instance creation
        try {
            val workManager = WorkManager.getInstance(context)
            results["instance_creation"] = true
        } catch (e: Exception) {
            results["instance_creation"] = false
            errors.add("Instance creation failed: ${e.message}")
        }
        
        // Check 3: HiltWorkerFactory injection
        try {
            val app = context.applicationContext as? VoyagerApplication
            val factoryInjected = app?.verifyWorkManagerInitialization() == true
            results["hilt_factory"] = factoryInjected
            if (!factoryInjected) errors.add("HiltWorkerFactory not properly injected")
        } catch (e: Exception) {
            results["hilt_factory"] = false
            errors.add("HiltWorkerFactory check exception: ${e.message}")
        }
        
        // Check 4: Work scheduling capability
        try {
            val workManager = getWorkManagerSafely()
            if (workManager != null) {
                // Try to create a simple work request (don't enqueue it)
                val testRequest = OneTimeWorkRequestBuilder<PlaceDetectionWorker>().build()
                results["work_scheduling"] = true
            } else {
                results["work_scheduling"] = false
                errors.add("Cannot create work requests")
            }
        } catch (e: Exception) {
            results["work_scheduling"] = false
            errors.add("Work scheduling test failed: ${e.message}")
        }
        
        val overallHealth = results.values.all { it }
        
        return HealthCheckResult(
            isHealthy = overallHealth,
            checks = results,
            errors = errors,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Result of work enqueue operations
 */
sealed class EnqueueResult {
    data class Success(val workId: java.util.UUID) : EnqueueResult()
    data class Failed(val exception: Exception) : EnqueueResult()
}

/**
 * Current work status
 */
sealed class WorkStatus {
    object NotScheduled : WorkStatus()
    data class Enqueued(val workerType: String = "main") : WorkStatus()
    data class Running(val workerType: String = "main") : WorkStatus()
    data class Completed(val workerType: String = "main") : WorkStatus()
    data class Failed(val error: String) : WorkStatus()
    data class Unavailable(val reason: String) : WorkStatus()
    object Unknown : WorkStatus()
}

/**
 * Health check result
 */
data class HealthCheckResult(
    val isHealthy: Boolean,
    val checks: Map<String, Boolean>,
    val errors: List<String>,
    val timestamp: Long
)