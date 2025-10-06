package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthLogDao {
    @Insert
    suspend fun insert(log: HealthLogEntity): Long

    @Query("SELECT * FROM health_log ORDER BY eventAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<HealthLogEntity>

    @Query("SELECT * FROM health_log WHERE eventType = :eventType ORDER BY eventAt DESC LIMIT :limit")
    suspend fun getByType(eventType: String, limit: Int): List<HealthLogEntity>

    @Query("SELECT * FROM health_log WHERE acknowledged = 0 ORDER BY eventAt DESC")
    fun observeUnacknowledged(): Flow<List<HealthLogEntity>>

    @Query("UPDATE health_log SET acknowledged = 1 WHERE logId = :logId")
    suspend fun acknowledge(logId: Long)

    @Query("DELETE FROM health_log WHERE eventAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
