package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.MileageSummaryEntity

@Dao
interface MileageSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: MileageSummaryEntity)

    @Query("SELECT * FROM mileage_summary WHERE vehicleId = :vehicleId AND period = :period AND periodKey = :periodKey LIMIT 1")
    suspend fun get(vehicleId: Long, period: String, periodKey: String): MileageSummaryEntity?

    @Query("SELECT * FROM mileage_summary WHERE vehicleId = :vehicleId AND period = :period ORDER BY periodKey DESC LIMIT :limit")
    suspend fun recent(vehicleId: Long, period: String, limit: Int): List<MileageSummaryEntity>

    @Query("DELETE FROM mileage_summary WHERE vehicleId = :vehicleId AND period = :period AND periodKey = :periodKey")
    suspend fun delete(vehicleId: Long, period: String, periodKey: String)
}
