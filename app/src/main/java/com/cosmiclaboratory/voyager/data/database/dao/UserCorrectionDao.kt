package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.UserCorrectionEntity
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCorrectionDao {

    @Query("SELECT * FROM user_corrections ORDER BY correctionTime DESC")
    fun getAllCorrections(): Flow<List<UserCorrectionEntity>>

    @Query("SELECT * FROM user_corrections WHERE placeId = :placeId ORDER BY correctionTime DESC")
    fun getCorrectionsForPlace(placeId: Long): Flow<List<UserCorrectionEntity>>

    @Query("SELECT * FROM user_corrections WHERE correctionType = :correctionType ORDER BY correctionTime DESC")
    fun getCorrectionsByType(correctionType: CorrectionType): Flow<List<UserCorrectionEntity>>

    @Query("SELECT * FROM user_corrections WHERE wasAppliedToLearning = 0 ORDER BY correctionTime DESC")
    suspend fun getUnappliedCorrections(): List<UserCorrectionEntity>

    @Query("SELECT * FROM user_corrections WHERE id = :id")
    suspend fun getCorrectionById(id: Long): UserCorrectionEntity?

    @Query("SELECT COUNT(*) FROM user_corrections WHERE correctionType = :correctionType")
    suspend fun getCorrectionCountByType(correctionType: CorrectionType): Int

    @Query("SELECT COUNT(*) FROM user_corrections WHERE correctionTime >= :since")
    suspend fun getRecentCorrectionCount(since: java.time.LocalDateTime): Int

    @Insert
    suspend fun insertCorrection(correction: UserCorrectionEntity): Long

    @Update
    suspend fun updateCorrection(correction: UserCorrectionEntity)

    @Delete
    suspend fun deleteCorrection(correction: UserCorrectionEntity)

    @Query("DELETE FROM user_corrections WHERE id = :id")
    suspend fun deleteCorrectionById(id: Long)

    @Query("UPDATE user_corrections SET wasAppliedToLearning = 1 WHERE id = :id")
    suspend fun markCorrectionAsApplied(id: Long)

    @Query("DELETE FROM user_corrections WHERE correctionTime < :before")
    suspend fun deleteOldCorrections(before: java.time.LocalDateTime)
}
