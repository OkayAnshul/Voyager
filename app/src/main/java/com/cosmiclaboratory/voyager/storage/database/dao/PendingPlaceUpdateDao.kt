package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.PendingPlaceUpdateEntity

@Dao
interface PendingPlaceUpdateDao {
    @Insert
    suspend fun insert(update: PendingPlaceUpdateEntity): Long

    /**
     * Atomically claims every currently-unconsumed update (H7 fix).
     *
     * The claiming UPDATE runs first inside the transaction, so it takes SQLite's
     * write lock before any row is read back. A concurrent caller's UPDATE blocks
     * until this transaction commits and then matches zero `consumedAt IS NULL`
     * rows — so a row can never be claimed twice. [claimToken] must be unique per
     * call (callers pass a wall-clock timestamp); it is stored in `consumedAt`.
     */
    @Transaction
    suspend fun claimUnconsumed(claimToken: Long): List<PendingPlaceUpdateEntity> {
        markClaimed(claimToken)
        return getByConsumedAt(claimToken)
    }

    @Query("UPDATE pending_place_updates SET consumedAt = :claimToken WHERE consumedAt IS NULL")
    suspend fun markClaimed(claimToken: Long)

    @Query("SELECT * FROM pending_place_updates WHERE consumedAt = :claimToken ORDER BY createdAt ASC")
    suspend fun getByConsumedAt(claimToken: Long): List<PendingPlaceUpdateEntity>

    @Query("DELETE FROM pending_place_updates WHERE consumedAt IS NOT NULL AND consumedAt < :cutoffMs")
    suspend fun deleteConsumedOlderThan(cutoffMs: Long): Int
}
