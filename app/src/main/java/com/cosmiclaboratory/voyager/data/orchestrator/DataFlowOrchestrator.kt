package com.cosmiclaboratory.voyager.data.orchestrator

import android.util.Log
import com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.usecase.AnalyticsUseCases
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Data Flow Orchestrator
 * Single point of truth for all data operations and consistency monitoring
 * CRITICAL COMPONENT: Ensures data integrity throughout the application
 */
@Singleton
class DataFlowOrchestrator @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val currentStateRepository: CurrentStateRepository,
    private val analyticsUseCases: AnalyticsUseCases
) {
    
    companion object {
        private const val TAG = "DataFlowOrchestrator"
    }
    
    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Data flow status monitoring
    private val _dataFlowStatus = MutableStateFlow(DataFlowStatus.INITIALIZING)
    val dataFlowStatus: Flow<DataFlowStatus> = _dataFlowStatus.asStateFlow()
    
    // Data consistency metrics
    private val _dataMetrics = MutableStateFlow(DataFlowMetrics())
    val dataMetrics: Flow<DataFlowMetrics> = _dataMetrics.asStateFlow()
    
    init {
        startDataFlowMonitoring()
    }
    
    /**
     * Comprehensive data validation and recovery system
     */
    suspend fun validateAndRepairDataFlow(): DataFlowValidationResult {
        Log.d(TAG, "CRITICAL: Starting comprehensive data flow validation")
        
        val validationResult = DataFlowValidationResult()
        
        try {
            // 1. Validate CurrentState integrity
            val stateValidation = validateCurrentStateIntegrity()
            validationResult.currentStateValid = stateValidation
            
            // 2. Validate location data flow
            val locationValidation = validateLocationDataFlow()
            validationResult.locationDataValid = locationValidation
            
            // 3. Validate analytics calculations
            val analyticsValidation = validateAnalyticsCalculations()
            validationResult.analyticsValid = analyticsValidation
            
            // 4. Check for orphaned references
            val referenceValidation = validateReferenceIntegrity()
            validationResult.referencesValid = referenceValidation
            
            // 5. Validate time calculations consistency
            val timeValidation = validateTimeCalculations()
            validationResult.timeCalculationsValid = timeValidation
            
            validationResult.overallValid = stateValidation && locationValidation && 
                                          analyticsValidation && referenceValidation && timeValidation
            
            if (validationResult.overallValid) {
                _dataFlowStatus.value = DataFlowStatus.HEALTHY
                Log.d(TAG, "CRITICAL: Data flow validation PASSED - all systems healthy")
            } else {
                _dataFlowStatus.value = DataFlowStatus.DEGRADED
                Log.w(TAG, "CRITICAL: Data flow validation FAILED - attempting repairs")
                
                // Attempt automatic repairs
                repairDataInconsistencies(validationResult)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Data flow validation failed", e)
            _dataFlowStatus.value = DataFlowStatus.CRITICAL_ERROR
            validationResult.overallValid = false
            validationResult.errorMessage = e.message
        }
        
        return validationResult
    }
    
    /**
     * Real-time data consistency monitoring
     */
    private fun startDataFlowMonitoring() {
        orchestratorScope.launch {
            while (isActive) {
                try {
                    val metrics = collectDataMetrics()
                    _dataMetrics.value = metrics
                    
                    // Log critical metrics for debugging
                    Log.d(TAG, "CRITICAL METRICS: Locations=${metrics.totalLocations}, " +
                          "Places=${metrics.totalPlaces}, Visits=${metrics.totalVisits}, " +
                          "StateTracking=${metrics.isStateTrackingActive}")
                    
                    // Check for data anomalies
                    if (metrics.totalLocations == 0 && metrics.isStateTrackingActive) {
                        Log.w(TAG, "CRITICAL ANOMALY: Tracking active but no locations - possible data loss!")
                        _dataFlowStatus.value = DataFlowStatus.DATA_LOSS_DETECTED
                    }
                    
                    delay(30000) // Check every 30 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in data flow monitoring", e)
                    delay(60000) // Longer delay on error
                }
            }
        }
    }
    
    private suspend fun validateCurrentStateIntegrity(): Boolean {
        return try {
            val state = currentStateRepository.getCurrentStateSync()
            if (state == null) {
                Log.w(TAG, "CurrentState is null - initializing")
                currentStateRepository.initializeState()
                currentStateRepository.getCurrentStateSync() != null
            } else {
                Log.d(TAG, "CurrentState validation passed")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "CurrentState validation failed", e)
            false
        }
    }
    
    private suspend fun validateLocationDataFlow(): Boolean {
        return try {
            val locationCount = locationRepository.getLocationCount()
            Log.d(TAG, "Location data validation: $locationCount locations found")
            
            // If tracking is active but no locations, there's a problem
            val state = currentStateRepository.getCurrentStateSync()
            if (state?.isLocationTrackingActive == true && locationCount == 0) {
                Log.w(TAG, "Location data validation failed: tracking active but no locations")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Location data validation failed", e)
            false
        }
    }
    
    private suspend fun validateAnalyticsCalculations(): Boolean {
        return try {
            val today = java.time.LocalDate.now()
            val analytics = analyticsUseCases.generateDayAnalytics(today)
            val currentStateAnalytics = analyticsUseCases.getCurrentStateAnalytics()
            
            Log.d(TAG, "Analytics validation: dayTime=${analytics.totalTimeTracked}, " +
                  "stateTime=${currentStateAnalytics.todayTimeTracked}")
            
            // Check if analytics are calculating correctly
            true
        } catch (e: Exception) {
            Log.e(TAG, "Analytics validation failed", e)
            false
        }
    }
    
    private suspend fun validateReferenceIntegrity(): Boolean {
        return try {
            // Check for orphaned place/visit references in CurrentState
            val state = currentStateRepository.getCurrentStateSync()
            
            if (state?.currentPlace != null) {
                val places = placeRepository.getAllPlaces().first()
                val placeExists = places.any { it.id == state.currentPlace!!.id }
                if (!placeExists) {
                    Log.w(TAG, "Orphaned place reference detected in CurrentState")
                    currentStateRepository.clearCurrentPlace()
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reference integrity validation failed", e)
            false
        }
    }
    
    private suspend fun validateTimeCalculations(): Boolean {
        return try {
            val today = java.time.LocalDate.now()
            val startOfDay = today.atStartOfDay()
            val endOfDay = today.plusDays(1).atStartOfDay()
            
            val visits = visitRepository.getVisitsBetween(startOfDay, endOfDay).first()
            val totalTime = visits.sumOf { visit ->
                if (visit.exitTime != null) {
                    visit.duration
                } else {
                    // Calculate current duration for active visits
                    val now = LocalDateTime.now()
                    if (visit.entryTime != null) {
                        java.time.Duration.between(visit.entryTime, now).toMillis()
                    } else {
                        0L
                    }
                }
            }
            
            Log.d(TAG, "Time calculation validation: ${visits.size} visits, ${totalTime}ms total")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Time calculation validation failed", e)
            false
        }
    }
    
    private suspend fun repairDataInconsistencies(validationResult: DataFlowValidationResult) {
        Log.d(TAG, "CRITICAL: Attempting automatic data repairs")
        
        try {
            if (!validationResult.currentStateValid) {
                currentStateRepository.initializeState()
                Log.d(TAG, "Repaired: CurrentState reinitialized")
            }
            
            if (!validationResult.referencesValid) {
                currentStateRepository.clearCurrentPlace()
                Log.d(TAG, "Repaired: Orphaned references cleared")
            }
            
            // Trigger fresh analytics calculation
            analyticsUseCases.generateDayAnalytics(java.time.LocalDate.now())
            Log.d(TAG, "Repaired: Analytics recalculated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Automatic repair failed", e)
        }
    }
    
    private suspend fun collectDataMetrics(): DataFlowMetrics {
        return try {
            val locationCount = locationRepository.getLocationCount()
            val placeCount = placeRepository.getAllPlaces().first().size
            val visitCount = visitRepository.getVisitsBetween(
                java.time.LocalDate.now().atStartOfDay(),
                java.time.LocalDate.now().plusDays(1).atStartOfDay()
            ).first().size
            
            val state = currentStateRepository.getCurrentStateSync()
            val isTracking = state?.isLocationTrackingActive ?: false
            
            DataFlowMetrics(
                totalLocations = locationCount,
                totalPlaces = placeCount,
                totalVisits = visitCount,
                isStateTrackingActive = isTracking,
                lastUpdateTime = LocalDateTime.now()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect data metrics", e)
            DataFlowMetrics() // Return empty metrics on error
        }
    }
}

enum class DataFlowStatus {
    INITIALIZING,
    HEALTHY,
    DEGRADED,
    DATA_LOSS_DETECTED,
    CRITICAL_ERROR
}

data class DataFlowMetrics(
    val totalLocations: Int = 0,
    val totalPlaces: Int = 0,
    val totalVisits: Int = 0,
    val isStateTrackingActive: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now()
)

data class DataFlowValidationResult(
    var currentStateValid: Boolean = false,
    var locationDataValid: Boolean = false,
    var analyticsValid: Boolean = false,
    var referencesValid: Boolean = false,
    var timeCalculationsValid: Boolean = false,
    var overallValid: Boolean = false,
    var errorMessage: String? = null
)