package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.VehicleServiceLogEntity

@Dao
interface VehicleServiceLogDao {

    @Insert
    suspend fun insert(entry: VehicleServiceLogEntity): Long

    @Update
    suspend fun update(entry: VehicleServiceLogEntity)

    @Query("SELECT * FROM vehicle_service_log WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY serviceAt DESC")
    suspend fun getByVehicle(vehicleId: Long): List<VehicleServiceLogEntity>

    @Query("SELECT * FROM vehicle_service_log WHERE vehicleId = :vehicleId AND serviceType = :type AND deletedAt IS NULL ORDER BY serviceAt DESC LIMIT 1")
    suspend fun getMostRecentOfType(vehicleId: Long, type: String): VehicleServiceLogEntity?

    @Query("UPDATE vehicle_service_log SET deletedAt = :now WHERE serviceId = :id")
    suspend fun softDelete(id: Long, now: Long)
}
