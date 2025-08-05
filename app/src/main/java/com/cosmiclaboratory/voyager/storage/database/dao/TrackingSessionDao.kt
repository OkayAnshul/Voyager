package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.TrackingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingSessionDao {
    @Insert
    suspend fun insert(session: TrackingSessionEntity): Long

    @Update
    suspend fun update(session: TrackingSessionEntity)

    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :id")
    suspend fun getById(id: Long): TrackingSessionEntity?

    @Query("SELECT * FROM tracking_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): TrackingSessionEntity?

    @Query("SELECT * FROM tracking_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<TrackingSessionEntity?>

    @Query("SELECT * FROM tracking_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<TrackingSessionEntity>

    @Query("SELECT * FROM tracking_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<TrackingSessionEntity>
}
