package com.cosmiclaboratory.voyager.domain.validation

import com.cosmiclaboratory.voyager.domain.exception.RecoveryAction
import com.cosmiclaboratory.voyager.domain.model.Location
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive validator for Location domain objects
 * Validates GPS coordinates, accuracy, timestamps, and other location data
 */
@Singleton
class LocationValidator @Inject constructor() : Validator<Location> {
    
    companion object {
        // Validation constants
        private const val MIN_LATITUDE = -90.0
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0
        private const val MAX_REASONABLE_ACCURACY = 1000.0f // 1km
        private const val MAX_REASONABLE_SPEED = 200.0f // 200 m/s (720 km/h)
        private const val MAX_REASONABLE_ALTITUDE = 10000.0 // 10km above sea level
        private const val MIN_REASONABLE_ALTITUDE = -500.0 // 500m below sea level
        private const val MAX_FUTURE_TIME_MINUTES = 5L // Allow 5 minutes in future for clock drift
        private const val MAX_PAST_TIME_HOURS = 168L // Allow 1 week in past
    }
    
    override fun validate(value: Location): ValidationResult {
        return ValidationUtils.validateAll(
            { validateCoordinates(value) },
            { validateAccuracy(value) },
            { validateTimestamp(value) },
            { validateSpeed(value) },
            { validateAltitude(value) },
            { validateBearing(value) },
            { validateBusinessRules(value) }
        )
    }
    
