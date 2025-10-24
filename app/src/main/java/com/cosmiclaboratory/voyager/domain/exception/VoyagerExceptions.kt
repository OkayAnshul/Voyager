package com.cosmiclaboratory.voyager.domain.exception

import java.time.LocalDateTime

/**
 * Domain-specific exception hierarchy for Voyager application
 * CRITICAL: Provides type-safe error handling and categorization
 */

/**
 * Base exception for all Voyager-specific errors
 */
sealed class VoyagerException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String,
    val errorCategory: ErrorCategory,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isRecoverable: Boolean = true
) : Exception(message, cause) {
    
    /**
     * Additional context information for debugging and recovery
     */
    val context: MutableMap<String, Any> = mutableMapOf()
    
    fun addContext(key: String, value: Any): VoyagerException {
        context[key] = value
        return this
    }
    
    /**
     * Recovery actions for this exception
     */
    private val recoveryActions = mutableListOf<RecoveryAction>()
    
    fun addRecoveryAction(action: RecoveryAction): VoyagerException {
        recoveryActions.add(action)
        return this
    }
    
    /**
     * Get a user-friendly error message
     */
    abstract fun getUserMessage(): String
    
    /**
     * Get suggested recovery actions
     */
    abstract fun getRecoveryActions(): List<RecoveryAction>
}

/**
 * Location tracking related exceptions
 */
sealed class LocationTrackingException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    isRecoverable: Boolean = true
) : VoyagerException(message, cause, errorCode, ErrorCategory.LOCATION_TRACKING, isRecoverable = isRecoverable) {
    
    class PermissionDeniedException(
        requiredPermissions: List<String>
    ) : LocationTrackingException(
        message = "Location permissions denied: ${requiredPermissions.joinToString(", ")}",
        errorCode = "LOC_PERMISSION_DENIED",
        isRecoverable = true
    ) {
        init {
            addContext("requiredPermissions", requiredPermissions)
        }
        
        override fun getUserMessage(): String = 
            "Location access is required for tracking. Please grant location permissions in settings."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.REQUEST_PERMISSIONS,
            RecoveryAction.OPEN_APP_SETTINGS,
            RecoveryAction.EXPLAIN_FEATURE_NEED
        )
    }
    
    class LocationServiceUnavailableException(
        reason: String
    ) : LocationTrackingException(
        message = "Location service unavailable: $reason",
        errorCode = "LOC_SERVICE_UNAVAILABLE",
        isRecoverable = true
    ) {
        init {
            addContext("reason", reason)
        }
        
        override fun getUserMessage(): String = 
            "Location services are currently unavailable. Please check your device settings."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.ENABLE_LOCATION_SERVICES,
            RecoveryAction.CHECK_GPS_SETTINGS,
            RecoveryAction.RETRY_OPERATION
        )
    }
    
    class AccuracyTooLowException(
        currentAccuracy: Float,
        requiredAccuracy: Float
    ) : LocationTrackingException(
        message = "Location accuracy too low: ${currentAccuracy}m (required: ${requiredAccuracy}m)",
        errorCode = "LOC_ACCURACY_TOO_LOW",
        isRecoverable = true
    ) {
        init {
            addContext("currentAccuracy", currentAccuracy)
            addContext("requiredAccuracy", requiredAccuracy)
        }
        
        override fun getUserMessage(): String = 
            "GPS signal is weak. Moving to an open area may improve accuracy."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.IMPROVE_GPS_SIGNAL,
            RecoveryAction.WAIT_FOR_BETTER_SIGNAL,
            RecoveryAction.ADJUST_ACCURACY_SETTINGS
        )
    }
    
    class TrackingServiceFailedException(
        reason: String,
        cause: Throwable?
    ) : LocationTrackingException(
        message = "Location tracking service failed: $reason",
        cause = cause,
        errorCode = "LOC_SERVICE_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("reason", reason)
        }
        
        override fun getUserMessage(): String = 
            "Location tracking stopped unexpectedly. Attempting to restart..."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.RESTART_SERVICE,
            RecoveryAction.CLEAR_APP_CACHE,
            RecoveryAction.CONTACT_SUPPORT
        )
    }
}

/**
 * Place detection and management exceptions
 */
