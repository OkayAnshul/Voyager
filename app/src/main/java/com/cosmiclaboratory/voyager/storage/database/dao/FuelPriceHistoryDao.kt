package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.FuelPriceHistoryEntity

@Dao
interface FuelPriceHistoryDao {

    @Insert
    suspend fun insert(price: FuelPriceHistoryEntity): Long

    @Query("SELECT * FROM fuel_price_history WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY effectiveFrom DESC")
    suspend fun getAllForVehicle(vehicleId: Long): List<FuelPriceHistoryEntity>

    /** Most-recent price whose effectiveFrom is at or before [atMs]. */
    @Query(
        """
        SELECT * FROM fuel_price_history
        WHERE vehicleId = :vehicleId AND deletedAt IS NULL AND effectiveFrom <= :atMs
        ORDER BY effectiveFrom DESC
        LIMIT 1
        """
    )
    suspend fun priceAt(vehicleId: Long, atMs: Long): FuelPriceHistoryEntity?
}
