package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.VisitDao
import com.cosmiclaboratory.voyager.data.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.domain.model.VisitSummary
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitRepositoryImpl @Inject constructor(
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao
) : VisitRepository {
    
    override fun getAllVisits(): Flow<List<Visit>> {
        return visitDao.getAllVisits().map { it.toDomainModels() }
    }
    
    override fun getVisitsForPlace(placeId: Long): Flow<List<Visit>> {
        return visitDao.getVisitsForPlace(placeId).map { it.toDomainModels() }
    }
    
    override fun getVisitsBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flow<List<Visit>> {
        return visitDao.getVisitsBetween(startTime, endTime).map { it.toDomainModels() }
    }
    
    override suspend fun getActiveVisits(): List<Visit> {
        return visitDao.getActiveVisits().toDomainModels()
    }
    
    override suspend fun getLastVisit(): Visit? {
        return visitDao.getLastVisit()?.toDomainModel()
    }
    
    override suspend fun getLastVisitForPlace(placeId: Long): Visit? {
        return visitDao.getLastVisitForPlace(placeId)?.toDomainModel()
    }
    
    override suspend fun insertVisit(visit: Visit): Long {
        return visitDao.insertVisit(visit.toEntity())
    }
    
    override suspend fun updateVisit(visit: Visit) {
        visitDao.updateVisit(visit.toEntity())
    }
    
    override suspend fun deleteVisit(visit: Visit) {
        visitDao.deleteVisit(visit.toEntity())
    }
    
    override suspend fun endVisit(id: Long, exitTime: LocalDateTime) {
        // Get the current visit to calculate duration
        val visits = visitDao.getAllVisits().first()
        val visitEntity = visits.find { it.id == id }
        
        if (visitEntity != null) {
            val duration = java.time.Duration.between(visitEntity.entryTime, exitTime).toMillis()
            visitDao.endVisit(id, exitTime, duration)
        }
    }
    
    override suspend fun deleteVisitsBefore(beforeDate: LocalDateTime): Int {
        return visitDao.deleteVisitsBefore(beforeDate)
    }
    
    override suspend fun getTotalTimeAtPlace(
        placeId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Long {
        return visitDao.getTotalTimeAtPlace(placeId, startTime, endTime) ?: 0L
    }
    
    override suspend fun getVisitCountForPlace(
        placeId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Int {
        return visitDao.getVisitCountForPlace(placeId, startTime, endTime)
    }
    
    override suspend fun getVisitSummaries(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<VisitSummary> {
        val visitEntities = visitDao.getVisitsBetween(startTime, endTime).first()
        val placeEntities = placeDao.getAllPlaces().first()
        val placesMap = placeEntities.associateBy { it.id }
        
        return visitEntities.mapNotNull { visitEntity ->
            val placeEntity = placesMap[visitEntity.placeId]
            if (placeEntity != null && visitEntity.exitTime != null) {
                VisitSummary(
                    place = placeEntity.toDomainModel(),
                    visitCount = 1,
                    totalDuration = visitEntity.duration,
                    averageDuration = visitEntity.duration,
                    lastVisit = visitEntity.entryTime
                )
            } else null
        }.groupBy { it.place.id }.map { (_, summaries) ->
            val firstSummary = summaries.first()
            VisitSummary(
                place = firstSummary.place,
                visitCount = summaries.size,
                totalDuration = summaries.sumOf { it.totalDuration },
                averageDuration = summaries.map { it.totalDuration }.average().toLong(),
                lastVisit = summaries.mapNotNull { it.lastVisit }.maxOrNull()
            )
        }
    }
}