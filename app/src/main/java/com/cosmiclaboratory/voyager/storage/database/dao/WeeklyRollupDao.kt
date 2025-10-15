package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.WeeklyRollupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyRollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rollup: WeeklyRollupEntity)

    @Query("SELECT * FROM weekly_rollups WHERE weekKey = :weekKey")
    suspend fun getByWeekKey(weekKey: String): WeeklyRollupEntity?

    @Query("SELECT * FROM weekly_rollups ORDER BY weekKey DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<WeeklyRollupEntity>

    @Query("SELECT * FROM weekly_rollups ORDER BY weekKey DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WeeklyRollupEntity>>

    @Query("DELETE FROM weekly_rollups WHERE weekKey < :cutoffWeek")
    suspend fun deleteOlderThan(cutoffWeek: String): Int
}
