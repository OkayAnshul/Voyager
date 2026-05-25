package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Query("SELECT * FROM activities WHERE activityId = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    suspend fun getAll(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE dayKey = :dayKey AND deletedAt IS NULL ORDER BY startedAt ASC")
    suspend fun getByDayKey(dayKey: String): List<ActivityEntity>

    /** User-authored fields for a recorded activity (the rest is computed). */
    @Query("UPDATE activities SET title = :title, notes = :notes, lastModifiedAt = :modifiedAt WHERE activityId = :id")
    suspend fun updateUserFields(id: Long, title: String?, notes: String?, modifiedAt: Long)

    /** Soft-delete — keeps the row as a tombstone for sync consistency. */
    @Query("UPDATE activities SET deletedAt = :deletedAt WHERE activityId = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
}
