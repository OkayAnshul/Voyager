package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.CorrectionFeedbackEntity

@Dao
interface CorrectionFeedbackDao {
    @Insert
    suspend fun insert(feedback: CorrectionFeedbackEntity): Long

    @Query("SELECT * FROM correction_feedback WHERE propagated = 0 ORDER BY createdAt ASC")
    suspend fun getUnpropagated(): List<CorrectionFeedbackEntity>

    @Query("SELECT * FROM correction_feedback WHERE correctionType = :type AND createdAt >= :sinceMs")
    suspend fun getByCorrectionTypeSince(type: String, sinceMs: Long): List<CorrectionFeedbackEntity>

    @Query("SELECT * FROM correction_feedback WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC")
    suspend fun getByEntity(entityType: String, entityId: Long): List<CorrectionFeedbackEntity>

    @Query("UPDATE correction_feedback SET propagated = 1 WHERE feedbackId = :feedbackId")
    suspend fun markPropagated(feedbackId: Long)

    @Query("DELETE FROM correction_feedback WHERE createdAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
