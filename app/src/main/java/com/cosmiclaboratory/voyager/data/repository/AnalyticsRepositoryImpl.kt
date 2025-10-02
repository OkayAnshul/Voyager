package com.cosmiclaboratory.voyager.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.cosmiclaboratory.voyager.data.database.dao.*
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao
) : AnalyticsRepository {
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getTimeAnalytics(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): TimeAnalytics {
        val visits = visitDao.getVisitsBetween(startTime, endTime).first()
        val places = placeDao.getAllPlaces().first().toDomainModels()
        val locations = locationDao.getLocationsSince(startTime).filter { it.timestamp.isBefore(endTime) }.toDomainModels()
        
        // Calculate time by category and place
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
        
        // Calculate place rankings
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
        val peakActivityHours = calculatePeakActivityHours(locations)
        
        // Calculate average daily movement
        val days = ChronoUnit.DAYS.between(startTime, endTime)
        val averageDailyMovement = if (days > 0) locations.size / days else 0L
        
        return TimeAnalytics(
            totalTimeTracked = timeByCategory.values.sum(),
            timeByCategory = timeByCategory,
            timeByPlace = timeByPlace,
            averageDailyMovement = averageDailyMovement,
            mostVisitedPlaces = placeRankings,
            peakActivityHours = peakActivityHours
        )
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getDayAnalytics(date: LocalDate): DayAnalytics {
        val startTime = date.atStartOfDay()
        val endTime = date.plusDays(1).atStartOfDay()
        
        val visits = visitDao.getVisitsBetween(startTime, endTime).first()
        val places = placeDao.getAllPlaces().first().toDomainModels()
        val locations = locationDao.getLocationsSince(startTime).filter { it.timestamp.isBefore(endTime) }.toDomainModels()
        
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
        
        val longestStay = visits.maxByOrNull { it.duration }?.toDomainModel()
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
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getWeekAnalytics(weekStart: LocalDate): List<DayAnalytics> {
        return (0..6).map { dayOffset ->
            getDayAnalytics(weekStart.plusDays(dayOffset.toLong()))
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getMonthAnalytics(year: Int, month: Int): List<DayAnalytics> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1)
        
        val result = mutableListOf<DayAnalytics>()
        var currentDate = startDate
        while (currentDate.isBefore(endDate)) {
            result.add(getDayAnalytics(currentDate))
            currentDate = currentDate.plusDays(1)
        }
        return result
    }
    
    override fun getPlaceRankings(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        limit: Int
    ): Flow<List<PlaceRanking>> = flow {
        val visits = visitDao.getVisitsBetween(startTime, endTime).first()
        val places = placeDao.getAllPlaces().first().toDomainModels()
        
        val placeStats = visits.groupBy { it.placeId }
            .mapNotNull { (placeId, placeVisits) ->
                val place = places.find { it.id == placeId }
                if (place != null) {
                    val totalTime = placeVisits.filter { it.exitTime != null }.sumOf { it.duration }
                    PlaceRanking(
                        place = place,
                        totalTime = totalTime,
                        visitCount = placeVisits.size,
                        rank = 0 // Will be set after sorting
                    )
                } else null
            }
            .sortedByDescending { it.totalTime }
            .take(limit)
            .mapIndexed { index, ranking ->
                ranking.copy(rank = index + 1)
            }
        
        emit(placeStats)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getPeakActivityHours(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<HourActivity> {
        val locations = locationDao.getLocationsSince(startTime).filter { it.timestamp.isBefore(endTime) }.toDomainModels()
        return calculatePeakActivityHours(locations)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun detectMovementPatterns(): List<MovementPattern> {
        val patterns = mutableListOf<MovementPattern>()
        val places = placeDao.getAllPlaces().first().toDomainModels()
        val homePlace = places.find { it.category == PlaceCategory.HOME }
        val workPlace = places.find { it.category == PlaceCategory.WORK }
        
        // Detect commute pattern if both home and work exist
        if (homePlace != null && workPlace != null) {
            val recentLocations = locationDao.getRecentLocations(1000).first().toDomainModels()
            patterns.add(
                MovementPattern(
                    patternType = PatternType.COMMUTE,
                    name = "Home ↔ Work Commute",
                    confidence = 0.8f,
                    frequency = 5,
                    locations = recentLocations.take(10),
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
            )
        }
        
        return patterns
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getTotalDistanceTraveled(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Double {
        val locations = locationDao.getLocationsSince(startTime).filter { it.timestamp.isBefore(endTime) }.toDomainModels()
        return calculateDistanceTraveled(locations)
    }
    
    override suspend fun getAverageStayDuration(placeId: Long): Long {
        val visits = visitDao.getVisitsForPlace(placeId).first()
        val completedVisits = visits.filter { it.exitTime != null }
        return if (completedVisits.isNotEmpty()) {
            completedVisits.map { it.duration }.average().toLong()
        } else 0L
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getMostActiveHour(): Int {
        val locations = locationDao.getRecentLocations(5000).first().toDomainModels()
        val hourCounts = locations.groupBy { it.timestamp.hour }
            .mapValues { it.value.size }
        return hourCounts.maxByOrNull { it.value }?.key ?: 12
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getTimeSpentByCategory(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Map<PlaceCategory, Long> {
        val visits = visitDao.getVisitsBetween(startTime, endTime).first()
        val places = placeDao.getAllPlaces().first().toDomainModels()
        
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
        return timeByCategory
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
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
    
    private fun calculateDistanceTraveled(locations: List<Location>): Double {
        if (locations.size < 2) return 0.0
        
        val sortedLocations = locations.sortedBy { it.timestamp }
        var totalDistance = 0.0
        
        for (i in 1 until sortedLocations.size) {
            val prev = sortedLocations[i - 1]
            val current = sortedLocations[i]
            totalDistance += LocationUtils.calculateDistance(
                prev.latitude, prev.longitude,
                current.latitude, current.longitude
            )
        }
        
        return totalDistance
    }
}