sealed class PlaceDetectionException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    isRecoverable: Boolean = true
) : VoyagerException(message, cause, errorCode, ErrorCategory.PLACE_DETECTION, isRecoverable = isRecoverable) {
    
    class InsufficientDataException(
        dataType: String,
        minimumRequired: Int,
        currentCount: Int
    ) : PlaceDetectionException(
        message = "Insufficient $dataType for place detection: $currentCount (minimum: $minimumRequired)",
        errorCode = "PLACE_INSUFFICIENT_DATA",
        isRecoverable = true
    ) {
        init {
            addContext("dataType", dataType)
            addContext("minimumRequired", minimumRequired)
            addContext("currentCount", currentCount)
        }
        
        override fun getUserMessage(): String = 
            "More location data is needed for accurate place detection. Continue using the app for better results."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.CONTINUE_TRACKING,
            RecoveryAction.MANUAL_PLACE_CREATION,
            RecoveryAction.ADJUST_DETECTION_SETTINGS
        )
    }
    
    class DuplicatePlaceException(
        placeName: String,
        existingPlaceId: Long
    ) : PlaceDetectionException(
        message = "Place '$placeName' already exists (ID: $existingPlaceId)",
        errorCode = "PLACE_DUPLICATE",
        isRecoverable = true
    ) {
        init {
            addContext("placeName", placeName)
            addContext("existingPlaceId", existingPlaceId)
        }
        
        override fun getUserMessage(): String = 
            "A similar place already exists. Would you like to merge them or create a new place?"
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.MERGE_PLACES,
            RecoveryAction.CREATE_NEW_PLACE,
            RecoveryAction.ADJUST_PLACE_BOUNDARIES
        )
    }
    
    class GeofenceCreationFailedException(
        placeId: Long,
        reason: String
    ) : PlaceDetectionException(
        message = "Failed to create geofence for place $placeId: $reason",
        errorCode = "PLACE_GEOFENCE_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("placeId", placeId)
            addContext("reason", reason)
        }
        
        override fun getUserMessage(): String = 
            "Could not set up automatic detection for this place. Manual tracking will still work."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.RETRY_GEOFENCE_CREATION,
            RecoveryAction.USE_MANUAL_TRACKING,
            RecoveryAction.ADJUST_GEOFENCE_SETTINGS
        )
    }
}

/**
 * Data validation and integrity exceptions
 */
