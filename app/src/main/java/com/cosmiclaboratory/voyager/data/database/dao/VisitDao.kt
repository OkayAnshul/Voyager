package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.VisitEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface VisitDao {
    
    @Query("SELECT * FROM visits ORDER BY entryTime DESC")
    fun getAllVisits(): Flow<List<VisitEntity>>
    
    @Query("SELECT * FROM visits WHERE placeId = :placeId ORDER BY entryTime DESC")
    fun getVisitsForPlace(placeId: Long): Flow<List<VisitEntity>>
    
    @Query("SELECT * FROM visits WHERE entryTime BETWEEN :startTime AND :endTime ORDER BY entryTime")
    fun getVisitsBetween(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<VisitEntity>>
    
    @Query("SELECT * FROM visits WHERE exitTime IS NULL ORDER BY entryTime DESC")
    suspend fun getActiveVisits(): List<VisitEntity>
    
    @Query("SELECT * FROM visits ORDER BY entryTime DESC LIMIT 1")
    suspend fun getLastVisit(): VisitEntity?
    
    @Query("SELECT * FROM visits WHERE placeId = :placeId ORDER BY entryTime DESC LIMIT 1")
    suspend fun getLastVisitForPlace(placeId: Long): VisitEntity?
    
    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getVisitById(id: Long): VisitEntity?
    
    @Insert
    suspend fun insertVisit(visit: VisitEntity): Long
    
    @Update
    suspend fun updateVisit(visit: VisitEntity)
    
    @Delete
    suspend fun deleteVisit(visit: VisitEntity)
    
    @Query("UPDATE visits SET exitTime = :exitTime, duration = :duration WHERE id = :id")
    suspend fun endVisit(id: Long, exitTime: LocalDateTime, duration: Long)
    
    @Query("DELETE FROM visits WHERE entryTime < :beforeDate")
    suspend fun deleteVisitsBefore(beforeDate: LocalDateTime): Int
    
    @Query("""
        SELECT SUM(duration) FROM visits 
        WHERE placeId = :placeId 
        AND entryTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getTotalTimeAtPlace(placeId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Long?
    
    @Query("""
        SELECT COUNT(*) FROM visits 
        WHERE placeId = :placeId 
        AND entryTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getVisitCountForPlace(placeId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Int
}