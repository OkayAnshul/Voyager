package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEvidenceEntity

@Dao
interface VisitEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(evidence: VisitEvidenceEntity)

    @Query("SELECT * FROM visit_evidence WHERE visitId = :visitId")
    suspend fun getByVisitId(visitId: Long): VisitEvidenceEntity?

    @Delete
    suspend fun delete(evidence: VisitEvidenceEntity)
}