sealed class DataValidationException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    isRecoverable: Boolean = true
) : VoyagerException(message, cause, errorCode, ErrorCategory.DATA_VALIDATION, isRecoverable = isRecoverable) {
    
    class InvalidLocationDataException(
        field: String,
        value: Any?,
        expectedRange: String
    ) : DataValidationException(
        message = "Invalid location data - $field: $value (expected: $expectedRange)",
        errorCode = "DATA_INVALID_LOCATION",
        isRecoverable = true
    ) {
        init {
            addContext("field", field)
            addContext("value", value ?: "null")
            addContext("expectedRange", expectedRange)
        }
        
        override fun getUserMessage(): String = 
            "Location data appears corrupted. Skipping invalid data points."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.SANITIZE_DATA,
            RecoveryAction.SKIP_INVALID_DATA,
            RecoveryAction.RESET_DATA_CACHE
        )
    }
    
    class CorruptedDatabaseException(
        tableName: String,
        corruptionDetails: String
    ) : DataValidationException(
        message = "Database corruption detected in $tableName: $corruptionDetails",
        errorCode = "DATA_DATABASE_CORRUPTED",
        isRecoverable = false
    ) {
        init {
            addContext("tableName", tableName)
            addContext("corruptionDetails", corruptionDetails)
        }
        
        override fun getUserMessage(): String = 
            "Database corruption detected. A repair may be needed to restore your data."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.REPAIR_DATABASE,
            RecoveryAction.RESTORE_FROM_BACKUP,
            RecoveryAction.CONTACT_SUPPORT
        )
    }
    
    class ValidationRuleViolationException(
        ruleName: String,
        violationDetails: String
    ) : DataValidationException(
        message = "Validation rule '$ruleName' violated: $violationDetails",
        errorCode = "DATA_VALIDATION_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("ruleName", ruleName)
            addContext("violationDetails", violationDetails)
        }
        
        override fun getUserMessage(): String = 
            "Data validation failed. Please check your input and try again."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.VALIDATE_INPUT,
            RecoveryAction.APPLY_DEFAULT_VALUES,
            RecoveryAction.SHOW_INPUT_HELP
        )
    }
    
    // Specific validation exceptions for different domain objects
    
    class LocationValidationException(
        message: String,
        recoveryAction: RecoveryAction = RecoveryAction.SKIP_INVALID_DATA
    ) : DataValidationException(
        message = "Location validation failed: $message",
        errorCode = "LOCATION_VALIDATION_FAILED",
        isRecoverable = true
    ) {
        init {
            addRecoveryAction(recoveryAction)
        }
        
        override fun getUserMessage(): String = 
            "Invalid location data. GPS coordinates may be inaccurate."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.IMPROVE_GPS_CONDITIONS,
            RecoveryAction.SKIP_INVALID_DATA,
            RecoveryAction.VALIDATE_GPS_DATA
        )
    }
    
    class PlaceValidationException(
        message: String,
        recoveryAction: RecoveryAction = RecoveryAction.SKIP_INVALID_DATA
    ) : DataValidationException(
        message = "Place validation failed: $message",
        errorCode = "PLACE_VALIDATION_FAILED",
        isRecoverable = true
    ) {
        init {
            addRecoveryAction(recoveryAction)
        }
        
        override fun getUserMessage(): String = 
            "Invalid place information. Please check the place details."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.SANITIZE_INPUT,
            RecoveryAction.SKIP_INVALID_DATA,
            RecoveryAction.MANUAL_REVIEW
        )
    }
    
    class VisitValidationException(
        message: String,
        recoveryAction: RecoveryAction = RecoveryAction.SKIP_INVALID_DATA
    ) : DataValidationException(
        message = "Visit validation failed: $message",
        errorCode = "VISIT_VALIDATION_FAILED",
        isRecoverable = true
    ) {
        init {
            addRecoveryAction(recoveryAction)
        }
        
        override fun getUserMessage(): String = 
            "Invalid visit data. Time or duration information may be incorrect."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.ADJUST_TIMESTAMP,
            RecoveryAction.SKIP_SHORT_VISITS,
            RecoveryAction.FIX_TIMESTAMP_ORDER
        )
    }
    
    class BatchValidationException(
        message: String,
        val individualFailures: List<Exception>,
        recoveryAction: RecoveryAction = RecoveryAction.SKIP_INVALID_DATA
    ) : DataValidationException(
        message = "Batch validation failed: $message",
        errorCode = "BATCH_VALIDATION_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("failureCount", individualFailures.size)
            addRecoveryAction(recoveryAction)
        }
        
        override fun getUserMessage(): String = 
            "Some data failed validation. Valid data will be processed."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.SKIP_INVALID_DATA,
            RecoveryAction.RETRY_WITH_VALIDATION,
            RecoveryAction.SHOW_VALIDATION_ERRORS
        )
    }
    
    class GeneralValidationException(
        message: String,
        recoveryAction: RecoveryAction = RecoveryAction.VALIDATE_INPUT
    ) : DataValidationException(
        message = "Validation failed: $message",
        errorCode = "GENERAL_VALIDATION_FAILED",
        isRecoverable = true
    ) {
        init {
            addRecoveryAction(recoveryAction)
        }
        
        override fun getUserMessage(): String = 
            "Data validation failed. Please check your input."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.VALIDATE_INPUT,
            RecoveryAction.SANITIZE_INPUT,
            RecoveryAction.SHOW_INPUT_HELP
        )
    }
}

/**
 * Database operation exceptions
 */
