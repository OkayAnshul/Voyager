package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rollup: DailyRollupEntity)

    @Query("SELECT * FROM daily_rollups WHERE dayKey = :dayKey")
    suspend fun getByDayKey(dayKey: String): DailyRollupEntity?

    @Query("SELECT * FROM daily_rollups WHERE dayKey = :dayKey")
    fun observeByDayKey(dayKey: String): Flow<DailyRollupEntity?>

    @Query("SELECT * FROM daily_rollups WHERE dayKey BETWEEN :startDay AND :endDay ORDER BY dayKey ASC")
    suspend fun getByRange(startDay: String, endDay: String): List<DailyRollupEntity>

    @Query("SELECT * FROM daily_rollups WHERE dayKey BETWEEN :startDay AND :endDay ORDER BY dayKey ASC")
    fun observeByRange(startDay: String, endDay: String): Flow<List<DailyRollupEntity>>

    @Query("SELECT * FROM daily_rollups ORDER BY dayKey DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<DailyRollupEntity>

    @Query("DELETE FROM daily_rollups WHERE dayKey < :cutoffDay")
    suspend fun deleteOlderThan(cutoffDay: String): Int

    @Query("SELECT dayKey FROM daily_rollups WHERE uniquePlacesVisited > 0 ORDER BY dayKey DESC LIMIT 60")
    suspend fun getActiveDayKeys(): List<String>
}
