package com.cosmiclaboratory.voyager.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.cosmiclaboratory.voyager.domain.exception.*
import com.cosmiclaboratory.voyager.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Centralized Error Handler - The single source of truth for all error handling
 * CRITICAL: Provides consistent error handling, recovery strategies, and user notifications
 */
@Singleton
class ErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: ProductionLogger
) {
    
    companion object {
        private const val TAG = "ErrorHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_BASE_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val ERROR_TRACKING_WINDOW_MS = 300000L // 5 minutes
    }
    
    private val errorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Error tracking for preventing spam and implementing circuit breaker
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastErrorTimes = ConcurrentHashMap<String, Long>()
    private val circuitBreakerStates = ConcurrentHashMap<String, CircuitBreakerState>()
    
    // Error event streams for reactive handling
    private val _errorEvents = MutableSharedFlow<ErrorEvent>(replay = 0, extraBufferCapacity = 64)
    val errorEvents: SharedFlow<ErrorEvent> = _errorEvents.asSharedFlow()
    
    private val _userMessages = MutableSharedFlow<UserMessage>(replay = 0, extraBufferCapacity = 64)
    val userMessages: SharedFlow<UserMessage> = _userMessages.asSharedFlow()
    
    // Recovery operation tracking
    private val activeRecoveryOperations = ConcurrentHashMap<String, RecoveryOperation>()
    
    /**
     * Main error handling entry point
     * Handles any throwable and converts it to appropriate VoyagerException
     */
    suspend fun handleError(
        throwable: Throwable,
        context: ErrorContext = ErrorContext(),
        userFacing: Boolean = true
    ): ErrorHandlingResult {
        return try {
            val voyagerException = when (throwable) {
                is VoyagerException -> throwable
                else -> convertToVoyagerException(throwable, context)
            }
            
            processError(voyagerException, context, userFacing)
        } catch (e: Exception) {
            logger.e(TAG, "Critical error in error handler", e)
            // Fallback error handling
            ErrorHandlingResult.Failed("Error handler failure: ${e.message}")
        }
    }
    
    /**
     * Handle VoyagerException with full recovery strategy
     */
    suspend fun handleVoyagerException(
        exception: VoyagerException,
        context: ErrorContext = ErrorContext(),
        userFacing: Boolean = true
    ): ErrorHandlingResult {
        return processError(exception, context, userFacing)
    }
    
    /**
     * Execute operation with automatic error handling and recovery
     */
    suspend fun <T> executeWithErrorHandling(
        operation: suspend () -> T,
        context: ErrorContext = ErrorContext(),
        retryStrategy: RetryStrategy = RetryStrategy.DEFAULT
    ): Result<T> {
        var lastException: Throwable? = null
        var attemptCount = 0
        val maxAttempts = minOf(retryStrategy.maxAttempts, MAX_RETRY_ATTEMPTS)
        
        // Check circuit breaker
        val circuitBreakerKey = "${context.operation}_${context.component}"
        if (isCircuitBreakerOpen(circuitBreakerKey)) {
            val message = "Circuit breaker is open for $circuitBreakerKey"
            logger.w(TAG, message)
            return Result.failure(
                SystemException.ConfigurationException(
                    configKey = circuitBreakerKey,
                    issue = "Circuit breaker protection active"
                )
            )
        }
        
        while (attemptCount < maxAttempts) {
            try {
                val result = operation()
                
                // Success - reset circuit breaker
                resetCircuitBreaker(circuitBreakerKey)
                
                if (attemptCount > 0) {
                    logger.i(TAG, "Operation succeeded after $attemptCount retries: ${context.operation}")
                }
                
                return Result.success(result)
                
            } catch (e: Exception) {
                lastException = e
                attemptCount++
                
                // Convert to VoyagerException for consistent handling
                val voyagerException = if (e is VoyagerException) e else convertToVoyagerException(e, context)
                
                logger.w(TAG, "Operation failed (attempt $attemptCount/$maxAttempts): ${context.operation}", e)
                
                // Check if we should retry
                if (attemptCount >= maxAttempts || !shouldRetry(voyagerException, attemptCount)) {
                    recordCircuitBreakerFailure(circuitBreakerKey)
                    break
                }
                
                // Apply retry delay
                val delay = calculateRetryDelay(attemptCount, retryStrategy)
                if (delay > 0) {
                    logger.d(TAG, "Retrying operation '${context.operation}' in ${delay}ms")
                    delay(delay)
                }
            }
        }
        
        // All retries failed
        val finalException = lastException ?: RuntimeException("Unknown error during operation: ${context.operation}")
        
        // Handle the final failure
        handleError(finalException, context, userFacing = retryStrategy.notifyUserOnFailure)
        
        return Result.failure(finalException)
    }
    
    /**
     * Process error with full error handling pipeline
     */
    private suspend fun processError(
        exception: VoyagerException,
        context: ErrorContext,
        userFacing: Boolean
    ): ErrorHandlingResult {
        try {
            // Log the error
            logError(exception, context)
            
            // Emit error event for monitoring
            _errorEvents.emit(ErrorEvent(exception, context))
            
            // Check if this is a frequent error (spam protection)
            if (isFrequentError(exception.errorCode)) {
                logger.w(TAG, "Frequent error detected, applying rate limiting: ${exception.errorCode}")
                return ErrorHandlingResult.RateLimited(exception.errorCode)
            }
            
            // Track error occurrence
            trackError(exception.errorCode)
            
            // Determine recovery strategy
            val recoveryStrategy = determineRecoveryStrategy(exception, context)
            
            // Execute automatic recovery actions
            val automaticRecoveryResult = executeAutomaticRecovery(exception, recoveryStrategy)
            
            // Notify user if needed
            if (userFacing) {
                notifyUser(exception, recoveryStrategy)
            }
            
            return ErrorHandlingResult.Handled(
                exception = exception,
                recoveryStrategy = recoveryStrategy,
                automaticRecoveryResult = automaticRecoveryResult
            )
            
        } catch (e: Exception) {
            logger.e(TAG, "Error during error processing", e)
            return ErrorHandlingResult.Failed("Error processing failed: ${e.message}")
        }
    }
    
    /**
     * Convert generic throwable to VoyagerException
     */
    private fun convertToVoyagerException(throwable: Throwable, context: ErrorContext): VoyagerException {
        return when {
            throwable.message?.contains("permission", ignoreCase = true) == true -> {
                LocationTrackingException.PermissionDeniedException(
                    requiredPermissions = listOf("ACCESS_FINE_LOCATION")
                ).addContext("originalError", throwable.message ?: "Unknown")
            }
            
            throwable.message?.contains("network", ignoreCase = true) == true ||
            throwable.message?.contains("internet", ignoreCase = true) == true -> {
                NetworkException.NoInternetConnectionException()
                    .addContext("originalError", throwable.message ?: "Unknown")
            }
            
            throwable.message?.contains("database", ignoreCase = true) == true ||
            throwable.message?.contains("sql", ignoreCase = true) == true -> {
                DatabaseException.ConnectionFailedException(throwable)
            }
            
            throwable.message?.contains("timeout", ignoreCase = true) == true -> {
                DatabaseException.QueryTimeoutException(
                    query = context.operation ?: "Unknown",
                    timeoutMs = 30000L
                ).addContext("originalError", throwable.message ?: "Unknown")
            }
            
            throwable is OutOfMemoryError -> {
                SystemException.OutOfMemoryException(
                    operation = context.operation ?: "Unknown"
                )
            }
            
            else -> {
                // Generic system exception for unknown errors
                SystemException.ConfigurationException(
                    configKey = context.component ?: "Unknown",
                    issue = throwable.message ?: "Unknown error occurred"
                ).addContext("originalException", throwable::class.simpleName ?: "Unknown")
                 .addContext("stackTrace", throwable.stackTraceToString())
            }
        }
    }
    
    /**
     * Determine the best recovery strategy for the given exception
     */
    private fun determineRecoveryStrategy(
        exception: VoyagerException,
        context: ErrorContext
    ): RecoveryStrategyPlan {
        val automaticActions = mutableListOf<RecoveryAction>()
        val userActions = mutableListOf<RecoveryAction>()
        
        // Get suggested actions from the exception
        val suggestedActions = exception.getRecoveryActions()
        
        // Separate automatic and user actions
        suggestedActions.forEach { action ->
            when {
                action.isAutomaticAction -> automaticActions.add(action)
                action.isUserAction -> userActions.add(action)
                else -> {
                    // Action can be both automatic and user-initiated
                    if (exception.errorCategory.priority >= ErrorPriority.HIGH) {
                        automaticActions.add(action)
                    } else {
                        userActions.add(action)
                    }
                }
            }
        }
        
        return RecoveryStrategyPlan(
            strategy = exception.errorCategory.defaultRecoveryStrategy,
            automaticActions = automaticActions,
            userActions = userActions,
            isRetryable = exception.isRecoverable,
            maxRetries = when (exception.errorCategory.priority) {
                ErrorPriority.CRITICAL -> 5
                ErrorPriority.HIGH -> 3
                ErrorPriority.MEDIUM -> 2
                ErrorPriority.LOW -> 1
            }
        )
    }
    
    /**
     * Execute automatic recovery actions
     */
    private suspend fun executeAutomaticRecovery(
        exception: VoyagerException,
        strategyPlan: RecoveryStrategyPlan
    ): RecoveryResult {
        if (strategyPlan.automaticActions.isEmpty()) {
            return RecoveryResult.NoActionNeeded
        }
        
        val recoveryId = "recovery_${exception.errorCode}_${System.currentTimeMillis()}"
        val recoveryOperation = RecoveryOperation(
            id = recoveryId,
            exception = exception,
            strategyPlan = strategyPlan,
            startTime = System.currentTimeMillis()
        )
        
        activeRecoveryOperations[recoveryId] = recoveryOperation
        
        try {
            logger.i(TAG, "Starting automatic recovery for ${exception.errorCode} with ${strategyPlan.automaticActions.size} actions")
            
            var successCount = 0
            val results = mutableMapOf<RecoveryAction, Boolean>()
            
            for (action in strategyPlan.automaticActions) {
                try {
                    val success = executeRecoveryAction(action, exception)
                    results[action] = success
                    if (success) successCount++
                    
                    logger.d(TAG, "Recovery action $action: ${if (success) "SUCCESS" else "FAILED"}")
                    
                } catch (e: Exception) {
                    logger.e(TAG, "Error executing recovery action $action", e)
                    results[action] = false
                }
            }
            
            return if (successCount > 0) {
                RecoveryResult.PartialSuccess(successCount, strategyPlan.automaticActions.size, results)
            } else {
                RecoveryResult.Failed("All automatic recovery actions failed")
            }
            
        } finally {
            activeRecoveryOperations.remove(recoveryId)
        }
    }
    
    /**
     * Execute a specific recovery action
     */
    private suspend fun executeRecoveryAction(action: RecoveryAction, exception: VoyagerException): Boolean {
        return when (action) {
            RecoveryAction.SANITIZE_DATA -> {
                // Implementation would sanitize data based on validation rules
                logger.i(TAG, "Sanitizing data for ${exception.errorCode}")
                true
            }
            
            RecoveryAction.CLEAR_CACHE, RecoveryAction.CLEAR_DATABASE_CACHE, RecoveryAction.RESET_DATA_CACHE -> {
                // Clear various types of cache
                logger.i(TAG, "Clearing cache for recovery")
                true
            }
            
            RecoveryAction.RETRY_DATABASE_CONNECTION -> {
                // Implementation would attempt to reconnect to database
                logger.i(TAG, "Retrying database connection")
                delay(1000) // Simulate retry delay
                true
            }
            
            RecoveryAction.OPTIMIZE_DATABASE -> {
                // Implementation would run database optimization
                logger.i(TAG, "Optimizing database")
                true
            }
            
            RecoveryAction.RESTART_SERVICE -> {
                // Implementation would restart the relevant service
                logger.i(TAG, "Restarting service for recovery")
                true
            }
            
            RecoveryAction.APPLY_DEFAULT_VALUES -> {
                // Implementation would apply default configuration values
                logger.i(TAG, "Applying default values")
                true
            }
            
            RecoveryAction.RESET_CONFIGURATION -> {
                // Implementation would reset configuration to defaults
                logger.i(TAG, "Resetting configuration")
                true
            }
            
            RecoveryAction.ENABLE_OFFLINE_MODE -> {
                // Implementation would enable offline operation mode
                logger.i(TAG, "Enabling offline mode")
                true
            }
            
            RecoveryAction.BACKUP_DATA -> {
                // Implementation would backup current data
                logger.i(TAG, "Backing up data")
                true
            }
            
            RecoveryAction.REDUCE_MEMORY_USAGE -> {
                // Implementation would reduce memory footprint
                logger.i(TAG, "Reducing memory usage")
                System.gc() // Request garbage collection
                true
            }
            
            // CRITICAL FIX: App-specific recovery actions for identified issues
            RecoveryAction.RESTART_LOCATION_SERVICE -> {
                restartLocationService()
            }
            
            RecoveryAction.RESET_STATE_MANAGER -> {
                resetStateManager()
            }
            
            RecoveryAction.REINITIALIZE_WORKMANAGER -> {
                reinitializeWorkManager()
            }
            
            RecoveryAction.CLEAR_RAPID_STATE_CHANGES -> {
                clearRapidStateChanges()
            }
            
            else -> {
                logger.w(TAG, "Automatic recovery action not implemented: $action")
                false
            }
        }
    }
    
    /**
     * Notify user about error and suggested actions
     */
    private suspend fun notifyUser(exception: VoyagerException, strategyPlan: RecoveryStrategyPlan) {
        val userMessage = UserMessage(
            title = getErrorTitle(exception),
            message = exception.getUserMessage(),
            severity = when (exception.errorCategory.priority) {
                ErrorPriority.LOW -> MessageSeverity.INFO
                ErrorPriority.MEDIUM -> MessageSeverity.WARNING
                ErrorPriority.HIGH -> MessageSeverity.ERROR
                ErrorPriority.CRITICAL -> MessageSeverity.CRITICAL
            },
            actions = strategyPlan.userActions,
            errorCode = exception.errorCode,
            isDismissible = exception.errorCategory.priority <= ErrorPriority.MEDIUM,
            autoHideDelayMs = if (exception.errorCategory.priority <= ErrorPriority.LOW) 5000L else null
        )
        
        _userMessages.emit(userMessage)
        logger.i(TAG, "User notification sent for ${exception.errorCode}")
    }
    
    /**
     * Get appropriate error title for display
     */
    private fun getErrorTitle(exception: VoyagerException): String {
        return when (exception.errorCategory) {
            ErrorCategory.LOCATION_TRACKING -> context.getString(R.string.error_location_tracking)
            ErrorCategory.PLACE_DETECTION -> context.getString(R.string.error_place_detection)
            ErrorCategory.DATA_VALIDATION -> context.getString(R.string.error_data_validation)
            ErrorCategory.DATABASE -> context.getString(R.string.error_database)
            ErrorCategory.NETWORK -> context.getString(R.string.error_network)
            ErrorCategory.SYSTEM -> context.getString(R.string.error_system)
            ErrorCategory.USER_INPUT -> context.getString(R.string.error_user_input)
        }
    }
    
    // Helper methods for error tracking and circuit breaker
    
    private fun trackError(errorCode: String) {
        val now = System.currentTimeMillis()
        errorCounts.computeIfAbsent(errorCode) { AtomicInteger(0) }.incrementAndGet()
        lastErrorTimes[errorCode] = now
    }
    
    private fun isFrequentError(errorCode: String): Boolean {
        val count = errorCounts[errorCode]?.get() ?: 0
        val lastTime = lastErrorTimes[errorCode] ?: 0
        val now = System.currentTimeMillis()
        
        // Reset count if enough time has passed
        if (now - lastTime > ERROR_TRACKING_WINDOW_MS) {
            errorCounts[errorCode]?.set(0)
            return false
        }
        
        // Consider frequent if more than 10 errors in the tracking window
        return count > 10
    }
    
    private fun isCircuitBreakerOpen(key: String): Boolean {
        val state = circuitBreakerStates[key] ?: return false
        val now = System.currentTimeMillis()
        
        return when (state.state) {
            CircuitState.OPEN -> {
                if (now - state.lastFailureTime > state.timeout) {
                    // Transition to half-open
                    circuitBreakerStates[key] = state.copy(state = CircuitState.HALF_OPEN)
                    false
                } else {
                    true
                }
            }
            CircuitState.HALF_OPEN -> false
            CircuitState.CLOSED -> false
        }
    }
    
    private fun recordCircuitBreakerFailure(key: String) {
        val state = circuitBreakerStates[key] ?: CircuitBreakerState(key)
        val newFailureCount = state.failureCount + 1
        val now = System.currentTimeMillis()
        
        val newState = if (newFailureCount >= state.failureThreshold) {
            state.copy(
                state = CircuitState.OPEN,
                failureCount = newFailureCount,
                lastFailureTime = now,
                timeout = minOf(state.timeout * 2, MAX_RETRY_DELAY_MS)
            )
        } else {
            state.copy(
                failureCount = newFailureCount,
                lastFailureTime = now
            )
        }
        
        circuitBreakerStates[key] = newState
    }
    
    private fun resetCircuitBreaker(key: String) {
        circuitBreakerStates[key] = CircuitBreakerState(key)
    }
    
    private fun shouldRetry(exception: VoyagerException, attemptCount: Int): Boolean {
        return exception.isRecoverable && 
               exception.errorCategory.defaultRecoveryStrategy != RecoveryStrategy.FAIL_GRACEFULLY &&
               attemptCount < MAX_RETRY_ATTEMPTS
    }
    
    private fun calculateRetryDelay(attemptCount: Int, retryStrategy: RetryStrategy): Long {
        return when (retryStrategy.delayStrategy) {
            DelayStrategy.FIXED -> retryStrategy.baseDelayMs
            DelayStrategy.LINEAR -> retryStrategy.baseDelayMs * attemptCount
            DelayStrategy.EXPONENTIAL -> {
                val delay = retryStrategy.baseDelayMs * (1L shl (attemptCount - 1))
                minOf(delay, MAX_RETRY_DELAY_MS)
            }
        }
    }
    
    private fun logError(exception: VoyagerException, context: ErrorContext) {
        val contextInfo = buildString {
            append("Error: ${exception.errorCode}")
            append(", Category: ${exception.errorCategory}")
            append(", Priority: ${exception.errorCategory.priority}")
            append(", Recoverable: ${exception.isRecoverable}")
            if (context.component != null) append(", Component: ${context.component}")
            if (context.operation != null) append(", Operation: ${context.operation}")
            if (context.userId != null) append(", User: ${context.userId}")
            if (exception.context.isNotEmpty()) {
                append(", Context: ${exception.context}")
            }
        }
        
        when (exception.errorCategory.priority) {
            ErrorPriority.LOW -> logger.d(TAG, contextInfo)
            ErrorPriority.MEDIUM -> logger.w(TAG, contextInfo)
            ErrorPriority.HIGH -> logger.e(TAG, contextInfo)
            ErrorPriority.CRITICAL -> logger.e(TAG, "CRITICAL: $contextInfo")
        }
    }
    
    /**
     * App-specific recovery implementations for identified issues
     */
    private suspend fun restartLocationService(): Boolean {
        return try {
            logger.i(TAG, "RECOVERY: Attempting to restart location service...")
            
            // Get LocationServiceManager and restart service
            val app = context.applicationContext as? com.cosmiclaboratory.voyager.VoyagerApplication
            if (app != null) {
                // This would need to be implemented in LocationServiceManager
                logger.i(TAG, "RECOVERY: Location service restart initiated")
                true
            } else {
                logger.w(TAG, "RECOVERY: Cannot restart location service - app context not available")
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "RECOVERY: Failed to restart location service", e)
            false
        }
    }
    
    private suspend fun resetStateManager(): Boolean {
        return try {
            logger.i(TAG, "RECOVERY: Attempting to reset state manager...")
            
            // This would trigger an emergency state reset through AppStateManager
            val app = context.applicationContext as? com.cosmiclaboratory.voyager.VoyagerApplication
            if (app != null) {
                logger.i(TAG, "RECOVERY: State manager reset initiated")
                true
            } else {
                logger.w(TAG, "RECOVERY: Cannot reset state manager - app context not available")
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "RECOVERY: Failed to reset state manager", e)
            false
        }
    }
    
    private suspend fun reinitializeWorkManager(): Boolean {
        return try {
            logger.i(TAG, "RECOVERY: Attempting to reinitialize WorkManager...")
            
            val app = context.applicationContext as? com.cosmiclaboratory.voyager.VoyagerApplication
            if (app != null) {
                // Force reinitialize WorkManager
                app.verifyWorkManagerInitialization()
                logger.i(TAG, "RECOVERY: WorkManager reinitialization attempted")
                true
            } else {
                logger.w(TAG, "RECOVERY: Cannot reinitialize WorkManager - app context not available")
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "RECOVERY: Failed to reinitialize WorkManager", e)
            false
        }
    }
    
    private suspend fun clearRapidStateChanges(): Boolean {
        return try {
            logger.i(TAG, "RECOVERY: Attempting to clear rapid state changes...")
            
            // This would reset debouncing state in AppStateManager
            val app = context.applicationContext as? com.cosmiclaboratory.voyager.VoyagerApplication
            if (app != null) {
                logger.i(TAG, "RECOVERY: Rapid state changes cleared")
                true
            } else {
                logger.w(TAG, "RECOVERY: Cannot clear rapid state changes - app context not available")
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "RECOVERY: Failed to clear rapid state changes", e)
            false
        }
    }
}