sealed class DatabaseException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    isRecoverable: Boolean = true
) : VoyagerException(message, cause, errorCode, ErrorCategory.DATABASE, isRecoverable = isRecoverable) {
    
    class ConnectionFailedException(
        cause: Throwable
    ) : DatabaseException(
        message = "Database connection failed",
        cause = cause,
        errorCode = "DB_CONNECTION_FAILED",
        isRecoverable = true
    ) {
        override fun getUserMessage(): String = 
            "Database connection error. Retrying connection..."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.RETRY_DATABASE_CONNECTION,
            RecoveryAction.CLEAR_DATABASE_CACHE,
            RecoveryAction.RESTART_APP
        )
    }
    
    class QueryTimeoutException(
        query: String,
        timeoutMs: Long
    ) : DatabaseException(
        message = "Database query timeout after ${timeoutMs}ms: $query",
        errorCode = "DB_QUERY_TIMEOUT",
        isRecoverable = true
    ) {
        init {
            addContext("query", query)
            addContext("timeoutMs", timeoutMs)
        }
        
        override fun getUserMessage(): String = 
            "Database operation is taking longer than expected. Please wait..."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.CANCEL_OPERATION,
            RecoveryAction.OPTIMIZE_DATABASE,
            RecoveryAction.RETRY_WITH_TIMEOUT
        )
    }
    
    class TransactionFailedException(
        operation: String,
        cause: Throwable
    ) : DatabaseException(
        message = "Database transaction failed for $operation",
        cause = cause,
        errorCode = "DB_TRANSACTION_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("operation", operation)
        }
        
        override fun getUserMessage(): String = 
            "Failed to save changes. Your data may not be up to date."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.RETRY_TRANSACTION,
            RecoveryAction.FORCE_SYNC,
            RecoveryAction.BACKUP_DATA
        )
    }
    
    class StateInitializationException(
        reason: String,
        val recoveryAction: RecoveryAction = RecoveryAction.RESTART_APP
    ) : DatabaseException(
        message = "Application state initialization failed: $reason",
        errorCode = "DB_STATE_INIT_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("reason", reason)
            addContext("recoveryAction", recoveryAction.displayName)
        }
        
        override fun getUserMessage(): String = 
            "App initialization failed. Attempting to recover..."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            recoveryAction,
            RecoveryAction.CLEAR_APP_CACHE,
            RecoveryAction.RESTART_APP
        )
    }
}

/**
 * Network and synchronization exceptions
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    isRecoverable: Boolean = true
) : VoyagerException(message, cause, errorCode, ErrorCategory.NETWORK, isRecoverable = isRecoverable) {
    
    class NoInternetConnectionException : NetworkException(
        message = "No internet connection available",
        errorCode = "NET_NO_CONNECTION",
        isRecoverable = true
    ) {
        override fun getUserMessage(): String = 
            "No internet connection. Some features may not work until connection is restored."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.CHECK_INTERNET_CONNECTION,
            RecoveryAction.ENABLE_OFFLINE_MODE,
            RecoveryAction.RETRY_WHEN_ONLINE
        )
    }
    
    class SyncFailedException(
        syncType: String,
        reason: String
    ) : NetworkException(
        message = "Synchronization failed for $syncType: $reason",
        errorCode = "NET_SYNC_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("syncType", syncType)
            addContext("reason", reason)
        }
        
        override fun getUserMessage(): String = 
            "Could not sync your data. Changes are saved locally and will sync when possible."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.RETRY_SYNC,
            RecoveryAction.FORCE_SYNC,
            RecoveryAction.CHECK_SYNC_SETTINGS
        )
    }
}

/**
 * System and configuration exceptions
 */
sealed class SystemException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    isRecoverable: Boolean = true
) : VoyagerException(message, cause, errorCode, ErrorCategory.SYSTEM, isRecoverable = isRecoverable) {
    
    class ConfigurationException(
        configKey: String,
        issue: String
    ) : SystemException(
        message = "Configuration error for '$configKey': $issue",
        errorCode = "SYS_CONFIG_ERROR",
        isRecoverable = true
    ) {
        init {
            addContext("configKey", configKey)
            addContext("issue", issue)
        }
        
        override fun getUserMessage(): String = 
            "App configuration issue detected. Resetting to default settings."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.RESET_CONFIGURATION,
            RecoveryAction.RESTORE_DEFAULT_SETTINGS,
            RecoveryAction.RESTART_APP
        )
    }
    
    class OutOfMemoryException(
        operation: String
    ) : SystemException(
        message = "Out of memory during $operation",
        errorCode = "SYS_OUT_OF_MEMORY",
        isRecoverable = true
    ) {
        init {
            addContext("operation", operation)
        }
        
        override fun getUserMessage(): String = 
            "Low memory detected. Clearing cache to free up space."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            RecoveryAction.CLEAR_CACHE,
            RecoveryAction.REDUCE_MEMORY_USAGE,
            RecoveryAction.RESTART_APP
        )
    }
    
    class StateManagementException(
        operation: String,
        val recoveryAction: RecoveryAction = RecoveryAction.REINITIALIZE_STATE
    ) : SystemException(
        message = "State management failure during $operation",
        errorCode = "SYS_STATE_MANAGEMENT_FAILED",
        isRecoverable = true
    ) {
        init {
            addContext("operation", operation)
            addContext("recoveryAction", recoveryAction.displayName)
        }
        
        override fun getUserMessage(): String = 
            "Application state error detected. Attempting to recover..."
        
        override fun getRecoveryActions(): List<RecoveryAction> = listOf(
            recoveryAction,
            RecoveryAction.CLEAR_APP_CACHE,
            RecoveryAction.RESTART_APP
        )
    }
}

