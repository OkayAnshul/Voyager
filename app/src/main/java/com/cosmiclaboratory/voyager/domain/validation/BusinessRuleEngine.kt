package com.cosmiclaboratory.voyager.domain.validation

import com.cosmiclaboratory.voyager.domain.exception.RecoveryAction
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.utils.LocationUtils
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Business rule validation engine for complex multi-entity validation
 * Handles validation scenarios that span multiple domain objects
 */
@Singleton
class BusinessRuleEngine @Inject constructor() {
    
    companion object {
        // Business rule constants
        private const val MIN_VISIT_DURATION_MINUTES = 1L
        private const val MAX_VISIT_DURATION_HOURS = 72L // 3 days
        private const val MIN_DISTANCE_BETWEEN_PLACES = 10.0 // meters
        private const val MAX_SPEED_BETWEEN_LOCATIONS = 150.0 // m/s (540 km/h)
        private const val MIN_TIME_BETWEEN_VISITS_MINUTES = 1L
        private const val MAX_LOCATIONS_PER_HOUR = 3600 // 1 per second max
        private const val MAX_PLACES_PER_LOCATION = 5 // reasonable overlap
    }
    
    /**
     * Validate visit business rules
     */
    fun validateVisit(visit: Visit, place: Place? = null): ValidationResult {
        return ValidationUtils.validateAll(
            { validateVisitDuration(visit) },
            { validateVisitTimes(visit) },
            { validateVisitPlace(visit, place) }
        )
    }
    
    /**
     * Validate location sequence business rules
     */
    fun validateLocationSequence(locations: List<Location>): ValidationResult {
        if (locations.size < 2) return ValidationResult.Success
        
        return ValidationUtils.validateAll(
            { validateLocationTimestamps(locations) },
            { validateLocationMovement(locations) },
            { validateLocationFrequency(locations) }
        )
    }
    
    /**
     * Validate place proximity rules
     */
    fun validatePlaceProximity(newPlace: Place, existingPlaces: List<Place>): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        existingPlaces.forEach { existingPlace ->
            val distance = LocationUtils.calculateDistance(
                newPlace.latitude, newPlace.longitude,
                existingPlace.latitude, existingPlace.longitude
            )
            
            // Check for places that are too close
            if (distance < MIN_DISTANCE_BETWEEN_PLACES) {
                results.add(
                    ValidationResult.Failure(
                        ValidationError(
                            field = "location",
                            code = "PLACES_TOO_CLOSE",
                            message = "New place is only ${distance.toInt()}m from existing place '${existingPlace.name}' (minimum: ${MIN_DISTANCE_BETWEEN_PLACES}m)",
                            severity = ValidationSeverity.WARNING,
                            suggestedAction = RecoveryAction.MERGE_DUPLICATES,
                            context = mapOf(
                                "existingPlaceId" to existingPlace.id,
                                "existingPlaceName" to existingPlace.name,
                                "distance" to distance
                            )
                        )
                    )
                )
            }
            
            // Check for potential duplicates with similar names
            if (distance < 100.0 && areSimilarNames(newPlace.name, existingPlace.name)) {
                results.add(
                    ValidationResult.Failure(
                        ValidationError(
                            field = "name",
                            code = "POTENTIAL_DUPLICATE",
                            message = "Potential duplicate place: '${newPlace.name}' vs '${existingPlace.name}' at ${distance.toInt()}m distance",
                            severity = ValidationSeverity.WARNING,
                            suggestedAction = RecoveryAction.MERGE_DUPLICATES,
                            context = mapOf(
                                "existingPlaceId" to existingPlace.id,
                                "existingPlaceName" to existingPlace.name,
                                "similarity" to calculateNameSimilarity(newPlace.name, existingPlace.name)
                            )
                        )
                    )
                )
            }
        }
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate user preferences consistency
     */
    fun validateUserPreferences(preferences: UserPreferences): ValidationResult {
        return ValidationUtils.validateAll(
            { validateTrackingSettings(preferences) },
            { validatePlaceDetectionSettings(preferences) },
            { validatePerformanceSettings(preferences) },
            { validatePrivacySettings(preferences) }
        )
    }
    
