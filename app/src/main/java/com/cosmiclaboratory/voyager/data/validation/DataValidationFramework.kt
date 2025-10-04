package com.cosmiclaboratory.voyager.data.validation

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.Visit
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Data Validation Framework
 * CRITICAL: Single source of truth for all validation logic throughout the app
 * Prevents inconsistent validation and ensures data integrity
 */
@Singleton
class DataValidationFramework @Inject constructor() {
    
    companion object {
        private const val TAG = "DataValidationFramework"
        
        // Validation thresholds - centralized configuration
        const val MAX_LATITUDE = 90.0
        const val MIN_LATITUDE = -90.0
        const val MAX_LONGITUDE = 180.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_ACCURACY_METERS = 500f
        const val MIN_ACCURACY_METERS = 0f
        const val MAX_SPEED_KMH = 300.0 // Maximum reasonable speed
        const val MIN_PLACE_RADIUS = 5.0 // Minimum place radius in meters
        const val MAX_PLACE_RADIUS = 1000.0 // Maximum place radius in meters
        const val MAX_VISIT_DURATION_HOURS = 168 // 7 days maximum
    }
    
    /**
     * Validate location data with comprehensive checks
     */
    fun validateLocation(location: Location): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Coordinate validation
        if (location.latitude < MIN_LATITUDE || location.latitude > MAX_LATITUDE) {
            issues.add("Invalid latitude: ${location.latitude} (must be between $MIN_LATITUDE and $MAX_LATITUDE)")
        }
        
        if (location.longitude < MIN_LONGITUDE || location.longitude > MAX_LONGITUDE) {
            issues.add("Invalid longitude: ${location.longitude} (must be between $MIN_LONGITUDE and $MAX_LONGITUDE)")
        }
        
        // Accuracy validation
        if (location.accuracy < MIN_ACCURACY_METERS || location.accuracy > MAX_ACCURACY_METERS) {
            issues.add("Invalid accuracy: ${location.accuracy}m (must be between $MIN_ACCURACY_METERS and $MAX_ACCURACY_METERS)")
        }
        
        // Speed validation (if provided)
        location.speed?.let { speed ->
            val speedKmh = speed * 3.6 // Convert m/s to km/h
            if (speedKmh > MAX_SPEED_KMH) {
                issues.add("Unrealistic speed: ${String.format("%.1f", speedKmh)}km/h (max allowed: ${MAX_SPEED_KMH}km/h)")
            }
        }
        
        // Timestamp validation
        val now = LocalDateTime.now()
        if (location.timestamp.isAfter(now.plusMinutes(5))) {
            issues.add("Future timestamp detected: ${location.timestamp} (current time: $now)")
        }
        