/**
 * Error categories for classification and handling
 */
enum class ErrorCategory(
    val displayName: String,
    val priority: ErrorPriority,
    val defaultRecoveryStrategy: RecoveryStrategy
) {
    LOCATION_TRACKING(
        "Location Tracking",
        ErrorPriority.HIGH,
        RecoveryStrategy.IMMEDIATE_RETRY
    ),
    PLACE_DETECTION(
        "Place Detection",
        ErrorPriority.MEDIUM,
        RecoveryStrategy.DELAYED_RETRY
    ),
    DATA_VALIDATION(
        "Data Validation",
        ErrorPriority.HIGH,
        RecoveryStrategy.SANITIZE_AND_CONTINUE
    ),
    DATABASE(
        "Database",
        ErrorPriority.CRITICAL,
        RecoveryStrategy.EXPONENTIAL_BACKOFF
    ),
    NETWORK(
        "Network",
        ErrorPriority.MEDIUM,
        RecoveryStrategy.QUEUE_FOR_RETRY
    ),
    SYSTEM(
        "System",
        ErrorPriority.CRITICAL,
        RecoveryStrategy.RESET_AND_RESTART
    ),
    USER_INPUT(
        "User Input",
        ErrorPriority.LOW,
        RecoveryStrategy.VALIDATE_AND_PROMPT
    )
}

/**
 * Error priority levels
 */
enum class ErrorPriority {
    LOW,      // Non-critical errors that don't affect core functionality
    MEDIUM,   // Errors that affect some features but app remains usable
    HIGH,     // Errors that affect core functionality
    CRITICAL  // Errors that make the app unusable
}

/**
 * Recovery strategies for different error types
 */
enum class RecoveryStrategy {
    IMMEDIATE_RETRY,        // Retry immediately
    DELAYED_RETRY,         // Retry after a delay
    EXPONENTIAL_BACKOFF,   // Retry with increasing delays
    QUEUE_FOR_RETRY,       // Queue operation for later retry
    SANITIZE_AND_CONTINUE, // Clean data and continue
    RESET_AND_RESTART,     // Reset state and restart
    VALIDATE_AND_PROMPT,   // Validate input and prompt user
    FAIL_GRACEFULLY        // Accept failure and continue with degraded functionality
}

/**
 * Recovery actions that can be suggested to users or performed automatically
 */