    /**
     * Validate visit duration
     */
    private fun validateVisitDuration(visit: Visit): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        if (visit.exitTime != null) {
            val durationMinutes = ChronoUnit.MINUTES.between(visit.entryTime, visit.exitTime)
            
            // Minimum duration check
            results.add(
                ValidationUtils.validateCondition(
                    condition = durationMinutes >= MIN_VISIT_DURATION_MINUTES,
                    field = "duration",
                    errorCode = "VISIT_TOO_SHORT",
                    errorMessage = "Visit duration is too short: ${durationMinutes} minutes (minimum: $MIN_VISIT_DURATION_MINUTES)",
                    severity = ValidationSeverity.WARNING,
                    suggestedAction = RecoveryAction.SKIP_SHORT_VISITS
                )
            )
            
            // Maximum duration check
            val durationHours = ChronoUnit.HOURS.between(visit.entryTime, visit.exitTime)
            results.add(
                ValidationUtils.validateCondition(
                    condition = durationHours <= MAX_VISIT_DURATION_HOURS,
                    field = "duration",
                    errorCode = "VISIT_TOO_LONG",
                    errorMessage = "Visit duration seems unreasonably long: ${durationHours} hours (maximum: $MAX_VISIT_DURATION_HOURS)",
                    severity = ValidationSeverity.WARNING,
                    suggestedAction = RecoveryAction.SPLIT_LONG_VISITS
                )
            )
        }
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate visit timing
     */
    private fun validateVisitTimes(visit: Visit): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        if (visit.exitTime != null) {
            // Entry time must be before exit time
            results.add(
                ValidationUtils.validateCondition(
                    condition = visit.entryTime.isBefore(visit.exitTime),
                    field = "times",
                    errorCode = "INVALID_VISIT_SEQUENCE",
                    errorMessage = "Visit entry time must be before exit time",
                    severity = ValidationSeverity.ERROR,
                    suggestedAction = RecoveryAction.FIX_TIMESTAMP_ORDER
                )
            )
        }
        
        // Visit shouldn't be too far in the future
        val now = LocalDateTime.now()
        results.add(
            ValidationUtils.validateCondition(
                condition = !visit.entryTime.isAfter(now.plusMinutes(5)),
                field = "entryTime",
                errorCode = "FUTURE_VISIT",
                errorMessage = "Visit entry time is in the future: ${visit.entryTime}",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TIMESTAMP
            )
        )
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate visit and place relationship
     */
    private fun validateVisitPlace(visit: Visit, place: Place?): ValidationResult {
        if (place == null) return ValidationResult.Success
        
        // Ensure visit is associated with the correct place
        return ValidationUtils.validateCondition(
            condition = visit.placeId == place.id,
            field = "placeId",
            errorCode = "VISIT_PLACE_MISMATCH",
            errorMessage = "Visit place ID doesn't match provided place",
            severity = ValidationSeverity.ERROR,
            suggestedAction = RecoveryAction.FIX_DATA_RELATIONSHIPS
        )
    }
    
    /**
     * Validate location timestamp sequence
     */
    private fun validateLocationTimestamps(locations: List<Location>): ValidationResult {
        val sortedLocations = locations.sortedBy { it.timestamp }
        
        for (i in 1 until sortedLocations.size) {
            val prev = sortedLocations[i - 1]
            val curr = sortedLocations[i]
            
            if (curr.timestamp.isBefore(prev.timestamp)) {
                return ValidationResult.Failure(
                    ValidationError(
                        field = "timestamp",
                        code = "TIMESTAMP_OUT_OF_ORDER",
                        message = "Location timestamps are not in chronological order",
                        severity = ValidationSeverity.ERROR,
                        suggestedAction = RecoveryAction.SORT_BY_TIMESTAMP
                    )
                )
            }
        }
        
        return ValidationResult.Success
    }
    
