package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.RawStepSampleEntity

@Dao
interface RawStepSampleDao {
    @Insert
    suspend fun insert(sample: RawStepSampleEntity): Long

    @Insert
    suspend fun insertAll(samples: List<RawStepSampleEntity>)

    @Query("SELECT * FROM raw_step_samples WHERE periodStart >= :startMs AND periodEnd <= :endMs ORDER BY periodStart ASC")
    suspend fun getByTimeRange(startMs: Long, endMs: Long): List<RawStepSampleEntity>

    @Query("SELECT SUM(stepCount) FROM raw_step_samples WHERE periodStart >= :startMs AND periodEnd <= :endMs")
    suspend fun sumStepsByTimeRange(startMs: Long, endMs: Long): Int?

    /** Reactive version — re-emits whenever step samples table changes in the given range. */
    @Query("SELECT SUM(stepCount) FROM raw_step_samples WHERE periodStart >= :startMs AND periodEnd <= :endMs")
    fun observeSumStepsByTimeRange(startMs: Long, endMs: Long): kotlinx.coroutines.flow.Flow<Int?>

    /** Reactive version — re-emits the list whenever step samples change in range. */
    @Query("SELECT * FROM raw_step_samples WHERE periodStart >= :startMs AND periodEnd <= :endMs ORDER BY periodStart ASC")
    fun observeByTimeRange(startMs: Long, endMs: Long): kotlinx.coroutines.flow.Flow<List<RawStepSampleEntity>>

    @Query("DELETE FROM raw_step_samples WHERE periodEnd < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
