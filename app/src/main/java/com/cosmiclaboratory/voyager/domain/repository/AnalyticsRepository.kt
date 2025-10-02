package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface AnalyticsRepository {
    
    suspend fun getTimeAnalytics(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): TimeAnalytics
    
    suspend fun getDayAnalytics(date: LocalDate): DayAnalytics
    
    suspend fun getWeekAnalytics(weekStart: LocalDate): List<DayAnalytics>
    
    suspend fun getMonthAnalytics(year: Int, month: Int): List<DayAnalytics>
    
    fun getPlaceRankings(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        limit: Int = 10
    ): Flow<List<PlaceRanking>>
    
    suspend fun getPeakActivityHours(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<HourActivity>
    
    suspend fun detectMovementPatterns(): List<MovementPattern>
    
    suspend fun getTotalDistanceTraveled(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Double
    
    suspend fun getAverageStayDuration(placeId: Long): Long
    
    suspend fun getMostActiveHour(): Int
    
    suspend fun getTimeSpentByCategory(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Map<PlaceCategory, Long>
}