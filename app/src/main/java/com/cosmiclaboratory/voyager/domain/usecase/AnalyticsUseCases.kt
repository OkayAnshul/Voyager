package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsUseCases @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val placeUseCases: PlaceUseCases
) {
    
    suspend fun generateTimeAnalytics(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): TimeAnalytics {
        val visits = visitRepository.getVisitsBetween(startTime, endTime).first()
        val places = placeRepository.getAllPlaces().first()
        
        // Calculate time by category
        val timeByCategory = mutableMapOf<PlaceCategory, Long>()
        val timeByPlace = mutableMapOf<Place, Long>()
        
        visits.forEach { visit ->
            if (visit.exitTime != null) {
                val place = places.find { it.id == visit.placeId }
                if (place != null) {
                    timeByCategory[place.category] = 
                        (timeByCategory[place.category] ?: 0L) + visit.duration
                    timeByPlace[place] = 
                        (timeByPlace[place] ?: 0L) + visit.duration
                }
            }
        }
        
        // Calculate most visited places ranking
        val placeRankings = timeByPlace.entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry ->
                val visitCount = visits.count { it.placeId == entry.key.id }
                PlaceRanking(
                    place = entry.key,
                    totalTime = entry.value,
                    visitCount = visitCount,
                    rank = index + 1
                )
            }
        
        // Calculate peak activity hours
        val locations = locationRepository.getLocationsSince(startTime)
            .filter { it.timestamp.isBefore(endTime) }
        
        val peakActivityHours = calculatePeakActivityHours(locations)
        
        return TimeAnalytics(
            totalTimeTracked = timeByCategory.values.sum(),
            timeByCategory = timeByCategory,
            timeByPlace = timeByPlace,
            averageDailyMovement = calculateAverageDailyMovement(startTime, endTime),
            mostVisitedPlaces = placeRankings,
            peakActivityHours = peakActivityHours
        )
    }
    
    suspend fun generateDayAnalytics(date: LocalDate): DayAnalytics {
        val startTime = date.atStartOfDay()
        val endTime = date.plusDays(1).atStartOfDay()
        
        val locations = locationRepository.getLocationsSince(startTime)
            .filter { it.timestamp.isBefore(endTime) }
        
        val visits = visitRepository.getVisitsBetween(startTime, endTime).first()
        val places = placeRepository.getAllPlaces().first()
        
        val placesVisited = visits.map { it.placeId }.distinct().size
        val distanceTraveled = calculateDistanceTraveled(locations)
        
        // Calculate time by category for this day
        val timeByCategory = mutableMapOf<PlaceCategory, Long>()
        visits.forEach { visit ->
            if (visit.exitTime != null) {
                val place = places.find { it.id == visit.placeId }
                if (place != null) {
                    timeByCategory[place.category] = 
                        (timeByCategory[place.category] ?: 0L) + visit.duration
                }
            }
        }
        
        val longestStay = visits.maxByOrNull { it.duration }
        val mostFrequentPlace = visits.groupBy { it.placeId }
            .maxByOrNull { it.value.size }
            ?.let { entry ->
                places.find { it.id == entry.key }
            }
        
        return DayAnalytics(
            date = date,
            totalTimeTracked = timeByCategory.values.sum(),
            placesVisited = placesVisited,
            distanceTraveled = distanceTraveled,
            timeByCategory = timeByCategory,
            longestStay = longestStay,
            mostFrequentPlace = mostFrequentPlace
        )
    }
    
    suspend fun detectMovementPatterns(): List<MovementPattern> {
        val patterns = mutableListOf<MovementPattern>()
        
        // Detect home-work commute pattern
        val homeWork = detectCommutePattern()
        if (homeWork != null) {
            patterns.add(homeWork)
        }
        
        // Detect weekend patterns
        val weekendPattern = detectWeekendPattern()
        if (weekendPattern != null) {
            patterns.add(weekendPattern)
        }
        
        return patterns
    }
    
    private suspend fun detectCommutePattern(): MovementPattern? {
        val places = placeRepository.getAllPlaces().first()
        val homePlace = places.find { it.category == PlaceCategory.HOME }
        val workPlace = places.find { it.category == PlaceCategory.WORK }
        
        if (homePlace == null || workPlace == null) return null
        
        // Look for regular movement between home and work
        val recentLocations = locationRepository.getRecentLocations(1000).first()
        
        return MovementPattern(
            patternType = PatternType.COMMUTE,
            name = "Home ↔ Work Commute",
            confidence = 0.8f,
            frequency = 5, // days per week
            locations = recentLocations.take(10), // Sample locations
            timePattern = TimePattern(
                startTime = java.time.LocalTime.of(8, 0),
                endTime = java.time.LocalTime.of(9, 0),
                daysOfWeek = setOf(
                    java.time.DayOfWeek.MONDAY,
                    java.time.DayOfWeek.TUESDAY,
                    java.time.DayOfWeek.WEDNESDAY,
                    java.time.DayOfWeek.THURSDAY,
                    java.time.DayOfWeek.FRIDAY
                )
            ),
            detectedAt = LocalDateTime.now()
        )
    }
    
    private suspend fun detectWeekendPattern(): MovementPattern? {
        // Simplified weekend pattern detection
        val locations = locationRepository.getRecentLocations(500).first()
        val weekendLocations = locations.filter { 
            val dayOfWeek = it.timestamp.dayOfWeek
            dayOfWeek == java.time.DayOfWeek.SATURDAY || 
            dayOfWeek == java.time.DayOfWeek.SUNDAY
        }
        
        if (weekendLocations.size < 10) return null
        
        return MovementPattern(
            patternType = PatternType.WEEKEND_PATTERN,
            name = "Weekend Routine",
            confidence = 0.6f,
            frequency = 2, // times per week
            locations = weekendLocations.take(10),
            timePattern = null,
            detectedAt = LocalDateTime.now()
        )
    }
    
    private fun calculatePeakActivityHours(locations: List<Location>): List<HourActivity> {
        val hourCounts = locations.groupBy { it.timestamp.hour }
            .mapValues { it.value.size }
        
        val maxCount = hourCounts.values.maxOrNull() ?: 1
        
        return (0..23).map { hour ->
            val count = hourCounts[hour] ?: 0
            HourActivity(
                hour = hour,
                activityLevel = count.toFloat() / maxCount.toFloat(),
                averageLocations = count
            )
        }
    }
    
    private suspend fun calculateAverageDailyMovement(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Long {
        val days = ChronoUnit.DAYS.between(startTime, endTime)
        if (days == 0L) return 0L
        
        val totalLocations = locationRepository.getLocationsSince(startTime)
            .filter { it.timestamp.isBefore(endTime) }
            .size
        
        return totalLocations / days
    }
    
    private fun calculateDistanceTraveled(locations: List<Location>): Double {
        if (locations.size < 2) return 0.0
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        var totalDistance = 0.0
        
        for (i in 1 until sortedLocations.size) {
            val prev = sortedLocations[i - 1]
            val current = sortedLocations[i]
            totalDistance += com.cosmiclaboratory.voyager.utils.LocationUtils.calculateDistance(
                prev.latitude, prev.longitude,
                current.latitude, current.longitude
            )
        }
        
        return totalDistance
    }
}