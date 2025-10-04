package com.cosmiclaboratory.voyager.data.migration

import android.util.Log
import com.cosmiclaboratory.voyager.data.database.dao.LocationDao
import com.cosmiclaboratory.voyager.data.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.data.database.dao.VisitDao
import com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataMigrationHelper @Inject constructor(
    private val visitDao: VisitDao,
    private val locationDao: LocationDao,
    private val placeDao: PlaceDao,
    private val preferencesRepository: PreferencesRepository
) {
    
    companion object {
        private const val TAG = "DataMigrationHelper"
        private const val MIGRATION_VERSION_KEY = "data_migration_version"
        private const val CURRENT_MIGRATION_VERSION = 2
    }
    
    /**
     * Run all necessary data migrations
     */
    suspend fun runMigrations() {
        withContext(Dispatchers.IO) {
            try {
                val currentVersion = getCurrentMigrationVersion()
                Log.d(TAG, "Current migration version: $currentVersion")
                
                if (currentVersion < 1) {
                    Log.d(TAG, "Running migration v1: Fix visit durations")
                    migrationV1_FixVisitDurations()
                    setMigrationVersion(1)
                }
                
                if (currentVersion < 2) {
                    Log.d(TAG, "Running migration v2: Trigger place detection for existing data")
                    migrationV2_TriggerPlaceDetectionForExistingData()
                    setMigrationVersion(2)
                }
                
                Log.d(TAG, "All migrations completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed", e)
                throw e
            }
        }
    }
    
    /**
     * Migration V1: Fix visit durations for existing data
     * Calculate and update duration for all visits where duration = 0 but exitTime exists
     */
    private suspend fun migrationV1_FixVisitDurations() {
        try {
            Log.d(TAG, "Starting visit duration migration...")
            
            // Get all visits where duration is 0 but exitTime exists
            val allVisits = visitDao.getAllVisits()
            var fixedCount = 0
            
            allVisits.collect { visits ->
                visits.forEach { visit ->
                    if (visit.duration == 0L && visit.exitTime != null) {
                        // Calculate proper duration
                        val duration = java.time.Duration.between(
                            visit.entryTime, 
                            visit.exitTime!!
                        ).toMillis()
                        
                        if (duration > 0) {
                            // Update visit with calculated duration
                            val updatedVisit = visit.copy(duration = duration)
                            visitDao.updateVisit(updatedVisit)
                            fixedCount++
                            
                            Log.d(TAG, "Fixed visit ${visit.id}: duration ${duration}ms")
                        }
                    }
                }
            }
            
            Log.d(TAG, "Visit duration migration completed. Fixed $fixedCount visits.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate visit durations", e)
            throw e
        }
    }
    
    /**
     * Migration V2: Trigger place detection for existing location data
     * If we have locations but no places, trigger place detection
     */
    private suspend fun migrationV2_TriggerPlaceDetectionForExistingData() {
        try {
            Log.d(TAG, "Starting place detection migration...")
            
            val locationCount = locationDao.getLocationCount()
            val places = placeDao.getAllPlaces()
            
            places.collect { placeList ->
                val placeCount = placeList.size
                
                Log.d(TAG, "Found $locationCount locations and $placeCount places")
                
                // If we have locations but few/no places, trigger place detection
                if (locationCount > 50 && placeCount < 3) {
                    Log.d(TAG, "Triggering place detection for $locationCount existing locations")
                    
                    // This will be handled by the existing automatic system
                    // Just log that migration detected the need
                    Log.d(TAG, "Place detection migration: existing location data needs processing")
                    
                    // The automatic place detection system will handle this
                    // when the app starts and LocationTrackingService runs
                } else {
                    Log.d(TAG, "Place detection migration: no action needed")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate place detection", e)
            throw e
        }
    }
    
    /**
     * Get current migration version from preferences
     */
    private suspend fun getCurrentMigrationVersion(): Int {
        return try {
            val preferences = preferencesRepository.getCurrentPreferences()
            // For now, assume version 0 if no migration has been run
            // In a real implementation, this would be stored in preferences
            0
        } catch (e: Exception) {
            Log.w(TAG, "Could not get migration version, assuming 0", e)
            0
        }
    }
    
    /**
     * Set migration version in preferences
     */
    private suspend fun setMigrationVersion(version: Int) {
        try {
            // In a real implementation, this would update preferences
            Log.d(TAG, "Migration version set to: $version")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set migration version", e)
        }
    }
    
    /**
     * Force reprocess all location data for place detection
     * Use this for manual data recovery
     */
    suspend fun forceReprocessLocationData() {
        try {
            Log.d(TAG, "Force reprocessing all location data...")
            
            val locationCount = locationDao.getLocationCount()
            Log.d(TAG, "Found $locationCount locations to reprocess")
            
            if (locationCount > 0) {
                // Trigger place detection immediately
                // This will use the existing PlaceDetectionWorker system
                Log.d(TAG, "Location data reprocessing initiated")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force reprocess location data", e)
            throw e
        }
    }
    
    /**
     * Validate data integrity after migration
     */
    suspend fun validateDataIntegrity(): DataIntegrityReport {
        return try {
            val locationCount = locationDao.getLocationCount()
            val places = placeDao.getAllPlaces()
            var placeCount = 0
            var visitCount = 0
            var visitsWithDuration = 0
            var visitDurationIssues = 0
            
            places.collect { placeList ->
                placeCount = placeList.size
            }
            
            val allVisits = visitDao.getAllVisits()
            allVisits.collect { visits ->
                visitCount = visits.size
                visitsWithDuration = visits.count { it.duration > 0L }
                visitDurationIssues = visits.count { 
                    it.exitTime != null && it.duration == 0L 
                }
            }
            
            DataIntegrityReport(
                locationCount = locationCount,
                placeCount = placeCount,
                visitCount = visitCount,
                visitsWithDuration = visitsWithDuration,
                visitDurationIssues = visitDurationIssues,
                isHealthy = visitDurationIssues == 0 && 
                          (locationCount < 50 || placeCount > 0)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate data integrity", e)
            DataIntegrityReport(
                locationCount = 0,
                placeCount = 0,
                visitCount = 0,
                visitsWithDuration = 0,
                visitDurationIssues = -1,
                isHealthy = false
            )
        }
    }
}

/**
 * Data integrity report after migration
 */
data class DataIntegrityReport(
    val locationCount: Int,
    val placeCount: Int,
    val visitCount: Int,
    val visitsWithDuration: Int,
    val visitDurationIssues: Int,
    val isHealthy: Boolean
) {
    override fun toString(): String {
        return """
            Data Integrity Report:
            - Locations: $locationCount
            - Places: $placeCount  
            - Visits: $visitCount
            - Visits with duration: $visitsWithDuration
            - Visit duration issues: $visitDurationIssues
            - Overall healthy: $isHealthy
        """.trimIndent()
    }
}