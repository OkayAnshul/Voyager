package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity

@Dao
interface SegmentEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(evidence: SegmentEvidenceEntity)

    @Query("SELECT * FROM segment_evidence WHERE segmentId = :segmentId")
    suspend fun getBySegmentId(segmentId: Long): SegmentEvidenceEntity?

    @Delete
    suspend fun delete(evidence: SegmentEvidenceEntity)
}