enum class RecoveryAction(
    val displayName: String,
    val isUserAction: Boolean,
    val isAutomaticAction: Boolean
) {
    // Permission-related actions
    REQUEST_PERMISSIONS("Grant Permissions", true, false),
    OPEN_APP_SETTINGS("Open App Settings", true, false),
    EXPLAIN_FEATURE_NEED("Learn Why Permissions Are Needed", true, false),
    
    // Location service actions
    ENABLE_LOCATION_SERVICES("Enable Location Services", true, false),
    CHECK_GPS_SETTINGS("Check GPS Settings", true, false),
    IMPROVE_GPS_SIGNAL("Move to Open Area", true, false),
    WAIT_FOR_BETTER_SIGNAL("Wait for Better Signal", false, true),
    ADJUST_ACCURACY_SETTINGS("Adjust Accuracy Settings", true, false),
    
    // Service management actions
    RESTART_SERVICE("Restart Service", false, true),
    CLEAR_APP_CACHE("Clear App Cache", true, false),
    CONTACT_SUPPORT("Contact Support", true, false),
    
    // Place detection actions
    CONTINUE_TRACKING("Continue Tracking", true, false),
    MANUAL_PLACE_CREATION("Create Place Manually", true, false),
    ADJUST_DETECTION_SETTINGS("Adjust Detection Settings", true, false),
    MERGE_PLACES("Merge Similar Places", true, false),
    CREATE_NEW_PLACE("Create New Place", true, false),
    ADJUST_PLACE_BOUNDARIES("Adjust Place Boundaries", true, false),
    
    // Geofence actions
    RETRY_GEOFENCE_CREATION("Retry Geofence Setup", false, true),
    USE_MANUAL_TRACKING("Use Manual Tracking", true, false),
    ADJUST_GEOFENCE_SETTINGS("Adjust Geofence Settings", true, false),
    
    // Data validation actions
    SANITIZE_DATA("Clean Invalid Data", false, true),
    SKIP_INVALID_DATA("Skip Invalid Data", false, true),
    RESET_DATA_CACHE("Reset Data Cache", false, true),
    VALIDATE_INPUT("Check Input", true, false),
    APPLY_DEFAULT_VALUES("Use Default Values", false, true),
    SHOW_INPUT_HELP("Show Input Help", true, false),
    SANITIZE_INPUT("Clean Input Data", false, true),
    MANUAL_REVIEW("Review Manually", true, false),
    RETRY_WITH_VALIDATION("Retry with Validation", false, true),
    SHOW_VALIDATION_ERRORS("Show Validation Errors", true, false),
    
    // GPS and location actions
    IMPROVE_GPS_CONDITIONS("Improve GPS Conditions", true, false),
    VALIDATE_GPS_DATA("Validate GPS Data", false, true),
    
    // Visit and timestamp actions
    ADJUST_TIMESTAMP("Adjust Timestamp", true, false),
    SKIP_SHORT_VISITS("Skip Short Visits", false, true),
    FIX_TIMESTAMP_ORDER("Fix Timestamp Order", false, true),
    SPLIT_LONG_VISITS("Split Long Visits", false, true),
    
    // Data relationship actions
    MERGE_DUPLICATES("Merge Duplicate Data", false, true),
    FIX_DATA_RELATIONSHIPS("Fix Data Relationships", false, true),
    SORT_BY_TIMESTAMP("Sort by Timestamp", false, true),
    THROTTLE_DATA_COLLECTION("Throttle Data Collection", false, true),
    ADJUST_TO_VALID_RANGE("Adjust to Valid Range", false, true),
    SKIP_STALE_DATA("Skip Stale Data", false, true),
    REMOVE_INVALID_FIELD("Remove Invalid Field", false, true),
    OPTIMIZE_SETTINGS("Optimize Settings", false, true),
    REVIEW_CLASSIFICATION("Review Classification", true, false),
    VALIDATE_EXTERNAL_DATA("Validate External Data", false, true),
    ENHANCE_DATA_QUALITY("Enhance Data Quality", false, true),
    
    // Database actions
    RETRY_DATABASE_CONNECTION("Retry Connection", false, true),
    CLEAR_DATABASE_CACHE("Clear Database Cache", false, true),
    RESTART_APP("Restart App", true, false),
    CANCEL_OPERATION("Cancel Operation", true, false),
    OPTIMIZE_DATABASE("Optimize Database", false, true),
    RETRY_WITH_TIMEOUT("Retry with Longer Timeout", false, true),
    RETRY_TRANSACTION("Retry Transaction", false, true),
    FORCE_SYNC("Force Sync", false, true),
    BACKUP_DATA("Backup Data", false, true),
    REPAIR_DATABASE("Repair Database", false, true),
    RESTORE_FROM_BACKUP("Restore from Backup", true, false),
    
    // Network actions
    CHECK_INTERNET_CONNECTION("Check Internet Connection", true, false),
    ENABLE_OFFLINE_MODE("Enable Offline Mode", false, true),
    RETRY_WHEN_ONLINE("Retry When Online", false, true),
    RETRY_SYNC("Retry Sync", false, true),
    CHECK_SYNC_SETTINGS("Check Sync Settings", true, false),
    
    // System actions
    RESET_CONFIGURATION("Reset Configuration", false, true),
    RESTORE_DEFAULT_SETTINGS("Restore Default Settings", false, true),
    CLEAR_CACHE("Clear Cache", false, true),
    REDUCE_MEMORY_USAGE("Reduce Memory Usage", false, true),
    
    // Generic actions
    RETRY_OPERATION("Try Again", true, false),
    IGNORE_ERROR("Ignore Error", true, false),
    REPORT_BUG("Report Bug", true, false),
    
    // State management actions
    REINITIALIZE_STATE("Reinitialize Application State", false, true),
    
    // CRITICAL FIX: App-specific recovery actions for identified issues
    RESTART_LOCATION_SERVICE("Restart Location Service", false, true),
    RESET_STATE_MANAGER("Reset State Manager", false, true),
    REINITIALIZE_WORKMANAGER("Reinitialize WorkManager", false, true),
    CLEAR_RAPID_STATE_CHANGES("Clear Rapid State Changes", false, true)
}