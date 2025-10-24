package com.cosmiclaboratory.voyager.domain.validation

import com.cosmiclaboratory.voyager.domain.exception.*
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized validation service that integrates all validators with error handling
 * Provides a unified interface for validation across the application
 */
@Singleton
class ValidationService @Inject constructor(
    private val locationValidator: LocationValidator,
    private val placeValidator: PlaceValidator,
    private val businessRuleEngine: BusinessRuleEngine,
    private val errorHandler: ErrorHandler,
    private val logger: ProductionLogger
) {
    
    companion object {
        private const val TAG = "ValidationService"
    }
    
    /**
     * Validate location with error handling integration
     */
    suspend fun validateLocation(
        location: Location,
        context: ErrorContext = ErrorContext(
            operation = "validateLocation",
            component = "ValidationService"
        )
    ): Result<Location> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                val result = locationValidator.validate(location)
                handleValidationResult(result, "location", location)
                location
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "accuracy" to location.accuracy.toString()
                )
            )
        )
    }
    
    /**
     * Validate place with error handling integration
     */
    suspend fun validatePlace(
        place: Place,
        existingPlaces: List<Place> = emptyList(),
        context: ErrorContext = ErrorContext(
            operation = "validatePlace",
            component = "ValidationService"
        )
    ): Result<Place> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                // Validate place itself
                val placeResult = placeValidator.validate(place)
                handleValidationResult(placeResult, "place", place)
                
                // Validate business rules with existing places
                if (existingPlaces.isNotEmpty()) {
                    val proximityResult = businessRuleEngine.validatePlaceProximity(place, existingPlaces)
                    handleValidationResult(proximityResult, "placeProximity", place)
                }
                
                place
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "placeName" to place.name,
                    "category" to place.category.toString(),
                    "latitude" to place.latitude.toString(),
                    "longitude" to place.longitude.toString(),
                    "existingPlacesCount" to existingPlaces.size.toString()
                )
            )
        )
    }
    
    /**
     * Validate visit with error handling integration
     */
    suspend fun validateVisit(
        visit: Visit,
        place: Place? = null,
        context: ErrorContext = ErrorContext(
            operation = "validateVisit",
            component = "ValidationService"
        )
    ): Result<Visit> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                val result = businessRuleEngine.validateVisit(visit, place)
                handleValidationResult(result, "visit", visit)
                visit
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "visitId" to visit.id.toString(),
                    "placeId" to visit.placeId.toString(),
                    "entryTime" to visit.entryTime.toString(),
                    "exitTime" to (visit.exitTime?.toString() ?: "ongoing"),
                    "hasPlace" to (place != null).toString()
                )
            )
        )
    }
    
    /**
     * Validate location sequence with error handling integration
     */
    suspend fun validateLocationSequence(
        locations: List<Location>,
        context: ErrorContext = ErrorContext(
            operation = "validateLocationSequence",
            component = "ValidationService"
        )
    ): Result<List<Location>> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                // Validate individual locations
                locations.forEach { location ->
                    val result = locationValidator.validate(location)
                    handleValidationResult(result, "locationInSequence", location)
                }
                
                // Validate sequence business rules
                val sequenceResult = businessRuleEngine.validateLocationSequence(locations)
                handleValidationResult(sequenceResult, "locationSequence", locations)
                
                locations
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "locationCount" to locations.size.toString(),
                    "timeSpan" to if (locations.isNotEmpty()) {
                        "${locations.minOf { it.timestamp }} to ${locations.maxOf { it.timestamp }}"
                    } else "empty"
                )
            )
        )
    }
    
    /**
     * Validate user preferences with error handling integration
     */
    suspend fun validateUserPreferences(
        preferences: UserPreferences,
        context: ErrorContext = ErrorContext(
            operation = "validateUserPreferences", 
            component = "ValidationService"
        )
    ): Result<UserPreferences> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                val result = businessRuleEngine.validateUserPreferences(preferences)
                handleValidationResult(result, "userPreferences", preferences)
                preferences
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "trackingAccuracy" to preferences.trackingAccuracyMode.toString(),
                    "placeDetectionEnabled" to preferences.enablePlaceDetection.toString(),
                    "updateInterval" to preferences.minTimeBetweenUpdatesSeconds.toString()
                )
            )
        )
    }
    
    /**
     * Validate and sanitize input data
     */
    suspend fun validateAndSanitize(
        data: Any,
        context: ErrorContext = ErrorContext(
            operation = "validateAndSanitize",
            component = "ValidationService"
        )
    ): Result<Any> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                when (data) {
                    is Location -> validateLocation(data, context).getOrThrow()
                    is Place -> validatePlace(data, context = context).getOrThrow()
                    is Visit -> validateVisit(data, context = context).getOrThrow()
                    is UserPreferences -> validateUserPreferences(data, context).getOrThrow()
                    is List<*> -> {
                        when {
                            data.all { it is Location } -> {
                                @Suppress("UNCHECKED_CAST")
                                validateLocationSequence(data as List<Location>, context).getOrThrow()
                            }
                            else -> data // Return as-is for unsupported list types
                        }
                    }
                    else -> data // Return as-is for unsupported types
                }
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "dataType" to data::class.simpleName.toString()
                )
            )
        )
    }
    
    /**
     * Batch validation for multiple items
     */
    suspend fun <T> validateBatch(
        items: List<T>,
        validator: suspend (T) -> Result<T>,
        maxFailures: Int = Int.MAX_VALUE,
        context: ErrorContext = ErrorContext(
            operation = "validateBatch",
            component = "ValidationService"
        )
    ): Result<List<T>> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                val validatedItems = mutableListOf<T>()
                val failures = mutableListOf<Exception>()
                
                for ((index, item) in items.withIndex()) {
                    try {
                        val validatedItem = validator(item).getOrThrow()
                        validatedItems.add(validatedItem)
                    } catch (e: Exception) {
                        failures.add(e)
                        logger.w(TAG, "Validation failed for item $index: ${e.message}")
                        
                        if (failures.size >= maxFailures) {
                            throw DataValidationException.BatchValidationException(
                                "Batch validation failed: ${failures.size} failures (max: $maxFailures)",
                                failures,
                                RecoveryAction.SKIP_INVALID_DATA
                            )
                        }
                    }
                }
                
                if (failures.isNotEmpty()) {
                    logger.w(TAG, "Batch validation completed with ${failures.size} failures out of ${items.size} items")
                }
                
                validatedItems
            },
            context = context.copy(
                metadata = context.metadata + mapOf(
                    "batchSize" to items.size.toString(),
                    "maxFailures" to maxFailures.toString()
                )
            )
        )
    }
    
    /**
     * Create validation summary for reporting
     */
    fun createValidationSummary(results: List<ValidationResult>): ValidationSummary {
        val allErrors = results.flatMap { it.getAllErrors() }
        
        return ValidationSummary(
            totalValidations = results.size,
            successful = results.count { it.isSuccess() },
            failed = results.count { it.isFailure() },
            errorsBySeverity = mapOf(
                ValidationSeverity.INFO to allErrors.count { it.severity == ValidationSeverity.INFO },
                ValidationSeverity.WARNING to allErrors.count { it.severity == ValidationSeverity.WARNING },
                ValidationSeverity.ERROR to allErrors.count { it.severity == ValidationSeverity.ERROR },
                ValidationSeverity.CRITICAL to allErrors.count { it.severity == ValidationSeverity.CRITICAL }
            ),
            commonErrors = allErrors.groupBy { it.code }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .toMap()
        )
    }
    
    /**
     * Handle validation result and convert to appropriate exceptions
     */
    private fun handleValidationResult(result: ValidationResult, context: String, data: Any) {
        when (result) {
            is ValidationResult.Success -> {
                logger.d(TAG, "Validation successful for $context")
            }
            is ValidationResult.Failure -> {
                val criticalErrors = result.getErrorsBySeverity(ValidationSeverity.CRITICAL)
                val errors = result.getErrorsBySeverity(ValidationSeverity.ERROR)
                
                // Throw exception for critical and error-level validation failures
                if (criticalErrors.isNotEmpty() || errors.isNotEmpty()) {
                    val primaryError = result.getHighestSeverityError()!!
                    
                    val exception = when (primaryError.code) {
                        "INVALID_LATITUDE", "INVALID_LONGITUDE", "NULL_ISLAND_COORDINATES",
                        "INVALID_ACCURACY", "FUTURE_TIMESTAMP", "NEGATIVE_SPEED",
                        "INVALID_BEARING" -> DataValidationException.LocationValidationException(
                            primaryError.message,
                            recoveryAction = primaryError.suggestedAction ?: RecoveryAction.SKIP_INVALID_DATA
                        )
                        
                        "INVALID_NAME_LENGTH", "BLANK_NAME", "INVALID_RADIUS", 
                        "INSUFFICIENT_PLACE_DATA" -> DataValidationException.PlaceValidationException(
                            primaryError.message,
                            recoveryAction = primaryError.suggestedAction ?: RecoveryAction.SKIP_INVALID_DATA
                        )
                        
                        "VISIT_TOO_SHORT", "VISIT_TOO_LONG", "INVALID_VISIT_SEQUENCE",
                        "FUTURE_VISIT" -> DataValidationException.VisitValidationException(
                            primaryError.message,
                            recoveryAction = primaryError.suggestedAction ?: RecoveryAction.SKIP_INVALID_DATA
                        )
                        
                        else -> DataValidationException.GeneralValidationException(
                            primaryError.message,
                            recoveryAction = primaryError.suggestedAction ?: RecoveryAction.SKIP_INVALID_DATA
                        )
                    }
                    
                    logger.e(TAG, "Validation failed for $context: ${primaryError.message}")
                    throw exception
                }
                
                // Log warnings but don't throw
                val warnings = result.getErrorsBySeverity(ValidationSeverity.WARNING)
                warnings.forEach { warning ->
                    logger.w(TAG, "Validation warning for $context: ${warning.message}")
                }
                
                // Log info messages
                val infoMessages = result.getErrorsBySeverity(ValidationSeverity.INFO)
                infoMessages.forEach { info ->
                    logger.i(TAG, "Validation info for $context: ${info.message}")
                }
            }
        }
    }
}

/**
 * Summary of validation results for reporting and monitoring
 */
data class ValidationSummary(
    val totalValidations: Int,
    val successful: Int,
    val failed: Int,
    val errorsBySeverity: Map<ValidationSeverity, Int>,
    val commonErrors: Map<String, Int>
) {
    val successRate: Double = if (totalValidations > 0) successful.toDouble() / totalValidations else 0.0
    val hasIssues: Boolean = failed > 0
    val hasCriticalIssues: Boolean = (errorsBySeverity[ValidationSeverity.CRITICAL] ?: 0) > 0
}