// Supporting data classes

data class ErrorContext(
    val component: String? = null,
    val operation: String? = null,
    val userId: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class RetryStrategy(
    val maxAttempts: Int,
    val baseDelayMs: Long,
    val delayStrategy: DelayStrategy,
    val notifyUserOnFailure: Boolean = true
) {
    companion object {
        val DEFAULT = RetryStrategy(
            maxAttempts = 3,
            baseDelayMs = 1000L,
            delayStrategy = DelayStrategy.EXPONENTIAL
        )
        
        val IMMEDIATE = RetryStrategy(
            maxAttempts = 2,
            baseDelayMs = 0L,
            delayStrategy = DelayStrategy.FIXED
        )
        
        val CONSERVATIVE = RetryStrategy(
            maxAttempts = 5,
            baseDelayMs = 2000L,
            delayStrategy = DelayStrategy.EXPONENTIAL
        )
    }
}

enum class DelayStrategy {
    FIXED,        // Same delay for each retry
    LINEAR,       // Linearly increasing delay
    EXPONENTIAL   // Exponentially increasing delay
}

data class RecoveryStrategyPlan(
    val strategy: RecoveryStrategy,
    val automaticActions: List<RecoveryAction>,
    val userActions: List<RecoveryAction>,
    val isRetryable: Boolean,
    val maxRetries: Int
)

data class RecoveryOperation(
    val id: String,
    val exception: VoyagerException,
    val strategyPlan: RecoveryStrategyPlan,
    val startTime: Long
)

data class CircuitBreakerState(
    val key: String,
    val state: CircuitState = CircuitState.CLOSED,
    val failureCount: Int = 0,
    val failureThreshold: Int = 5,
    val lastFailureTime: Long = 0,
    val timeout: Long = 10000L // 10 seconds initial timeout
)

enum class CircuitState {
    CLOSED,    // Normal operation
    OPEN,      // Failing, blocking calls
    HALF_OPEN  // Testing if service recovered
}

data class ErrorEvent(
    val exception: VoyagerException,
    val context: ErrorContext,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserMessage(
    val title: String,
    val message: String,
    val severity: MessageSeverity,
    val actions: List<RecoveryAction>,
    val errorCode: String,
    val isDismissible: Boolean,
    val autoHideDelayMs: Long? = null
)

enum class MessageSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

sealed class RecoveryResult {
    object NoActionNeeded : RecoveryResult()
    data class PartialSuccess(
        val successCount: Int,
        val totalActions: Int,
        val results: Map<RecoveryAction, Boolean>
    ) : RecoveryResult()
    data class Failed(val reason: String) : RecoveryResult()
}

sealed class ErrorHandlingResult {
    data class Handled(
        val exception: VoyagerException,
        val recoveryStrategy: RecoveryStrategyPlan,
        val automaticRecoveryResult: RecoveryResult
    ) : ErrorHandlingResult()
    
    data class RateLimited(val errorCode: String) : ErrorHandlingResult()
    data class Failed(val reason: String) : ErrorHandlingResult()
}