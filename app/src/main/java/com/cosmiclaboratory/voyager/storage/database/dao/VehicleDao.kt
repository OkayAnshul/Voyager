package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE vehicleId = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE isDefault = 1 AND deletedAt IS NULL LIMIT 1")
    suspend fun getDefault(): VehicleEntity?

    @Query("UPDATE vehicles SET isDefault = (vehicleId = :targetId)")
    suspend fun setDefault(targetId: Long)

    @Query("UPDATE vehicles SET deletedAt = :now WHERE vehicleId = :id")
    suspend fun softDelete(id: Long, now: Long)
}
