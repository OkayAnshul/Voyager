package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cosmiclaboratory.voyager.storage.database.entity.WorkoutSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSegmentDao {

    @Insert
    suspend fun insert(segment: WorkoutSegmentEntity): Long

    @Query("SELECT * FROM workout_segments WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WorkoutSegmentEntity>>

    @Query("SELECT * FROM workout_segments WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAll(): List<WorkoutSegmentEntity>

    @Query("SELECT * FROM workout_segments WHERE segmentId = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): WorkoutSegmentEntity?

    @Query("UPDATE workout_segments SET deletedAt = :deletedAt WHERE segmentId = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
}
