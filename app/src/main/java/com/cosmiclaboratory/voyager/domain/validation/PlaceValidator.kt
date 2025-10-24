package com.cosmiclaboratory.voyager.domain.validation

import com.cosmiclaboratory.voyager.domain.exception.RecoveryAction
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive validator for Place domain objects
 * Validates place names, coordinates, categories, and business rules
 */
@Singleton
class PlaceValidator @Inject constructor(
    private val locationValidator: LocationValidator
) : Validator<Place> {
    
    companion object {
        // Validation constants
        private const val MIN_PLACE_NAME_LENGTH = 1
        private const val MAX_PLACE_NAME_LENGTH = 200
        private const val MIN_PLACE_RADIUS = 1.0
        private const val MAX_PLACE_RADIUS = 5000.0 // 5km
        private const val MIN_ADDRESS_LENGTH = 5
        private const val MAX_ADDRESS_LENGTH = 500
        private const val MAX_GOOGLE_PLACE_ID_LENGTH = 100
        
        // Suspicious patterns
        private val SUSPICIOUS_NAME_PATTERNS = listOf(
            "test", "temp", "temporary", "delete", "remove", "spam", "fake"
        )
    }
    
    override fun validate(value: Place): ValidationResult {
        return ValidationUtils.validateAll(
            { validateName(value) },
            { validateCoordinates(value) },
            { validateRadius(value) },
            { validateCategory(value) },
            { validateAddress(value) },
            { validateGooglePlaceId(value) },
            { validateBusinessRules(value) }
        )
    }
    
    /**
     * Validate place name
     */
    private fun validateName(place: Place): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Name length validation
        results.add(
            ValidationUtils.validateCondition(
                condition = place.name.length in MIN_PLACE_NAME_LENGTH..MAX_PLACE_NAME_LENGTH,
                field = "name",
                errorCode = "INVALID_NAME_LENGTH",
                errorMessage = "Place name length must be between $MIN_PLACE_NAME_LENGTH and $MAX_PLACE_NAME_LENGTH characters, got ${place.name.length}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SANITIZE_INPUT
            )
        )
        
        // Name content validation
        results.add(
            ValidationUtils.validateCondition(
                condition = place.name.isNotBlank(),
                field = "name",
                errorCode = "BLANK_NAME",
                errorMessage = "Place name cannot be blank or empty",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        // Check for suspicious patterns
        val suspiciousPattern = SUSPICIOUS_NAME_PATTERNS.find { 
            place.name.lowercase().contains(it.lowercase()) 
        }
        if (suspiciousPattern != null) {
            results.add(
                ValidationResult.Failure(
                    ValidationError(
                        field = "name",
                        code = "SUSPICIOUS_NAME",
                        message = "Place name contains suspicious pattern: '$suspiciousPattern'",
                        severity = ValidationSeverity.WARNING,
                        suggestedAction = RecoveryAction.MANUAL_REVIEW
                    )
                )
            )
        }
        
        // Check for excessive special characters
        val specialCharCount = place.name.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        val specialCharRatio = specialCharCount.toDouble() / place.name.length
        results.add(
            ValidationUtils.validateCondition(
                condition = specialCharRatio <= 0.3, // Max 30% special characters
                field = "name",
                errorCode = "EXCESSIVE_SPECIAL_CHARS",
                errorMessage = "Place name has too many special characters (${(specialCharRatio * 100).toInt()}%)",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.SANITIZE_INPUT
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate place coordinates using LocationValidator
     */
    private fun validateCoordinates(place: Place): ValidationResult {
        // Create a temporary location object for coordinate validation
        val tempLocation = com.cosmiclaboratory.voyager.domain.model.Location(
            latitude = place.latitude,
            longitude = place.longitude,
            timestamp = java.time.LocalDateTime.now(),
            accuracy = 10.0f // Use reasonable accuracy for validation
        )
        
        val locationResult = locationValidator.validate(tempLocation)
        
        // Convert location validation errors to place context
        return when (locationResult) {
            is ValidationResult.Success -> ValidationResult.Success
            is ValidationResult.Failure -> {
                val placeErrors = locationResult.errors.map { error ->
                    error.copy(
                        field = "coordinates.${error.field}",
                        message = "Place ${error.message.lowercase()}"
                    )
                }
                ValidationResult.Failure(placeErrors)
            }
        }
    }
    
    /**
     * Validate place radius
     */
    private fun validateRadius(place: Place): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Basic radius validation
        results.add(
            ValidationUtils.validateCondition(
                condition = place.radius in MIN_PLACE_RADIUS..MAX_PLACE_RADIUS,
                field = "radius",
                errorCode = "INVALID_RADIUS",
                errorMessage = "Place radius must be between $MIN_PLACE_RADIUS and $MAX_PLACE_RADIUS meters, got ${place.radius}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        // Warn about very large radius
        results.add(
            ValidationUtils.validateCondition(
                condition = place.radius <= 1000.0, // 1km
                field = "radius",
                errorCode = "LARGE_RADIUS",
                errorMessage = "Place radius is very large: ${place.radius}m - may affect detection accuracy",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.OPTIMIZE_SETTINGS
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate place category
     */
    private fun validateCategory(place: Place): ValidationResult {
        // Category is an enum, so basic validation is handled by type system
        // We can add business rule validation here
        
        val results = mutableListOf<ValidationResult>()
        
        // Check for appropriate radius based on category
        val expectedRadius = when (place.category) {
            PlaceCategory.HOME -> 50.0..200.0
            PlaceCategory.WORK -> 50.0..500.0
            PlaceCategory.GYM -> 30.0..200.0
            PlaceCategory.SHOPPING -> 20.0..300.0
            PlaceCategory.RESTAURANT -> 10.0..100.0
            PlaceCategory.ENTERTAINMENT -> 50.0..1000.0
            PlaceCategory.TRANSPORT -> 20.0..500.0
            PlaceCategory.HEALTHCARE -> 20.0..200.0
            PlaceCategory.EDUCATION -> 50.0..1000.0
            PlaceCategory.TRAVEL -> 100.0..2000.0
            PlaceCategory.OUTDOOR -> 100.0..2000.0
            PlaceCategory.SOCIAL -> 50.0..500.0
            PlaceCategory.SERVICES -> 20.0..200.0
            PlaceCategory.UNKNOWN -> 10.0..500.0
            PlaceCategory.CUSTOM -> 10.0..500.0
        }
        
        results.add(
            ValidationUtils.validateCondition(
                condition = place.radius in expectedRadius,
                field = "radius",
                errorCode = "RADIUS_CATEGORY_MISMATCH",
                errorMessage = "Radius ${place.radius}m seems unusual for category ${place.category} (expected: ${expectedRadius.start}-${expectedRadius.endInclusive}m)",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.REVIEW_CLASSIFICATION
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate place address if present
     */
    private fun validateAddress(place: Place): ValidationResult {
        if (place.address.isNullOrBlank()) return ValidationResult.Success
        
        val results = mutableListOf<ValidationResult>()
        
        // Address length validation
        results.add(
            ValidationUtils.validateCondition(
                condition = place.address.length in MIN_ADDRESS_LENGTH..MAX_ADDRESS_LENGTH,
                field = "address",
                errorCode = "INVALID_ADDRESS_LENGTH",
                errorMessage = "Address length must be between $MIN_ADDRESS_LENGTH and $MAX_ADDRESS_LENGTH characters",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.SANITIZE_INPUT
            )
        )
        
        // Basic address format validation
        val hasNumbers = place.address.any { it.isDigit() }
        val hasLetters = place.address.any { it.isLetter() }
        
        results.add(
            ValidationUtils.validateCondition(
                condition = hasNumbers && hasLetters,
                field = "address",
                errorCode = "MALFORMED_ADDRESS",
                errorMessage = "Address should contain both letters and numbers",
                severity = ValidationSeverity.INFO,
                suggestedAction = RecoveryAction.VALIDATE_INPUT
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate Google Place ID if present
     */
    private fun validateGooglePlaceId(place: Place): ValidationResult {
        if (place.placeId.isNullOrBlank()) return ValidationResult.Success
        
        val results = mutableListOf<ValidationResult>()
        
        // Google Place ID format validation
        results.add(
            ValidationUtils.validateCondition(
                condition = place.placeId.length <= MAX_GOOGLE_PLACE_ID_LENGTH,
                field = "googlePlaceId",
                errorCode = "INVALID_GOOGLE_PLACE_ID_LENGTH",
                errorMessage = "Google Place ID is too long: ${place.placeId.length} characters",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.SANITIZE_INPUT
            )
        )
        
        // Basic format check (Google Place IDs typically start with specific prefixes)
        val validPrefixes = listOf("ChIJ", "EhI", "GhIJ")
        val hasValidPrefix = validPrefixes.any { place.placeId.startsWith(it) }
        
        results.add(
            ValidationUtils.validateCondition(
                condition = hasValidPrefix,
                field = "googlePlaceId",
                errorCode = "INVALID_GOOGLE_PLACE_ID_FORMAT",
                errorMessage = "Google Place ID doesn't match expected format",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.VALIDATE_EXTERNAL_DATA
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate business rules and constraints
     */
    private fun validateBusinessRules(place: Place): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Check for minimum viable place data
        val hasMinimumData = place.name.isNotBlank() && 
                           place.latitude != 0.0 && 
                           place.longitude != 0.0 && 
                           place.radius > 0
        
        results.add(
            ValidationUtils.validateCondition(
                condition = hasMinimumData,
                field = "place",
                errorCode = "INSUFFICIENT_PLACE_DATA",
                errorMessage = "Place lacks minimum required data for operation",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        // Check for duplicate detection potential
        if (place.name.length < 3) {
            results.add(
                ValidationResult.Failure(
                    ValidationError(
                        field = "name",
                        code = "SHORT_NAME_DUPLICATE_RISK",
                        message = "Very short place name may cause duplicate detection issues",
                        severity = ValidationSeverity.WARNING,
                        suggestedAction = RecoveryAction.ENHANCE_DATA_QUALITY
                    )
                )
            )
        }
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
}