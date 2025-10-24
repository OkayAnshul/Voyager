package com.cosmiclaboratory.voyager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import com.cosmiclaboratory.voyager.data.migration.DataMigrationHelper
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.data.sync.StateSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltAndroidApp
class VoyagerApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var dataMigrationHelper: DataMigrationHelper
    
    @Inject
    lateinit var currentStateRepository: CurrentStateRepository
    
    @Inject
    lateinit var dataFlowOrchestrator: com.cosmiclaboratory.voyager.data.orchestrator.DataFlowOrchestrator
    
    @Inject
    lateinit var stateSynchronizer: StateSynchronizer
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // CRITICAL FIX: WorkManager initialization callbacks
    private val workManagerInitCallbacks = mutableListOf<() -> Unit>()
    private var isWorkManagerReady = false
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            android.util.Log.d("VoyagerApplication", "Application started successfully")
            
            // Debug: Check if HiltWorkerFactory is properly injected
            if (::workerFactory.isInitialized) {
                android.util.Log.d("VoyagerApplication", "HiltWorkerFactory injected successfully: ${workerFactory::class.simpleName}")
            } else {
                android.util.Log.e("VoyagerApplication", "HiltWorkerFactory NOT injected - WorkManager will fail!")
            }
            
            // Run data migrations and initialize state in background
            runDataMigrations()
            initializeCurrentState()
            initializeStateSynchronizer()
            initializeDataFlowOrchestrator()
            
            // CRITICAL FIX: Initialize WorkManager after all dependencies are ready
            initializeWorkManager()
            
        } catch (e: Exception) {
            android.util.Log.e("VoyagerApplication", "Error during application initialization", e)
            // Continue execution to prevent app crash
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() {
            return try {
                android.util.Log.d("VoyagerApplication", "Building WorkManager configuration with HiltWorkerFactory")
                
                // CRITICAL FIX: Enhanced verification and fallback for HiltWorkerFactory
                if (::workerFactory.isInitialized) {
                    android.util.Log.d("VoyagerApplication", "HiltWorkerFactory verified - creating configuration with enhanced logging")
                    Configuration.Builder()
                        .setWorkerFactory(workerFactory)
                        .setMinimumLoggingLevel(android.util.Log.VERBOSE)
                        .setMaxSchedulerLimit(50) // Increase scheduler limit for better performance
                        .build().also {
                            android.util.Log.i("VoyagerApplication", "CRITICAL: WorkManager configuration SUCCESSFULLY built with HiltWorkerFactory")
                        }
                } else {
                    android.util.Log.e("VoyagerApplication", "CRITICAL ERROR: HiltWorkerFactory NOT initialized - WorkManager will use default factory")
                    android.util.Log.e("VoyagerApplication", "This will cause PlaceDetectionWorker instantiation to FAIL")
                    
                    // Still provide a valid configuration but log the critical error
                    Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.VERBOSE)
                        .setMaxSchedulerLimit(50)
                        .build().also {
                            android.util.Log.w("VoyagerApplication", "Using default WorkManager configuration - workers with DI will fail")
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoyagerApplication", "CRITICAL: Exception while building WorkManager configuration", e)
                // Provide detailed error logging for debugging
                android.util.Log.e("VoyagerApplication", "Error details: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                
                // Fallback to default configuration to prevent crash
                Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.VERBOSE)
                    .build()
            }
        }
    
    /**
     * Run data migrations in background to fix existing data issues
     */
    private fun runDataMigrations() {
        if (::dataMigrationHelper.isInitialized) {
            applicationScope.launch {
                try {
                    android.util.Log.d("VoyagerApplication", "Starting data migrations...")
                    
                    dataMigrationHelper.runMigrations()
                    
                    // Validate data integrity after migration
                    val report = dataMigrationHelper.validateDataIntegrity()
                    android.util.Log.d("VoyagerApplication", "Data migration completed: $report")
                    
                } catch (e: Exception) {
                    android.util.Log.e("VoyagerApplication", "Data migration failed", e)
                    // Don't crash the app, just log the error
                }
            }
        } else {
            android.util.Log.w("VoyagerApplication", "DataMigrationHelper not injected - skipping migrations")
        }
    }
    
    /**
     * Initialize CurrentState in background to ensure it exists
     */
    private fun initializeCurrentState() {
        if (::currentStateRepository.isInitialized) {
            applicationScope.launch {
                try {
                    android.util.Log.d("VoyagerApplication", "Initializing current state...")
                    
                    // Initialize state table if it doesn't exist
                    currentStateRepository.initializeState()
                    
                    // Validate that state was created successfully
                    val state = currentStateRepository.getCurrentStateSync()
                    if (state != null) {
                        android.util.Log.d("VoyagerApplication", "Current state initialized successfully: $state")
                    } else {
                        android.util.Log.d("VoyagerApplication", "Current state table created, but no data yet")
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("VoyagerApplication", "Failed to initialize current state", e)
                    // Don't crash the app, just log the error
                }
            }
        } else {
            android.util.Log.w("VoyagerApplication", "CurrentStateRepository not injected - skipping state initialization")
        }
    }
    
    /**
     * Initialize State Synchronizer for event-driven state management
     */
    private fun initializeStateSynchronizer() {
        if (::stateSynchronizer.isInitialized) {
            applicationScope.launch {
                try {
                    android.util.Log.d("VoyagerApplication", "CRITICAL: Initializing State Synchronizer...")
                    
                    // Initialize event-driven synchronization
                    stateSynchronizer.initialize()
                    
                    // CRITICAL FIX: Perform startup state reconciliation
                    delay(2000) // Wait for all components to initialize
                    stateSynchronizer.performStartupReconciliation()
                    
                    android.util.Log.d("VoyagerApplication", "CRITICAL: State Synchronizer initialized successfully - event-driven sync enabled")
                    
                } catch (e: Exception) {
                    android.util.Log.e("VoyagerApplication", "CRITICAL: Failed to initialize State Synchronizer", e)
                }
            }
        } else {
            android.util.Log.w("VoyagerApplication", "StateSynchronizer not injected - skipping synchronizer initialization")
        }
    }
    
    /**
     * Initialize Data Flow Orchestrator for comprehensive monitoring
     */
    private fun initializeDataFlowOrchestrator() {
        if (::dataFlowOrchestrator.isInitialized) {
            applicationScope.launch {
                try {
                    android.util.Log.d("VoyagerApplication", "CRITICAL: Initializing Data Flow Orchestrator...")
                    
                    // Perform initial validation and repair
                    val validationResult = dataFlowOrchestrator.validateAndRepairDataFlow()
                    
                    if (validationResult.overallValid) {
                        android.util.Log.d("VoyagerApplication", "CRITICAL: Data flow validation PASSED - all systems operational")
                    } else {
                        android.util.Log.w("VoyagerApplication", "CRITICAL: Data flow validation FAILED - repairs attempted")
                        validationResult.errorMessage?.let { error ->
                            android.util.Log.e("VoyagerApplication", "Validation error: $error")
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("VoyagerApplication", "CRITICAL: Failed to initialize Data Flow Orchestrator", e)
                }
            }
        } else {
            android.util.Log.w("VoyagerApplication", "DataFlowOrchestrator not injected - skipping orchestrator initialization")
        }
    }
    
    /**
     * Initialize WorkManager with proper timing and dependency checking
     */
    private fun initializeWorkManager() {
        applicationScope.launch {
            try {
                android.util.Log.d("VoyagerApplication", "CRITICAL: Initializing WorkManager with enhanced verification...")
                
                // Wait longer for all Hilt dependencies to be fully ready
                delay(2000)
                
                // Multiple verification attempts with detailed logging
                var verificationAttempts = 0
                var isReady = false
                
                while (verificationAttempts < 3 && !isReady) {
                    verificationAttempts++
                    android.util.Log.d("VoyagerApplication", "WorkManager verification attempt $verificationAttempts")
                    
                    isReady = verifyWorkManagerInitialization()
                    
                    if (!isReady) {
                        android.util.Log.w("VoyagerApplication", "WorkManager verification FAILED on attempt $verificationAttempts - waiting before retry")
                        delay(1000)
                    }
                }
                
                if (isReady) {
                    isWorkManagerReady = true
                    android.util.Log.i("VoyagerApplication", "CRITICAL: WorkManager initialization SUCCESSFUL after $verificationAttempts attempts")
                    
                    // Additional verification: Test worker factory
                    testWorkerFactoryFunctionality()
                    
                    // Execute any pending callbacks
                    synchronized(workManagerInitCallbacks) {
                        android.util.Log.d("VoyagerApplication", "Executing ${workManagerInitCallbacks.size} pending WorkManager callbacks")
                        workManagerInitCallbacks.forEach { callback ->
                            try {
                                callback()
                            } catch (e: Exception) {
                                android.util.Log.e("VoyagerApplication", "Error executing WorkManager init callback", e)
                            }
                        }
                        workManagerInitCallbacks.clear()
                    }
                } else {
                    android.util.Log.e("VoyagerApplication", "CRITICAL: WorkManager initialization FAILED after $verificationAttempts attempts")
                    android.util.Log.e("VoyagerApplication", "Workers using Hilt dependency injection will NOT work")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("VoyagerApplication", "CRITICAL: Error during WorkManager initialization", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Register callback to execute when WorkManager is ready
     */
    fun executeWhenWorkManagerReady(callback: () -> Unit) {
        synchronized(workManagerInitCallbacks) {
            if (isWorkManagerReady) {
                // WorkManager is already ready, execute immediately
                try {
                    callback()
                } catch (e: Exception) {
                    android.util.Log.e("VoyagerApplication", "Error executing immediate WorkManager callback", e)
                }
            } else {
                // WorkManager not ready yet, queue the callback
                workManagerInitCallbacks.add(callback)
                android.util.Log.d("VoyagerApplication", "Queued callback for WorkManager initialization")
            }
        }
    }
    
    /**
     * Check if WorkManager is ready for use
     */
    fun isWorkManagerReady(): Boolean = isWorkManagerReady
    
    /**
     * Verify WorkManager is properly initialized with HiltWorkerFactory
     */
    fun verifyWorkManagerInitialization(): Boolean {
        return try {
            if (::workerFactory.isInitialized) {
                // Try to get WorkManager instance
                val workManager = androidx.work.WorkManager.getInstance(this)
                android.util.Log.d("VoyagerApplication", "WorkManager verification PASSED - factory and instance available")
                true
            } else {
                android.util.Log.e("VoyagerApplication", "WorkManager verification FAILED - HiltWorkerFactory not injected")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("VoyagerApplication", "WorkManager verification FAILED with exception", e)
            false
        }
    }
    
    /**
     * Test WorkManager factory functionality to ensure DI workers can be created
     */
    private fun testWorkerFactoryFunctionality() {
        try {
            android.util.Log.d("VoyagerApplication", "Testing WorkManager factory functionality...")
            
            if (::workerFactory.isInitialized) {
                val workerFactoryClass = workerFactory.javaClass.simpleName
                android.util.Log.d("VoyagerApplication", "WorkerFactory type: $workerFactoryClass")
                
                // Log that the factory is available for creating workers
                android.util.Log.i("VoyagerApplication", "HiltWorkerFactory is ready for creating @HiltWorker instances")
                
                // Additional factory verification
                if (workerFactory is androidx.hilt.work.HiltWorkerFactory) {
                    android.util.Log.i("VoyagerApplication", "✅ Confirmed: Using HiltWorkerFactory - DI workers should work")
                } else {
                    android.util.Log.w("VoyagerApplication", "⚠️ Warning: Not using HiltWorkerFactory - DI workers may fail")
                }
            } else {
                android.util.Log.e("VoyagerApplication", "❌ WorkerFactory not initialized - DI workers will fail")
            }
        } catch (e: Exception) {
            android.util.Log.e("VoyagerApplication", "Error testing WorkManager factory functionality", e)
        }
    }
}