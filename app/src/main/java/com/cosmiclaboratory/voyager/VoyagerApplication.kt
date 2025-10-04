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
            
        } catch (e: Exception) {
            android.util.Log.e("VoyagerApplication", "Error during application initialization", e)
            // Continue execution to prevent app crash
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() {
            return try {
                android.util.Log.d("VoyagerApplication", "Building WorkManager configuration with HiltWorkerFactory")
                Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .setMinimumLoggingLevel(android.util.Log.DEBUG)
                    .build()
            } catch (e: Exception) {
                android.util.Log.e("VoyagerApplication", "Error building WorkManager configuration", e)
                // Fallback to default configuration to prevent crash
                Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.DEBUG)
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
}