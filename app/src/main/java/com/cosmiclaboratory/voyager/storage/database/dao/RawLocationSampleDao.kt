package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawLocationSampleDao {
    @Insert
    suspend fun insert(sample: RawLocationSampleEntity): Long

    @Insert
    suspend fun insertAll(samples: List<RawLocationSampleEntity>)

    @Query("SELECT * FROM raw_location_samples WHERE sampleId = :id")
    suspend fun getById(id: Long): RawLocationSampleEntity?

    @Query("SELECT * FROM raw_location_samples WHERE trackingSessionId = :sessionId ORDER BY capturedAt ASC")
    suspend fun getBySession(sessionId: Long): List<RawLocationSampleEntity>

    @Query("SELECT * FROM raw_location_samples WHERE capturedAt BETWEEN :startMs AND :endMs ORDER BY capturedAt ASC")
    suspend fun getByTimeRange(startMs: Long, endMs: Long): List<RawLocationSampleEntity>

    @Query("SELECT * FROM raw_location_samples WHERE geohash LIKE :prefix || '%' AND capturedAt BETWEEN :startMs AND :endMs ORDER BY capturedAt ASC")
    suspend fun getByGeohashPrefix(prefix: String, startMs: Long, endMs: Long): List<RawLocationSampleEntity>

    @Query("SELECT * FROM raw_location_samples ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatest(): RawLocationSampleEntity?

    @Query("SELECT * FROM raw_location_samples ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatest(): Flow<RawLocationSampleEntity?>

    @Query("SELECT COUNT(*) FROM raw_location_samples WHERE trackingSessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    @Query("DELETE FROM raw_location_samples WHERE capturedAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM raw_location_samples")
    suspend fun count(): Int
}