    /**
     * Validate GPS coordinates
     */
    private fun validateCoordinates(location: Location): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Latitude validation
        results.add(
            ValidationUtils.validateCondition(
                condition = location.latitude in MIN_LATITUDE..MAX_LATITUDE,
                field = "latitude",
                errorCode = "INVALID_LATITUDE",
                errorMessage = "Latitude must be between $MIN_LATITUDE and $MAX_LATITUDE degrees, got ${location.latitude}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        // Longitude validation
        results.add(
            ValidationUtils.validateCondition(
                condition = location.longitude in MIN_LONGITUDE..MAX_LONGITUDE,
                field = "longitude", 
                errorCode = "INVALID_LONGITUDE",
                errorMessage = "Longitude must be between $MIN_LONGITUDE and $MAX_LONGITUDE degrees, got ${location.longitude}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        // Check for null island (0,0) coordinates
        results.add(
            ValidationUtils.validateCondition(
                condition = !(location.latitude == 0.0 && location.longitude == 0.0),
                field = "coordinates",
                errorCode = "NULL_ISLAND_COORDINATES",
                errorMessage = "Coordinates cannot be exactly (0,0) - likely indicates GPS error",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate GPS accuracy
     */
    private fun validateAccuracy(location: Location): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Basic accuracy validation
        results.add(
            ValidationUtils.validateCondition(
                condition = location.accuracy > 0,
                field = "accuracy",
                errorCode = "INVALID_ACCURACY",
                errorMessage = "GPS accuracy must be positive, got ${location.accuracy}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        // Reasonable accuracy check (warning for poor accuracy)
        results.add(
            ValidationUtils.validateCondition(
                condition = location.accuracy <= MAX_REASONABLE_ACCURACY,
                field = "accuracy",
                errorCode = "POOR_GPS_ACCURACY",
                errorMessage = "GPS accuracy is very poor: ${location.accuracy}m (threshold: ${MAX_REASONABLE_ACCURACY}m)",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.IMPROVE_GPS_CONDITIONS
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate timestamp
     */
    private fun validateTimestamp(location: Location): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        val now = LocalDateTime.now()
        
        // Check for future timestamps (allow small drift)
        val futureLimit = now.plusMinutes(MAX_FUTURE_TIME_MINUTES)
        results.add(
            ValidationUtils.validateCondition(
                condition = !location.timestamp.isAfter(futureLimit),
                field = "timestamp",
                errorCode = "FUTURE_TIMESTAMP",
                errorMessage = "Location timestamp is too far in the future: ${location.timestamp}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TIMESTAMP
            )
        )
        
        // Check for very old timestamps
        val pastLimit = now.minusHours(MAX_PAST_TIME_HOURS)
        results.add(
            ValidationUtils.validateCondition(
                condition = !location.timestamp.isBefore(pastLimit),
                field = "timestamp",
                errorCode = "STALE_TIMESTAMP",
                errorMessage = "Location timestamp is too old: ${location.timestamp}",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.SKIP_STALE_DATA
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate speed if present
     */
    private fun validateSpeed(location: Location): ValidationResult {
        if (location.speed == null) return ValidationResult.Success
        
        val results = mutableListOf<ValidationResult>()
        
        // Basic speed validation
        results.add(
            ValidationUtils.validateCondition(
                condition = location.speed >= 0,
                field = "speed",
                errorCode = "NEGATIVE_SPEED",
                errorMessage = "Speed cannot be negative, got ${location.speed} m/s",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.REMOVE_INVALID_FIELD
            )
        )
        
        // Reasonable speed check
        results.add(
            ValidationUtils.validateCondition(
                condition = location.speed <= MAX_REASONABLE_SPEED,
                field = "speed",
                errorCode = "EXCESSIVE_SPEED",
                errorMessage = "Speed seems unreasonably high: ${location.speed} m/s (${location.speed * 3.6} km/h)",
                severity = ValidationSeverity.WARNING,
                suggestedAction = RecoveryAction.VALIDATE_GPS_DATA
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
    
    /**
     * Validate altitude if present
     */
    private fun validateAltitude(location: Location): ValidationResult {
        if (location.altitude == null) return ValidationResult.Success
        
        return ValidationUtils.validateCondition(
            condition = location.altitude in MIN_REASONABLE_ALTITUDE..MAX_REASONABLE_ALTITUDE,
            field = "altitude",
            errorCode = "UNREASONABLE_ALTITUDE",
            errorMessage = "Altitude seems unreasonable: ${location.altitude}m (valid range: $MIN_REASONABLE_ALTITUDE to $MAX_REASONABLE_ALTITUDE)",
            severity = ValidationSeverity.WARNING,
            suggestedAction = RecoveryAction.VALIDATE_GPS_DATA
        )
    }
    
    /**
     * Validate bearing if present
     */
    private fun validateBearing(location: Location): ValidationResult {
        if (location.bearing == null) return ValidationResult.Success
        
        return ValidationUtils.validateCondition(
            condition = location.bearing in 0.0f..360.0f,
            field = "bearing",
            errorCode = "INVALID_BEARING",
            errorMessage = "Bearing must be between 0 and 360 degrees, got ${location.bearing}",
            severity = ValidationSeverity.ERROR,
            suggestedAction = RecoveryAction.REMOVE_INVALID_FIELD
        )
    }
    
    /**
     * Validate business rules and constraints
     */
    private fun validateBusinessRules(location: Location): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Check if location has minimum required fields for place detection
        val hasMinimumFields = location.latitude != 0.0 && 
                              location.longitude != 0.0 && 
                              location.accuracy > 0
        
        results.add(
            ValidationUtils.validateCondition(
                condition = hasMinimumFields,
                field = "location",
                errorCode = "INSUFFICIENT_DATA",
                errorMessage = "Location lacks minimum required data for processing",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.SKIP_INVALID_DATA
            )
        )
        
        // Check for indoor/poor GPS conditions based on accuracy
        results.add(
            ValidationUtils.validateCondition(
                condition = location.accuracy <= 50.0f,
                field = "accuracy",
                errorCode = "INDOOR_GPS_CONDITIONS",
                errorMessage = "GPS accuracy suggests indoor/poor reception conditions: ${location.accuracy}m",
                severity = ValidationSeverity.INFO,
                suggestedAction = RecoveryAction.IMPROVE_GPS_CONDITIONS
            )
        )
        
        return results.reduce { acc, result -> acc.combineWith(result) }
    }
}