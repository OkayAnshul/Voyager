package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.RawActivitySampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawActivitySampleDao {
    @Insert
    suspend fun insert(sample: RawActivitySampleEntity): Long

    @Insert
    suspend fun insertAll(samples: List<RawActivitySampleEntity>)

    @Query("SELECT * FROM raw_activity_samples WHERE trackingSessionId = :sessionId ORDER BY capturedAt ASC")
    suspend fun getBySession(sessionId: Long): List<RawActivitySampleEntity>

    @Query("SELECT * FROM raw_activity_samples WHERE capturedAt BETWEEN :startMs AND :endMs ORDER BY capturedAt ASC")
    suspend fun getByTimeRange(startMs: Long, endMs: Long): List<RawActivitySampleEntity>

    @Query("SELECT * FROM raw_activity_samples ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatest(): RawActivitySampleEntity?

    @Query("SELECT * FROM raw_activity_samples ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatest(): Flow<RawActivitySampleEntity?>

    @Query("DELETE FROM raw_activity_samples WHERE capturedAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
