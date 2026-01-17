package com.cosmiclaboratory.voyager.domain.usecase

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.domain.exception.*
import com.cosmiclaboratory.voyager.domain.validation.ValidationService
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceDetectionUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val errorHandler: ErrorHandler,
    private val validationService: ValidationService,
    private val enrichPlaceWithDetailsUseCase: EnrichPlaceWithDetailsUseCase,  // NEW: Geocoding integration
    private val autoAcceptDecisionUseCase: AutoAcceptDecisionUseCase,  // Week 3: Auto-accept decision
    private val placeReviewUseCases: PlaceReviewUseCases,  // Week 3: Review management
    private val categoryLearningEngine: CategoryLearningEngine  // Week 3: Category learning
) {
    
    companion object {
        private const val TAG = "PlaceDetectionUseCases"
    }
    
    /**
     * Debug function to manually trigger place detection with comprehensive logging
     * This bypasses WorkManager and runs place detection immediately
     */
    suspend fun debugManualPlaceDetection(): String {
        return try {
            Log.i(TAG, "=== DEBUG MANUAL PLACE DETECTION STARTED ===")
            
            val preferences = preferencesRepository.getCurrentPreferences()
            Log.i(TAG, "Current preferences: enablePlaceDetection=${preferences.enablePlaceDetection}")
            
            val totalLocations = locationRepository.getRecentLocations(Int.MAX_VALUE).first().size
            Log.i(TAG, "Total locations in database: $totalLocations")
            
            val existingPlaces = placeRepository.getAllPlaces().first()
            Log.i(TAG, "Existing places in database: ${existingPlaces.size}")
            
            // Force place detection even if disabled
            val originalEnabled = preferences.enablePlaceDetection
            val testPreferences = preferences.copy(enablePlaceDetection = true)
            
            val detectedPlaces = detectNewPlacesInternal(testPreferences)
            
            val result = """
                DEBUG PLACE DETECTION RESULTS:
                - Total locations: $totalLocations
                - Existing places: ${existingPlaces.size}
                - Places detected: ${detectedPlaces.size}
                - Place detection enabled: $originalEnabled
                - Detection forced: ${!originalEnabled}
            """.trimIndent()
            
            Log.i(TAG, result)
            Log.i(TAG, "=== DEBUG MANUAL PLACE DETECTION COMPLETED ===")
            
            result
        } catch (e: Exception) {
            val errorResult = "DEBUG PLACE DETECTION FAILED: ${e.message}"
            Log.e(TAG, errorResult, e)
            errorResult
        }
    }
    
    suspend fun detectNewPlaces(): List<Place> {
        return errorHandler.executeWithErrorHandling(
            operation = {
                Log.i(TAG, "=== PLACE DETECTION STARTED ===")
                val startTime = System.currentTimeMillis()
                
                val preferences = preferencesRepository.getCurrentPreferences()
                Log.i(TAG, "PREFERENCES: placeDetectionEnabled=${preferences.enablePlaceDetection}, " +
                    "maxAccuracy=${preferences.maxGpsAccuracyMeters}m, " +
                    "maxSpeed=${preferences.maxSpeedKmh}km/h, " +
                    "triggerCount=${preferences.autoDetectTriggerCount}")
                
                if (!preferences.enablePlaceDetection) {
                    Log.w(TAG, "CRITICAL: Place detection is DISABLED in preferences - no places will be detected!")
                    return@executeWithErrorHandling emptyList<Place>()
                }
                
                val result = detectNewPlacesInternal(preferences)
                val duration = System.currentTimeMillis() - startTime
                
                Log.i(TAG, "=== PLACE DETECTION COMPLETED in ${duration}ms: ${result.size} places detected ===")
                if (result.isEmpty()) {
                    Log.w(TAG, "WARNING: No places detected - check location quality and clustering parameters")
                }
                
                result
            },
            context = ErrorContext(
                operation = "detectNewPlaces", 
                component = "PlaceDetectionUseCases"
            )
        ).getOrElse { 
            Log.e(TAG, "CRITICAL: Place detection failed with exception")
            emptyList() 
        }
    }
    
    private suspend fun detectNewPlacesInternal(preferences: UserPreferences): List<Place> {
        
        // Limit processing to prevent OOM on older devices
        val maxLocationsToProcess = minOf(preferences.maxLocationsToProcess, 5000)
        val recentLocations = locationRepository.getRecentLocations(maxLocationsToProcess).first()
        Log.i(TAG, "STEP 1: Retrieved ${recentLocations.size} recent locations for processing (limited to $maxLocationsToProcess)")
        
        if (recentLocations.isEmpty()) {
            Log.e(TAG, "CRITICAL: No recent locations found in database - cannot detect places!")
            return emptyList()
        }
        
        // Debug: Show sample of locations
        val sampleSize = minOf(3, recentLocations.size)
        recentLocations.take(sampleSize).forEachIndexed { index, location ->
            Log.d(TAG, "Sample location ${index + 1}: lat=${location.latitude}, lng=${location.longitude}, " +
                "accuracy=${location.accuracy}m, speed=${location.speed}km/h, time=${location.timestamp}")
        }
        
        // Process in batches to manage memory usage
        val batchSize = preferences.dataProcessingBatchSize.coerceIn(100, 1000)
        val allFilteredLocations = mutableListOf<Location>()
        
        Log.i(TAG, "STEP 2: Quality filtering ${recentLocations.size} locations in batches of $batchSize")
        recentLocations.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            val filteredBatch = filterLocationsByQuality(batch, preferences)
            allFilteredLocations.addAll(filteredBatch)
            Log.d(TAG, "Batch ${batchIndex + 1}: ${batch.size} → ${filteredBatch.size} locations passed filtering")
        }
        
        val removedCount = recentLocations.size - allFilteredLocations.size
        Log.i(TAG, "STEP 2 RESULT: ${allFilteredLocations.size} quality locations (${removedCount} removed by filtering)")
        
        if (allFilteredLocations.isEmpty()) {
            Log.e(TAG, "CRITICAL: No locations passed quality filtering! Check filtering criteria:")
            Log.e(TAG, "  - maxAccuracy: ${preferences.maxGpsAccuracyMeters}m")
            Log.e(TAG, "  - maxSpeed: ${preferences.maxSpeedKmh}km/h") 
            Log.e(TAG, "  - Consider relaxing quality filters or check location data quality")
            return emptyList()
        }
        
        // Limit clustering to prevent performance issues
        val locationsToCluster = if (allFilteredLocations.size > 2000) {
            Log.d(TAG, "Limiting locations for clustering from ${allFilteredLocations.size} to 2000 for performance")
            allFilteredLocations.takeLast(2000) // Use most recent locations
        } else {
            allFilteredLocations
        }
        
        val locationPairs = locationsToCluster.map { it.latitude to it.longitude }
        Log.i(TAG, "STEP 3: Starting DBSCAN clustering on ${locationPairs.size} location points")
        Log.i(TAG, "CLUSTERING PARAMETERS: distance=${preferences.clusteringDistanceMeters}m, minPoints=${preferences.minPointsForCluster}")
        
        val clusters = LocationUtils.clusterLocationsWithPreferences(locationPairs, preferences)
        Log.i(TAG, "STEP 3 RESULT: Found ${clusters.size} location clusters using DBSCAN")
        
        if (clusters.isEmpty()) {
            Log.e(TAG, "CRITICAL: No clusters found! Possible causes:")
            Log.e(TAG, "  - Clustering distance too small (${preferences.clusteringDistanceMeters}m)")
            Log.e(TAG, "  - MinPoints too high (${preferences.minPointsForCluster})")
            Log.e(TAG, "  - Locations too spread out")
            Log.e(TAG, "  - Consider reducing clusteringDistanceMeters or minPointsForCluster")
            return emptyList()
        }
        
        // Log cluster details for debugging
        clusters.forEachIndexed { index, cluster ->
            val centerLat = cluster.map { it.first }.average()
            val centerLng = cluster.map { it.second }.average()
            Log.d(TAG, "Cluster ${index + 1}: ${cluster.size} points at (${String.format("%.6f", centerLat)}, ${String.format("%.6f", centerLng)})")
        }
        
        val newPlaces = mutableListOf<Place>()
        
        Log.i(TAG, "STEP 4: Processing ${clusters.size} clusters for place creation...")
        
        // Process clusters in batches to manage memory and database operations
        clusters.chunked(10).forEach { clusterBatch ->
            clusterBatch.forEach { cluster ->
                try {
                    val centerLat = cluster.map { it.first }.average()
                    val centerLng = cluster.map { it.second }.average()
                    
                    val nearbyPlaces = placeRepository.getPlacesNearLocation(
                        centerLat, centerLng, preferences.placeDetectionRadius / 1000.0 // Convert to km
                    )
                    
                    // IMPROVED DUPLICATE DETECTION: Check both center distance AND cluster overlap
                    val minDistanceToExisting = nearbyPlaces.minOfOrNull { existingPlace ->
                        LocationUtils.calculateDistance(
                            existingPlace.latitude, existingPlace.longitude,
                            centerLat, centerLng
                        )
                    } ?: Double.MAX_VALUE

                    // NEW: Check if cluster points fall within existing place radius (overlap detection)
                    val overlapsWithExisting = nearbyPlaces.any { existingPlace ->
                        isClusterOverlappingWithPlace(cluster, existingPlace)
                    }

                    // Only create new place if far enough from existing places AND doesn't overlap
                    val minimumDistanceBetweenPlaces = preferences.minimumDistanceBetweenPlaces.toDouble()
                    val shouldCreatePlace = minDistanceToExisting >= minimumDistanceBetweenPlaces && !overlapsWithExisting
                    
                    if (shouldCreatePlace) {
                        Log.d(TAG, "CLUSTER ANALYSIS: Creating place at ($centerLat, $centerLng). " +
                            "Distance to nearest existing place: ${String.format("%.1f", minDistanceToExisting)}m, " +
                            "Overlaps with existing: $overlapsWithExisting")
                    } else {
                        val reason = when {
                            overlapsWithExisting -> "overlaps with existing place"
                            else -> "too close (${String.format("%.1f", minDistanceToExisting)}m < ${minimumDistanceBetweenPlaces}m)"
                        }
                        Log.d(TAG, "CLUSTER SKIPPED: $reason")
                    }
                    
                    if (shouldCreatePlace) {
                        val locationsInCluster = allFilteredLocations.filter { location ->
                            cluster.any { clusterPoint ->
                                LocationUtils.calculateDistance(
                                    location.latitude, location.longitude,
                                    clusterPoint.first, clusterPoint.second
                                ) <= preferences.placeDetectionRadius
                            }
                        }
                        
                        if (locationsInCluster.size >= preferences.minPointsForCluster) {
                            // Phase 2: Analyze activity distribution at this cluster
                            val activityCounts = locationsInCluster
                                .groupBy { it.userActivity }
                                .mapValues { it.value.size }

                            val dominantActivity = activityCounts.maxByOrNull { it.value }?.key

                            val activityDistribution = activityCounts.mapValues {
                                it.value.toFloat() / locationsInCluster.size
                            }

                            Log.d(TAG, "Phase 2: Activity distribution at cluster: $activityDistribution")

                            // Phase 2: Analyze semantic contexts
                            val contextCounts = locationsInCluster
                                .mapNotNull { it.semanticContext }
                                .groupBy { it }
                                .mapValues { it.value.size }

                            val dominantContext = contextCounts.maxByOrNull { it.value }?.key

                            val contextDistribution = contextCounts.mapValues {
                                it.value.toFloat() / locationsInCluster.size
                            }

                            Log.d(TAG, "Phase 2: Context distribution at cluster: $contextDistribution")

                            // ISSUE #3 FIX: Stop automatic category prediction
                            // All places start as UNKNOWN until user manually categorizes them
                            val category = PlaceCategory.UNKNOWN

                            // OLD CODE (removed): Automatic categorization based on activity data
                            // val category = categorizePlaceFromActivityAndTime(
                            //     locationsInCluster,
                            //     dominantActivity,
                            //     dominantContext,
                            //     activityDistribution,
                            //     preferences
                            // )

                            val placeName = generatePlaceName(category, centerLat, centerLng)

                            var place = Place(
                                name = placeName,
                                category = category,
                                latitude = centerLat,
                                longitude = centerLng,
                                visitCount = 1,
                                radius = calculateOptimalRadius(cluster),
                                isCustom = false,
                                confidence = calculateConfidence(locationsInCluster, category, preferences),
                                // Phase 2: Activity insights
                                dominantActivity = dominantActivity,
                                activityDistribution = activityDistribution,
                                dominantSemanticContext = dominantContext,
                                contextDistribution = contextDistribution
                            )

                            // NEW: Enrich place with geocoding (real addresses and names)
                            try {
                                place = enrichPlaceWithDetailsUseCase(place)
                                Log.d(TAG, "Enriched place with geocoding: ${place.name}, address: ${place.address}")
                            } catch (geocodingException: Exception) {
                                Log.w(TAG, "Geocoding failed for place, using generic name", geocodingException)
                                // Continue with generic name if geocoding fails
                            }

                            // CRITICAL FIX: Robust place creation with error recovery
                            try {
                                val placeId = placeRepository.insertPlace(place)
                                val createdPlace = place.copy(id = placeId)
                                newPlaces.add(createdPlace)
                                Log.d(TAG, "Created new place: $placeName at ($centerLat, $centerLng) " +
                                    "with confidence ${String.format("%.2f", place.confidence)} and ${locationsInCluster.size} locations")

                                // Create initial visit records for this place with error recovery
                                var visitCount = 0
                                try {
                                    visitCount = createInitialVisits(placeId, locationsInCluster, preferences)
                                    Log.d(TAG, "Successfully created $visitCount initial visits for place $placeName")
                                } catch (visitException: Exception) {
                                    Log.w(TAG, "Failed to create initial visits for place $placeName - place created but no visits", visitException)
                                    // Place creation succeeded, visit creation failed - this is acceptable
                                }

                                // Week 3: Auto-accept decision and review system integration
                                try {
                                    handlePlaceReview(
                                        createdPlace,
                                        locationsInCluster.size,
                                        visitCount,
                                        preferences
                                    )
                                } catch (reviewException: Exception) {
                                    Log.w(TAG, "Failed to create place review for $placeName - place created but review not tracked", reviewException)
                                    // Place creation succeeded, review failed - this is acceptable
                                }
                            } catch (placeException: Exception) {
                                Log.e(TAG, "CRITICAL ERROR: Failed to create place $placeName at ($centerLat, $centerLng)", placeException)
                                
                                // Try alternative place creation strategy
                                try {
                                    val simplifiedPlace = place.copy(
                                        name = "Place ${System.currentTimeMillis() % 10000}",
                                        category = PlaceCategory.UNKNOWN,
                                        confidence = 0.5f
                                    )
                                    val fallbackPlaceId = placeRepository.insertPlace(simplifiedPlace)
                                    newPlaces.add(simplifiedPlace.copy(id = fallbackPlaceId))
                                    Log.w(TAG, "Created simplified fallback place with ID $fallbackPlaceId")
                                } catch (fallbackException: Exception) {
                                    Log.e(TAG, "CRITICAL: Even fallback place creation failed", fallbackException)
                                    // Continue processing other clusters instead of failing completely
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cluster: ${e.message}", e)
                    // Continue with next cluster instead of failing completely
                }
            }
        }
        
        Log.d(TAG, "Place detection completed: created ${newPlaces.size} new places")
        return newPlaces
    }
    
    /**
     * DISABLED: Automatic place improvement removed to prevent unwanted category changes
     * Categories must be manually assigned by user - no automatic updates
     */
    suspend fun improveExistingPlaces() {
        // CRITICAL FIX: Do NOT automatically update place categories
        // All category assignments must be user-driven
        Log.i(TAG, "improveExistingPlaces() called but disabled - no automatic categorization allowed")

        // OLD CODE (disabled to prevent automatic categorization):
        // val preferences = preferencesRepository.getCurrentPreferences()
        // val allPlaces = placeRepository.getAllPlaces().first()
        //
        // allPlaces.forEach { place ->
        //     if (place.confidence < preferences.autoConfidenceThreshold) {
        //         val recentLocations = locationRepository.getLocationsInBounds(
        //             place.latitude - 0.002, place.latitude + 0.002,
        //             place.longitude - 0.002, place.longitude + 0.002
        //         )
        //
        //         if (recentLocations.isNotEmpty()) {
        //             val improvedCategory = categorizePlace(recentLocations, preferences)
        //             val improvedConfidence = calculateConfidence(recentLocations, improvedCategory, preferences)
        //
        //             if (improvedConfidence > place.confidence) {
        //                 placeRepository.updatePlace(
        //                     place.copy(
        //                         category = improvedCategory,
        //                         confidence = improvedConfidence,
        //                         name = if (place.isCustom) place.name else generatePlaceName(improvedCategory, place.latitude, place.longitude)
        //                     )
        //                 )
        //             }
        //         }
        //     }
        // }
    }
    
    /**
     * Phase 2: Infer place category using activity data + time patterns
     * Much more accurate than time patterns alone
     */
    private fun categorizePlaceFromActivityAndTime(
        locations: List<Location>,
        dominantActivity: UserActivity?,
        dominantContext: SemanticContext?,
        activityDistribution: Map<UserActivity, Float>,
        preferences: UserPreferences
    ): PlaceCategory {
        if (locations.isEmpty()) return PlaceCategory.UNKNOWN

        // Priority 1: Use semantic context if available (most reliable)
        dominantContext?.let { context ->
            return when (context) {
                SemanticContext.WORKING -> PlaceCategory.WORK
                SemanticContext.WORKING_OUT -> PlaceCategory.GYM
                SemanticContext.OUTDOOR_EXERCISE -> PlaceCategory.OUTDOOR
                SemanticContext.EATING -> PlaceCategory.RESTAURANT
                SemanticContext.SHOPPING,
                SemanticContext.RUNNING_ERRANDS -> PlaceCategory.SHOPPING
                SemanticContext.SOCIALIZING -> PlaceCategory.SOCIAL
                SemanticContext.ENTERTAINMENT -> PlaceCategory.ENTERTAINMENT
                SemanticContext.RELAXING_HOME -> PlaceCategory.HOME
                else -> PlaceCategory.UNKNOWN
            }
        }

        // Priority 2: Use activity patterns
        val stationaryPercent = activityDistribution[UserActivity.STATIONARY] ?: 0f
        val walkingPercent = activityDistribution[UserActivity.WALKING] ?: 0f

        // High stationary + work hours = likely WORK or HOME
        if (stationaryPercent > 0.8f) {
            val nightHours = locations.count { it.timestamp.hour in 22..23 || it.timestamp.hour in 0..6 }
            val nightPercent = nightHours.toFloat() / locations.size

            if (nightPercent > 0.6f) return PlaceCategory.HOME

            val workHours = locations.count {
                it.timestamp.hour in 9..17 &&
                it.timestamp.dayOfWeek.value in 1..5
            }
            val workPercent = workHours.toFloat() / locations.size

            if (workPercent > 0.5f) return PlaceCategory.WORK
        }

        // Moderate walking + stationary = SHOPPING or RESTAURANT
        if (walkingPercent in 0.2f..0.6f && stationaryPercent in 0.3f..0.7f) {
            val avgDurationMinutes = calculateAverageDuration(locations)

            return when {
                avgDurationMinutes in 30..120 -> PlaceCategory.SHOPPING
                avgDurationMinutes in 45..90 -> PlaceCategory.RESTAURANT
                else -> PlaceCategory.UNKNOWN
            }
        }

        // Fallback to time-based inference
        return PlaceCategory.UNKNOWN
    }

    private fun calculateAverageDuration(locations: List<Location>): Int {
        if (locations.size < 2) return 0

        val sorted = locations.sortedBy { it.timestamp }
        val duration = java.time.Duration.between(
            sorted.first().timestamp,
            sorted.last().timestamp
        )

        return duration.toMinutes().toInt()
    }

    private suspend fun categorizePlace(locations: List<Location>, preferences: UserPreferences): PlaceCategory {
        if (locations.isEmpty()) return PlaceCategory.UNKNOWN

        // FIX: Score-based categorization instead of order-dependent
        val scores = mutableMapOf<PlaceCategory, Float>()

        scores[PlaceCategory.HOME] = calculateHomeScore(locations, preferences)
        scores[PlaceCategory.WORK] = calculateWorkScore(locations, preferences)
        scores[PlaceCategory.EDUCATION] = calculateEducationScore(locations, preferences)
        scores[PlaceCategory.GYM] = calculateGymScore(locations, preferences)
        scores[PlaceCategory.SHOPPING] = calculateShoppingScore(locations, preferences)
        scores[PlaceCategory.RESTAURANT] = calculateRestaurantScore(locations, preferences)

        // Find highest scoring category
        val bestMatch = scores.maxByOrNull { it.value }

        // Only accept if score > threshold, otherwise UNKNOWN
        return if (bestMatch != null && bestMatch.value >= 0.5f) {
            Log.d(TAG, "Category: ${bestMatch.key} (score: ${String.format("%.2f", bestMatch.value)})")
            bestMatch.key
        } else {
            Log.d(TAG, "Category: UNKNOWN (best score: ${String.format("%.2f", bestMatch?.value ?: 0f)})")
            PlaceCategory.UNKNOWN
        }
    }

    private fun calculateHomeScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val nightHours = hourCounts.filterKeys { it >= 22 || it <= 6 }.values.sum()
        val eveningHours = hourCounts.filterKeys { it in 18..21 }.values.sum()
        val totalCount = locations.size

        val nightEveningRatio = (nightHours + eveningHours).toFloat() / totalCount

        // Home gets high score for night/evening activity
        return when {
            nightEveningRatio > preferences.homeNightActivityThreshold -> nightEveningRatio
            nightEveningRatio > 0.4f -> nightEveningRatio * 0.7f  // Possible home
            else -> nightEveningRatio * 0.3f  // Unlikely
        }
    }

    private fun calculateWorkScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val dayOfWeekCounts = locations.groupBy { it.timestamp.dayOfWeek }.mapValues { it.value.size }

        val workHours = hourCounts.filterKeys { it in 9..17 }.values.sum()
        val weekdayCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        }.values.sum()
        val weekendCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }.values.sum()

        val totalCount = locations.size
        val workHoursRatio = workHours.toFloat() / totalCount
        val weekdayRatio = weekdayCount.toFloat() / maxOf(1, weekdayCount + weekendCount)

        // Work requires both work hours AND weekday predominance
        return if (workHoursRatio > preferences.workHoursActivityThreshold && weekdayRatio > 0.7f) {
            (workHoursRatio + weekdayRatio) / 2f
        } else {
            (workHoursRatio * weekdayRatio) * 0.5f
        }
    }

    private fun calculateEducationScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val dayOfWeekCounts = locations.groupBy { it.timestamp.dayOfWeek }.mapValues { it.value.size }

        // Education hours: Morning classes (8-12) and afternoon (13-17)
        val morningClassHours = hourCounts.filterKeys { it in 8..12 }.values.sum()
        val afternoonClassHours = hourCounts.filterKeys { it in 13..17 }.values.sum()
        val classHoursTotal = morningClassHours + afternoonClassHours

        // Strong weekday pattern but can have some weekend activity (study, labs)
        val weekdayCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        }.values.sum()
        val weekendCount = dayOfWeekCounts.filterKeys {
            it in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }.values.sum()

        val totalCount = locations.size
        val classHoursRatio = classHoursTotal.toFloat() / totalCount
        val weekdayRatio = weekdayCount.toFloat() / maxOf(1, weekdayCount + weekendCount)

        // Education differs from work by:
        // 1. Less strict on continuous presence (classes have gaps)
        // 2. Strong morning concentration (8-12)
        // 3. Moderate afternoon presence (not as late as work)
        // 4. Weekday dominant but more flexible (60%+ instead of 70%+)

        val morningRatio = morningClassHours.toFloat() / totalCount
        val afternoonRatio = afternoonClassHours.toFloat() / totalCount

        // Bonus for balanced morning/afternoon pattern (indicates multiple classes)
        val balanceBonus = if (morningRatio > 0.2f && afternoonRatio > 0.2f) 0.1f else 0f

        // Education requires class hours AND weekday pattern (more lenient than work)
        return if (classHoursRatio > 0.4f && weekdayRatio > 0.6f) {
            val baseScore = (classHoursRatio + weekdayRatio) / 2f
            (baseScore + balanceBonus).coerceAtMost(1.0f)
        } else {
            (classHoursRatio * weekdayRatio * 0.6f)  // Lower than work multiplier
        }
    }

    private suspend fun calculateGymScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.size < 5) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val morningWorkout = hourCounts.filterKeys { it in 6..9 }.values.sum()
        val eveningWorkout = hourCounts.filterKeys { it in 17..20 }.values.sum()
        val totalCount = locations.size

        // Requirement 1: Workout time pattern
        val workoutTimeFactor = (morningWorkout + eveningWorkout).toFloat() / totalCount

        // Requirement 2: 2-3x per week frequency (not daily like work)
        val distinctDays = locations.map { it.timestamp.toLocalDate() }.distinct().size
        val daysSpan = if (locations.size > 1) {
            java.time.Duration.between(
                locations.minOf { it.timestamp },
                locations.maxOf { it.timestamp }
            ).toDays()
        } else 1L

        val weeksSpan = maxOf(1, daysSpan / 7)
        val visitsPerWeek = distinctDays.toFloat() / weeksSpan
        val frequencyFactor = when {
            visitsPerWeek in 2f..4f -> 1.0f  // Perfect gym frequency
            visitsPerWeek in 1f..5f -> 0.5f  // Possible
            else -> 0.1f  // Too frequent (work) or too rare
        }

        // Requirement 3: Short-medium duration (30min-2hr typical)
        val avgDuration = calculateAverageStayDuration(locations, preferences)
        val durationFactor = when {
            avgDuration in 30L..120L -> 1.0f  // Perfect gym duration
            avgDuration in 20L..180L -> 0.5f  // Possible
            else -> 0.1f  // Too short or too long
        }

        // Combined score (all factors must be reasonable)
        return workoutTimeFactor * frequencyFactor * durationFactor
    }
    
    private suspend fun calculateShoppingScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val avgDuration = calculateAverageStayDuration(locations, preferences)

        // Shopping has medium duration
        val durationScore = when {
            avgDuration in preferences.shoppingMinDurationMinutes..preferences.shoppingMaxDurationMinutes -> 1.0f
            avgDuration in (preferences.shoppingMinDurationMinutes - 10)..(preferences.shoppingMaxDurationMinutes + 30) -> 0.5f
            else -> 0.1f
        }

        return durationScore * 0.7f  // Lower baseline confidence for shopping
    }
    
    private suspend fun calculateRestaurantScore(locations: List<Location>, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0f

        val hourCounts = locations.groupBy { it.timestamp.hour }.mapValues { it.value.size }
        val mealTimes = hourCounts.filterKeys { it in 11..14 || it in 18..21 }.values.sum()
        val totalCount = locations.size

        // Requirement 1: Meal time pattern
        val mealTimeFactor = mealTimes.toFloat() / totalCount

        // Requirement 2: Medium duration (30min-2hr)
        val avgDuration = calculateAverageStayDuration(locations, preferences)
        val durationFactor = when {
            avgDuration in 30L..120L -> 1.0f
            avgDuration in 20L..180L -> 0.5f
            else -> 0.1f
        }

        // Requirement 3: Irregular visits (not daily routine)
        val distinctDays = locations.map { it.timestamp.toLocalDate() }.distinct().size
        val daysSpan = if (locations.size > 1) {
            java.time.Duration.between(
                locations.minOf { it.timestamp },
                locations.maxOf { it.timestamp }
            ).toDays()
        } else 1L

        val weeksSpan = maxOf(1, daysSpan / 7)
        val visitsPerWeek = distinctDays.toFloat() / weeksSpan
        val irregularityFactor = when {
            visitsPerWeek < 2f -> 1.0f  // Irregular visits - good for restaurant
            visitsPerWeek < 4f -> 0.5f  // Semi-regular
            else -> 0.2f  // Too regular (likely work/home during meal times)
        }

        // Combined score
        return mealTimeFactor * durationFactor * irregularityFactor
    }
    
    private suspend fun calculateAverageStayDuration(locations: List<Location>, preferences: UserPreferences): Long {
        if (locations.size < 2) return 0
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        val durations = mutableListOf<Long>()
        
        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()
            
            if (timeDiff > preferences.sessionBreakTimeMinutes) { // Session break based on user preference
                val sessionDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
                if (sessionDuration > preferences.minVisitDurationMinutes) { // Only count sessions longer than minimum duration
                    durations.add(sessionDuration)
                }
                sessionStart = current.timestamp
            }
            lastLocation = current
        }
        
        // Add final session
        val finalDuration = java.time.Duration.between(sessionStart, lastLocation.timestamp).toMinutes()
        if (finalDuration > preferences.minVisitDurationMinutes) {
            durations.add(finalDuration)
        }
        
        return if (durations.isNotEmpty()) durations.average().toLong() else 0
    }
    
    private suspend fun calculateConfidence(locations: List<Location>, category: PlaceCategory, preferences: UserPreferences): Float {
        if (locations.isEmpty()) return 0.0f

        // ISSUE #3 FIX: Base confidence on data quality, not category prediction
        // Since categories are no longer auto-assigned, confidence reflects
        // how confident we are about the PLACE detection (not categorization)

        val locationCount = locations.size
        val countBonus = minOf(locationCount / 30.0f, 0.3f) // Up to 0.3 bonus for many locations

        // Factor in location accuracy - better accuracy increases confidence
        val avgAccuracy = locations.map { it.accuracy }.average()
        val accuracyBonus = when {
            avgAccuracy <= 10f -> 0.2f  // Very accurate GPS
            avgAccuracy <= 25f -> 0.15f  // Good GPS
            avgAccuracy <= 50f -> 0.1f  // Moderate GPS
            else -> 0.05f // Poor GPS gets minimal bonus
        }

        // Factor in time span - places visited over longer periods are more confident
        val timeSpanHours = if (locations.size > 1) {
            val sortedLocations = locations.sortedBy { it.timestamp }
            java.time.Duration.between(
                sortedLocations.first().timestamp,
                sortedLocations.last().timestamp
            ).toHours()
        } else 0L

        val timeSpanBonus = when {
            timeSpanHours >= 24 * 7 -> 0.15f  // Week or more
            timeSpanHours >= 24 -> 0.1f      // Day or more
            else -> 0.05f
        }

        // Base confidence starts at 0.3 (detected cluster is a real place)
        val baseConfidence = 0.3f

        val finalConfidence = baseConfidence + countBonus + accuracyBonus + timeSpanBonus
        return finalConfidence.coerceIn(0.3f, 0.85f) // Keep between 30% and 85%
    }
    
    private fun generatePlaceName(category: PlaceCategory, lat: Double, lng: Double): String {
        val locationHash = ((lat + lng) * 1000).toInt().toString().takeLast(3)
        
        return when (category) {
            PlaceCategory.HOME -> "Home"
            PlaceCategory.WORK -> "Work"
            PlaceCategory.GYM -> "Gym $locationHash"
            PlaceCategory.RESTAURANT -> "Restaurant $locationHash"
            PlaceCategory.SHOPPING -> "Store $locationHash"
            else -> "Place $locationHash"
        }
    }
    
    private fun calculateOptimalRadius(cluster: List<Pair<Double, Double>>): Double {
        if (cluster.size < 2) return 50.0
        
        val distances = mutableListOf<Double>()
        val center = cluster.map { it.first }.average() to cluster.map { it.second }.average()
        
        cluster.forEach { point ->
            val distance = LocationUtils.calculateDistance(
                center.first, center.second,
                point.first, point.second
            )
            distances.add(distance)
        }
        
        if (distances.isEmpty()) return 50.0
        
        // Use 90th percentile as radius to include most points, but handle edge cases better
        distances.sort()
        val index = maxOf(0, minOf(distances.size - 1, (distances.size * 0.9).toInt()))
        val radius = distances[index]
        
        // Ensure reasonable radius bounds: minimum 25m, maximum 200m
        return radius.coerceIn(25.0, 200.0)
    }

    /**
     * Check if a cluster overlaps significantly with an existing place
     * This prevents creating duplicate places for the same building
     *
     * @param cluster List of (lat, lng) coordinates in the cluster
     * @param existingPlace Existing place to check overlap against
     * @return true if >50% of cluster points fall within existing place's radius
     */
    private fun isClusterOverlappingWithPlace(
        cluster: List<Pair<Double, Double>>,
        existingPlace: Place
    ): Boolean {
        if (cluster.isEmpty()) return false

        // Count how many cluster points fall within existing place's radius
        val pointsInside = cluster.count { (lat, lng) ->
            val distance = LocationUtils.calculateDistance(
                existingPlace.latitude, existingPlace.longitude,
                lat, lng
            )
            distance <= existingPlace.radius
        }

        // If more than 50% of points are inside existing place, consider it overlapping
        val overlapRatio = pointsInside.toFloat() / cluster.size
        return overlapRatio > 0.5f
    }

    /**
     * Week 3: Handle place review decision and creation
     * Decides if place should be auto-accepted or needs review
     */
    private suspend fun handlePlaceReview(
        place: Place,
        locationCount: Int,
        visitCount: Int,
        preferences: UserPreferences
    ) {
        // Get OSM suggested category if available (from enrichment)
        val osmSuggestedCategory = place.osmSuggestedCategory

        // Make auto-accept decision
        val decision = autoAcceptDecisionUseCase.shouldAutoAccept(
            detectedPlace = place,
            confidence = place.confidence,
            visitCount = visitCount,
            osmSuggestedCategory = osmSuggestedCategory
        )

        when (decision) {
            is AutoAcceptDecision.AutoAccept -> {
                // Auto-accepted - create approved review record for tracking
                Log.d(TAG, "Place ${place.name} AUTO-ACCEPTED with status ${decision.status}")

                placeReviewUseCases.createPlaceReview(
                    place = place,
                    confidence = place.confidence,
                    locationCount = locationCount,
                    visitCount = visitCount,
                    reviewType = ReviewType.NEW_PLACE,
                    priority = ReviewPriority.LOW,
                    osmSuggestedName = place.osmSuggestedName,
                    osmSuggestedCategory = osmSuggestedCategory,
                    osmPlaceType = place.osmPlaceType
                ).onSuccess { reviewId ->
                    // Immediately approve it
                    placeReviewUseCases.approvePlace(reviewId)
                    Log.d(TAG, "Auto-accepted place review ID: $reviewId")
                }
            }

            is AutoAcceptDecision.NeedsReview -> {
                // Needs user review - create pending review
                Log.d(TAG, "Place ${place.name} NEEDS REVIEW - Priority: ${decision.priority}, Type: ${decision.reviewType}")

                placeReviewUseCases.createPlaceReview(
                    place = place,
                    confidence = place.confidence,
                    locationCount = locationCount,
                    visitCount = visitCount,
                    reviewType = decision.reviewType,
                    priority = decision.priority,
                    osmSuggestedName = place.osmSuggestedName,
                    osmSuggestedCategory = osmSuggestedCategory,
                    osmPlaceType = place.osmPlaceType
                ).onSuccess { reviewId ->
                    Log.d(TAG, "Created pending place review ID: $reviewId")
                }
            }

            is AutoAcceptDecision.Reject -> {
                // Category disabled - reject and delete place
                Log.d(TAG, "Place ${place.name} REJECTED - Reason: ${decision.reason}")

                placeReviewUseCases.createPlaceReview(
                    place = place,
                    confidence = place.confidence,
                    locationCount = locationCount,
                    visitCount = visitCount,
                    reviewType = ReviewType.CATEGORY_UNCERTAIN,
                    priority = ReviewPriority.LOW,
                    osmSuggestedName = place.osmSuggestedName,
                    osmSuggestedCategory = osmSuggestedCategory,
                    osmPlaceType = place.osmPlaceType
                ).onSuccess { reviewId ->
                    // Immediately reject it
                    placeReviewUseCases.rejectPlace(reviewId, decision.reason)
                    Log.d(TAG, "Auto-rejected place review ID: $reviewId, place deleted")
                }
            }
        }
    }

    private suspend fun createInitialVisits(placeId: Long, locations: List<Location>, preferences: UserPreferences): Int {
        if (locations.isEmpty()) return 0
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        val visits = mutableListOf<Visit>()
        
        var sessionStart = sortedLocations.first().timestamp
        var lastLocation = sortedLocations.first()
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val timeDiff = java.time.Duration.between(lastLocation.timestamp, current.timestamp).toMinutes()
            
            if (timeDiff > preferences.sessionBreakTimeMinutes) { // Session break based on user preference
                val visit = Visit(
                    placeId = placeId,
                    entryTime = sessionStart,
                    exitTime = lastLocation.timestamp
                )
                visits.add(visit)
                sessionStart = current.timestamp
            }
            lastLocation = current
        }
        
        // Add final visit
        val finalVisit = Visit(
            placeId = placeId,
            entryTime = sessionStart,
            exitTime = lastLocation.timestamp
        )
        visits.add(finalVisit)
        
        // Insert visits with minimum duration filter based on user preference
        val filteredVisits = visits.filter {
            java.time.Duration.between(it.entryTime, it.exitTime).toMinutes() >= preferences.minVisitDurationMinutes
        }

        filteredVisits.forEach { visit ->
            visitRepository.insertVisit(visit)
        }

        return filteredVisits.size
    }
    
    /**
     * Filter locations by GPS accuracy and remove outliers
     */
    private fun filterLocationsByQuality(locations: List<Location>, preferences: UserPreferences): List<Location> {
        if (locations.isEmpty()) return emptyList()

        Log.d(TAG, "Filtering ${locations.size} locations with user preferences: " +
            "maxAccuracy=${preferences.maxGpsAccuracyMeters}m, " +
            "maxSpeed=${preferences.maxSpeedKmh}km/h, " +
            "minTimeGap=${preferences.minTimeBetweenUpdatesSeconds}s")

        val maxAccuracyMeters = preferences.maxGpsAccuracyMeters
        val maxSpeedKmh = preferences.maxSpeedKmh

        // Phase 2: Filter out locations captured while moving (prevents false places while driving)
        val stationaryLocations = locations.filter { location ->
            location.userActivity !in setOf(UserActivity.DRIVING, UserActivity.CYCLING) ||
            location.activityConfidence < 0.75f
        }

        val movingFiltered = locations.size - stationaryLocations.size
        if (movingFiltered > 0) {
            Log.i(TAG, "Phase 2: Filtered out $movingFiltered moving locations (DRIVING/CYCLING with confidence >75%)")
        }

        // First filter by accuracy
        val accurateLocations = stationaryLocations.filter { location ->
            location.accuracy <= maxAccuracyMeters
        }
        
        if (accurateLocations.size < 2) return accurateLocations
        
        // Sort by timestamp for sequential processing
        val sortedLocations = accurateLocations.sortedBy { it.timestamp }
        val filteredLocations = mutableListOf<Location>()
        
        filteredLocations.add(sortedLocations.first())
        
        for (i in 1 until sortedLocations.size) {
            val current = sortedLocations[i]
            val previous = sortedLocations[i-1]  // FIX: Compare with actual previous in sorted list

            // Calculate time difference in seconds
            val timeDiffSeconds = java.time.Duration.between(previous.timestamp, current.timestamp).seconds
            
            if (timeDiffSeconds > 0) {
                // Calculate distance and speed
                val distanceMeters = LocationUtils.calculateDistance(
                    previous.latitude, previous.longitude,
                    current.latitude, current.longitude
                )
                
                val speedMps = distanceMeters / timeDiffSeconds // meters per second
                val speedKmh = speedMps * 3.6 // Convert m/s to km/h
                
                // Filter out locations with impossible speeds (likely GPS errors)
                if (speedKmh <= maxSpeedKmh) {
                    // Also filter out locations that are too close in time and space (GPS jitter)
                    val minMovement = 5.0 // meters (keep this fixed as it's for GPS jitter)
                    val minTimeGap = preferences.minTimeBetweenUpdatesSeconds // Use user preference
                    
                    if (distanceMeters >= minMovement || timeDiffSeconds >= minTimeGap) {
                        filteredLocations.add(current)
                    }
                } else {
                    Log.d(TAG, "Location filtered out: impossible speed ${speedKmh}km/h > ${maxSpeedKmh}km/h")
                }
            }
        }
        
        return filteredLocations
    }
}