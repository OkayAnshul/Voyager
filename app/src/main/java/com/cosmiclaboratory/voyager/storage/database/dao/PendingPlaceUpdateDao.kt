package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.PendingPlaceUpdateEntity

@Dao
interface PendingPlaceUpdateDao {
    @Insert
    suspend fun insert(update: PendingPlaceUpdateEntity): Long

    @Query("SELECT * FROM pending_place_updates WHERE consumedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getUnconsumed(): List<PendingPlaceUpdateEntity>

    @Query("UPDATE pending_place_updates SET consumedAt = :consumedAt WHERE updateId = :updateId")
    suspend fun markConsumed(updateId: Long, consumedAt: Long)

    @Query("DELETE FROM pending_place_updates WHERE consumedAt IS NOT NULL AND consumedAt < :cutoffMs")
    suspend fun deleteConsumedOlderThan(cutoffMs: Long): Int
}
