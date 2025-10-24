package com.cosmiclaboratory.voyager.domain.validation

import com.cosmiclaboratory.voyager.domain.exception.RecoveryAction

/**
 * Represents the result of a validation operation
 * Provides detailed information about validation failures
 */
sealed class ValidationResult {
    
    /**
     * Combine with another validation result
     */
    fun combineWith(other: ValidationResult): ValidationResult {
        return when (this) {
            is Success -> other
            is Failure -> when (other) {
                is Success -> this
                is Failure -> Failure(errors + other.errors)
            }
        }
    }
    
    /**
     * Validation passed successfully
     */
    object Success : ValidationResult()
    
    /**
     * Validation failed with specific errors
     */
    data class Failure(
        val errors: List<ValidationError>
    ) : ValidationResult() {
        
        constructor(error: ValidationError) : this(listOf(error))
        
        /**
         * Get all errors of a specific severity
         */
        fun getErrorsBySeverity(severity: ValidationSeverity): List<ValidationError> {
            return errors.filter { it.severity == severity }
        }
        
        /**
         * Check if validation has errors of a specific severity or higher
         */
        fun hasErrorsWithSeverity(severity: ValidationSeverity): Boolean {
            return errors.any { it.severity.priority >= severity.priority }
        }
        
        /**
         * Get the highest severity error
         */
        fun getHighestSeverityError(): ValidationError? {
            return errors.maxByOrNull { it.severity.priority }
        }
        
    }
    
    /**
     * Check if validation was successful
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Check if validation failed
     */
    fun isFailure(): Boolean = this is Failure
    
    /**
     * Get errors if validation failed, empty list otherwise
     */
    fun getAllErrors(): List<ValidationError> {
        return when (this) {
            is Success -> emptyList()
            is Failure -> errors
        }
    }
    
    /**
     * Combine with another validation result
     */
    fun combine(other: ValidationResult): ValidationResult {
        return when {
            this is Success && other is Success -> Success
            this is Success && other is Failure -> other
            this is Failure && other is Success -> this
            this is Failure && other is Failure -> Failure(errors + other.errors)
            else -> this
        }
    }
}

/**
 * Represents a single validation error
 */
data class ValidationError(
    val field: String,
    val code: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
    val suggestedAction: RecoveryAction? = null,
    val context: Map<String, Any> = emptyMap()
) {
    
    /**
     * Create a user-friendly error message
     */
    fun getUserMessage(): String {
        return when (severity) {
            ValidationSeverity.INFO -> "Info: $message"
            ValidationSeverity.WARNING -> "Warning: $message"
            ValidationSeverity.ERROR -> "Error: $message"
            ValidationSeverity.CRITICAL -> "Critical Error: $message"
        }
    }
}

/**
 * Validation error severity levels
 */
enum class ValidationSeverity(val priority: Int) {
    INFO(1),
    WARNING(2), 
    ERROR(3),
    CRITICAL(4)
}

/**
 * Validation rule interface
 */
interface ValidationRule<T> {
    fun validate(value: T): ValidationResult
    val errorCode: String
    val errorMessage: String
}

/**
 * Validator interface for complex objects
 */
interface Validator<T> {
    fun validate(value: T): ValidationResult
}

/**
 * Utility functions for validation
 */
object ValidationUtils {
    
    /**
     * Validate multiple values and combine results
     */
    fun validateAll(vararg validations: () -> ValidationResult): ValidationResult {
        return validations.fold(ValidationResult.Success as ValidationResult) { acc, validation ->
            acc.combineWith(validation())
        }
    }
    
    /**
     * Validate a list of items
     */
    fun <T> validateList(
        items: List<T>,
        validator: Validator<T>,
        fieldPrefix: String = "item"
    ): ValidationResult {
        return items.foldIndexed(ValidationResult.Success as ValidationResult) { index, acc, item ->
            val result = validator.validate(item)
            when (result) {
                is ValidationResult.Success -> acc
                is ValidationResult.Failure -> {
                    val adjustedErrors = result.errors.map { error ->
                        error.copy(field = "$fieldPrefix[$index].${error.field}")
                    }
                    acc.combineWith(ValidationResult.Failure(adjustedErrors))
                }
            }
        }
    }
    
    /**
     * Create a validation result from a boolean condition
     */
    fun validateCondition(
        condition: Boolean,
        field: String,
        errorCode: String,
        errorMessage: String,
        severity: ValidationSeverity = ValidationSeverity.ERROR,
        suggestedAction: RecoveryAction? = null
    ): ValidationResult {
        return if (condition) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(
                ValidationError(
                    field = field,
                    code = errorCode,
                    message = errorMessage,
                    severity = severity,
                    suggestedAction = suggestedAction
                )
            )
        }
    }
}