    /**
     * Validate realistic movement between locations
     */
    private fun validateLocationMovement(locations: List<Location>): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        for (i in 1 until locations.size) {
            val prev = locations[i - 1]
            val curr = locations[i]
            
            val distance = LocationUtils.calculateDistance(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )
            
            val timeDiff = ChronoUnit.SECONDS.between(prev.timestamp, curr.timestamp)
            if (timeDiff > 0) {
                val speed = distance / timeDiff // m/s
                
                if (speed > MAX_SPEED_BETWEEN_LOCATIONS) {
                    results.add(
                        ValidationResult.Failure(
                            ValidationError(
                                field = "movement",
                                code = "UNREALISTIC_SPEED",
                                message = "Unrealistic speed between locations: ${(speed * 3.6).toInt()} km/h (${distance.toInt()}m in ${timeDiff}s)",
                                severity = ValidationSeverity.WARNING,
                                suggestedAction = RecoveryAction.VALIDATE_GPS_DATA,
                                context = mapOf(
                                    "speed_mps" to speed,
                                    "speed_kmh" to (speed * 3.6),
                                    "distance_m" to distance,
                                    "time_s" to timeDiff
                                )
                            )
                        )
                    )
                }
            }
        }
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate location frequency
     */
    private fun validateLocationFrequency(locations: List<Location>): ValidationResult {
        if (locations.isEmpty()) return ValidationResult.Success
        
        val timeRange = ChronoUnit.HOURS.between(
            locations.minOf { it.timestamp },
            locations.maxOf { it.timestamp }
        ).coerceAtLeast(1)
        
        val locationsPerHour = locations.size.toDouble() / timeRange
        
        return ValidationUtils.validateCondition(
            condition = locationsPerHour <= MAX_LOCATIONS_PER_HOUR,
            field = "frequency",
            errorCode = "EXCESSIVE_LOCATION_FREQUENCY",
            errorMessage = "Location frequency is too high: ${locationsPerHour.toInt()} per hour (max: $MAX_LOCATIONS_PER_HOUR)",
            severity = ValidationSeverity.WARNING,
            suggestedAction = RecoveryAction.THROTTLE_DATA_COLLECTION
        )
    }
    
    /**
     * Validate tracking settings
     */
    private fun validateTrackingSettings(preferences: UserPreferences): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Validate update interval
        results.add(
            ValidationUtils.validateCondition(
                condition = preferences.minTimeBetweenUpdatesSeconds in 5..3600,
                field = "minTimeBetweenUpdatesSeconds",
                errorCode = "INVALID_UPDATE_INTERVAL",
                errorMessage = "Update interval must be between 5 seconds and 1 hour",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        // Validate accuracy threshold
        results.add(
            ValidationUtils.validateCondition(
                condition = preferences.maxGpsAccuracyMeters in 1f..1000f,
                field = "maxGpsAccuracyMeters",
                errorCode = "INVALID_ACCURACY_THRESHOLD",
                errorMessage = "GPS accuracy threshold must be between 1m and 1000m",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate place detection settings
     */
    private fun validatePlaceDetectionSettings(preferences: UserPreferences): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        // Validate clustering parameters
        results.add(
            ValidationUtils.validateCondition(
                condition = preferences.clusteringDistanceMeters in 10.0..500.0,
                field = "clusteringDistanceMeters",
                errorCode = "INVALID_CLUSTERING_DISTANCE",
                errorMessage = "Clustering distance must be between 10m and 500m",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        results.add(
            ValidationUtils.validateCondition(
                condition = preferences.minPointsForCluster in 3..100,
                field = "minPointsForCluster",
                errorCode = "INVALID_MIN_POINTS",
                errorMessage = "Minimum points for cluster must be between 3 and 100",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate performance settings
     */
    private fun validatePerformanceSettings(preferences: UserPreferences): ValidationResult {
        val results = mutableListOf<ValidationResult>()
        
        results.add(
            ValidationUtils.validateCondition(
                condition = preferences.maxLocationsToProcess in 100..50000,
                field = "maxLocationsToProcess",
                errorCode = "INVALID_MAX_LOCATIONS",
                errorMessage = "Max locations to process must be between 100 and 50,000",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        results.add(
            ValidationUtils.validateCondition(
                condition = preferences.dataProcessingBatchSize in 50..5000,
                field = "dataProcessingBatchSize",
                errorCode = "INVALID_BATCH_SIZE",
                errorMessage = "Data processing batch size must be between 50 and 5,000",
                severity = ValidationSeverity.ERROR,
                suggestedAction = RecoveryAction.ADJUST_TO_VALID_RANGE
            )
        )
        
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.combineWith(result)
        }
    }
    
    /**
     * Validate privacy settings
     */
    private fun validatePrivacySettings(preferences: UserPreferences): ValidationResult {
        // Add privacy-related validation rules here
        // For now, return success as privacy settings are mostly boolean flags
        return ValidationResult.Success
    }
    
    /**
     * Check if two place names are similar
     */
    private fun areSimilarNames(name1: String, name2: String): Boolean {
        val similarity = calculateNameSimilarity(name1, name2)
        return similarity > 0.8 // 80% similarity threshold
    }
    
    /**
     * Calculate name similarity using Levenshtein distance
     */
    private fun calculateNameSimilarity(name1: String, name2: String): Double {
        val s1 = name1.lowercase().trim()
        val s2 = name2.lowercase().trim()
        
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        val distance = levenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        
        return 1.0 - (distance.toDouble() / maxLength)
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }
}