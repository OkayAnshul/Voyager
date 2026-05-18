package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.MileageClassificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MileageClassificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(classification: MileageClassificationEntity)

    @Query("SELECT * FROM mileage_classifications WHERE segmentId = :segmentId")
    suspend fun getBySegmentId(segmentId: Long): MileageClassificationEntity?

    @Query("SELECT * FROM mileage_classifications WHERE segmentId IN (:segmentIds)")
    suspend fun getBySegmentIds(segmentIds: List<Long>): List<MileageClassificationEntity>

    /** Reactive view of every classification — drives the mileage-log screen. */
    @Query("SELECT * FROM mileage_classifications")
    fun observeAll(): Flow<List<MileageClassificationEntity>>

    @Query("DELETE FROM mileage_classifications WHERE segmentId = :segmentId")
    suspend fun deleteBySegmentId(segmentId: Long)
}
