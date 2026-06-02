package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.TripVehicleAssignmentEntity

@Dao
interface TripVehicleAssignmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: TripVehicleAssignmentEntity)

    @Query("SELECT * FROM trip_vehicle_assignments WHERE segmentId = :segmentId AND deletedAt IS NULL")
    suspend fun getBySegment(segmentId: Long): TripVehicleAssignmentEntity?

    @Query("SELECT * FROM trip_vehicle_assignments WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY assignedAt DESC")
    suspend fun getByVehicle(vehicleId: Long): List<TripVehicleAssignmentEntity>

    @Query(
        """
        SELECT * FROM trip_vehicle_assignments
        WHERE vehicleId = :vehicleId AND deletedAt IS NULL
        AND assignedAt BETWEEN :startMs AND :endMs
        """
    )
    suspend fun getByVehicleInRange(vehicleId: Long, startMs: Long, endMs: Long): List<TripVehicleAssignmentEntity>

    @Query("DELETE FROM trip_vehicle_assignments WHERE segmentId = :segmentId")
    suspend fun delete(segmentId: Long)
}
