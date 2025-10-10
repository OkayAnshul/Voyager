package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.CurrentRuntimeStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrentRuntimeStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CurrentRuntimeStateEntity)

    @Query("SELECT * FROM current_runtime_state WHERE id = 1")
    suspend fun get(): CurrentRuntimeStateEntity?

    @Query("SELECT * FROM current_runtime_state WHERE id = 1")
    fun observe(): Flow<CurrentRuntimeStateEntity?>

    @Transaction
    suspend fun atomicUpdate(transform: (CurrentRuntimeStateEntity) -> CurrentRuntimeStateEntity) {
        val current = get() ?: CurrentRuntimeStateEntity()
        val updated = transform(current).copy(stateVersion = current.stateVersion + 1)
        upsert(updated)
    }
}