        if (location.timestamp.isBefore(now.minusDays(30))) {
            issues.add("Very old timestamp detected: ${location.timestamp} (older than 30 days)")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = if (issues.isEmpty()) ValidationLevel.VALID else ValidationLevel.ERROR
        )
    }
    
    /**
     * Validate place data with business rules
     */
    fun validatePlace(place: Place): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Basic coordinate validation
        val locationValidation = validateCoordinates(place.latitude, place.longitude)
        if (!locationValidation.isValid) {
            issues.addAll(locationValidation.issues)
        }
        
        // Name validation
        if (place.name.isBlank()) {
            issues.add("Place name cannot be empty")
        }
        
        if (place.name.length > 100) {
            issues.add("Place name too long: ${place.name.length} characters (max 100)")
        }
        
        // Radius validation
        if (place.radius < MIN_PLACE_RADIUS || place.radius > MAX_PLACE_RADIUS) {
            issues.add("Invalid place radius: ${place.radius}m (must be between $MIN_PLACE_RADIUS and $MAX_PLACE_RADIUS)")
        }
        
        // Visit count validation
        if (place.visitCount < 0) {
            issues.add("Invalid visit count: ${place.visitCount} (cannot be negative)")
        }
        
        // Time spent validation
        if (place.totalTimeSpent < 0) {
            issues.add("Invalid total time spent: ${place.totalTimeSpent}ms (cannot be negative)")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = if (issues.isEmpty()) ValidationLevel.VALID else ValidationLevel.ERROR
        )
    }
    
    /**
     * Validate visit data with temporal logic
     */
    fun validateVisit(visit: Visit): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Entry time validation
        if (visit.entryTime == null) {
            issues.add("Visit entry time is required")
            return ValidationResult(false, issues, ValidationLevel.ERROR)
        }
        
        val now = LocalDateTime.now()
        
        // Entry time should not be in the future
        if (visit.entryTime!!.isAfter(now.plusMinutes(5))) {
            issues.add("Visit entry time is in the future: ${visit.entryTime}")
        }
        
        // Exit time validation (if provided)
        visit.exitTime?.let { exitTime ->
            if (exitTime.isBefore(visit.entryTime)) {
                issues.add("Visit exit time (${exitTime}) is before entry time (${visit.entryTime})")
            }
            
            if (exitTime.isAfter(now.plusMinutes(5))) {
                issues.add("Visit exit time is in the future: $exitTime")
            }
        }
        
        // Duration validation
        if (visit.duration < 0) {
            issues.add("Invalid visit duration: ${visit.duration}ms (cannot be negative)")
        }
        
        // Check for unrealistic long visits
        val maxDurationMs = MAX_VISIT_DURATION_HOURS * 60 * 60 * 1000L
        if (visit.duration > maxDurationMs) {
            val durationHours = visit.duration / (60 * 60 * 1000L)
            issues.add("Unrealistic visit duration: ${durationHours}h (max allowed: ${MAX_VISIT_DURATION_HOURS}h)")
        }
        
        // Confidence validation
        if (visit.confidence < 0f || visit.confidence > 1f) {
            issues.add("Invalid confidence: ${visit.confidence} (must be between 0.0 and 1.0)")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = if (issues.isEmpty()) ValidationLevel.VALID else ValidationLevel.ERROR
        )
    }
    
    /**
     * Validate time range queries
     */
    fun validateTimeRange(startTime: LocalDateTime, endTime: LocalDateTime): ValidationResult {
        val issues = mutableListOf<String>()
        
        if (endTime.isBefore(startTime)) {
            issues.add("End time ($endTime) is before start time ($startTime)")
        }
        
        val duration = java.time.Duration.between(startTime, endTime)
        if (duration.toDays() > 365) {
            issues.add("Time range too large: ${duration.toDays()} days (max 365 days)")
        }
        
        val now = LocalDateTime.now()
        if (startTime.isAfter(now)) {
            issues.add("Start time is in the future: $startTime")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = if (issues.isEmpty()) ValidationLevel.VALID else ValidationLevel.WARNING
        )
    }
    
    /**
     * Validate daily statistics
     */
    fun validateDailyStats(locationCount: Int, placeCount: Int, timeTracked: Long): ValidationResult {
        val issues = mutableListOf<String>()
        
        if (locationCount < 0) {
            issues.add("Invalid location count: $locationCount (cannot be negative)")
        }
        
        if (placeCount < 0) {
            issues.add("Invalid place count: $placeCount (cannot be negative)")
        }
        
        if (timeTracked < 0) {
            issues.add("Invalid time tracked: ${timeTracked}ms (cannot be negative)")
        }
        
        // Sanity check: can't have more places than locations
        if (placeCount > locationCount && locationCount > 0) {
            issues.add("Places count ($placeCount) cannot exceed locations count ($locationCount)")
        }
        
        // Sanity check: maximum time per day is 24 hours
        val maxDailyTimeMs = 24 * 60 * 60 * 1000L
        if (timeTracked > maxDailyTimeMs) {
            val timeHours = timeTracked / (60 * 60 * 1000L)
            issues.add("Time tracked exceeds 24 hours: ${timeHours}h")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = if (issues.isEmpty()) ValidationLevel.VALID else ValidationLevel.ERROR
        )
    }
    
    /**
     * Validate state transition logic
     */
    fun validateStateTransition(
        fromTrackingActive: Boolean,
        toTrackingActive: Boolean,
        fromPlaceId: Long?,
        toPlaceId: Long?
    ): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Check for logical state transitions
        if (!fromTrackingActive && toPlaceId != null) {
            issues.add("Cannot be at a place when tracking is not active")
        }
        
        // Log state transition for monitoring
        Log.d(TAG, "State transition validation: tracking=$fromTrackingActive→$toTrackingActive, place=$fromPlaceId→$toPlaceId")
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = if (issues.isEmpty()) ValidationLevel.VALID else ValidationLevel.WARNING
        )
    }
    
    // Helper methods
    
    private fun validateCoordinates(latitude: Double, longitude: Double): ValidationResult {
        val issues = mutableListOf<String>()
        
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            issues.add("Invalid latitude: $latitude")
        }
        
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            issues.add("Invalid longitude: $longitude")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            level = ValidationLevel.ERROR
        )
    }
    
    /**
     * Sanitize location data by clamping values to valid ranges
     */
    fun sanitizeLocation(location: Location): Location {
        return location.copy(
            latitude = location.latitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE),
            longitude = location.longitude.coerceIn(MIN_LONGITUDE, MAX_LONGITUDE),
            accuracy = location.accuracy.coerceIn(MIN_ACCURACY_METERS, MAX_ACCURACY_METERS),
            speed = location.speed?.let { maxOf(0f, it) }
        )
    }
    
    /**
     * Sanitize place data by clamping values to valid ranges
     */
    fun sanitizePlace(place: Place): Place {
        return place.copy(
            latitude = place.latitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE),
            longitude = place.longitude.coerceIn(MIN_LONGITUDE, MAX_LONGITUDE),
            radius = place.radius.coerceIn(MIN_PLACE_RADIUS, MAX_PLACE_RADIUS),
            visitCount = place.visitCount.coerceAtLeast(0),
            totalTimeSpent = place.totalTimeSpent.coerceAtLeast(0L)
        )
    }
}

// Data classes for validation results

data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String> = emptyList(),
    val level: ValidationLevel = ValidationLevel.VALID
) {
    fun getFormattedMessage(): String {
        return if (issues.isEmpty()) {
            "Validation passed"
        } else {
            "Validation issues: ${issues.joinToString("; ")}"
        }
    }
}

enum class ValidationLevel {
    VALID,
    WARNING,
    ERROR
}

// Extension functions for easy validation

fun Location.validate(): ValidationResult {
    return DataValidationFramework().validateLocation(this)
}

fun Place.validate(): ValidationResult {
    return DataValidationFramework().validatePlace(this)
}

fun Visit.validate(): ValidationResult {
    return DataValidationFramework().validateVisit(this)
}