package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: RouteEntity): Long

    @Update
    suspend fun update(route: RouteEntity)

    @Query("SELECT * FROM routes WHERE routeId = :id")
    suspend fun getById(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE segmentId = :segmentId")
    suspend fun getBySegmentId(segmentId: Long): RouteEntity?

    @Delete
    suspend fun delete(route: RouteEntity)
}
