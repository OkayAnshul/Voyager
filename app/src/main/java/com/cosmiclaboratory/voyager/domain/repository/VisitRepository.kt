package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.domain.model.VisitSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface VisitRepository {
    
    fun getAllVisits(): Flow<List<Visit>>
    
    fun getVisitsForPlace(placeId: Long): Flow<List<Visit>>
    
    fun getVisitsBetween(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<Visit>>
    
    suspend fun getActiveVisits(): List<Visit>
    
    suspend fun getLastVisit(): Visit?
    
    suspend fun getLastVisitForPlace(placeId: Long): Visit?
    
    suspend fun insertVisit(visit: Visit): Long
    
    suspend fun updateVisit(visit: Visit)
    
    suspend fun deleteVisit(visit: Visit)
    
    suspend fun endVisit(id: Long, exitTime: LocalDateTime)
    
    suspend fun deleteVisitsBefore(beforeDate: LocalDateTime): Int
    
    suspend fun getTotalTimeAtPlace(placeId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Long
    
    suspend fun getVisitCountForPlace(placeId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Int
    
    suspend fun getVisitSummaries(startTime: LocalDateTime, endTime: LocalDateTime): List<VisitSummary>
    
    // New methods for visit management
    suspend fun completeActiveVisits(exitTime: LocalDateTime)
    
    suspend fun startVisit(placeId: Long, entryTime: LocalDateTime): Long
    
    suspend fun getCurrentVisit(): Visit?
}