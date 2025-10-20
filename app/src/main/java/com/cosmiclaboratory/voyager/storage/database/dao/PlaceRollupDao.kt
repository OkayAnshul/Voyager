package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceRollupEntity

@Dao
interface PlaceRollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rollup: PlaceRollupEntity)

    @Query("SELECT * FROM place_rollups WHERE placeId = :placeId")
    suspend fun getByPlaceId(placeId: Long): PlaceRollupEntity?

    @Query("SELECT * FROM place_rollups ORDER BY totalVisitCount DESC LIMIT :limit")
    suspend fun getTopByVisitCount(limit: Int): List<PlaceRollupEntity>
}
