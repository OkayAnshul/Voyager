package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEvidenceEntity

@Dao
interface PlaceEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(evidence: PlaceEvidenceEntity)

    @Query("SELECT * FROM place_evidence WHERE placeId = :placeId")
    suspend fun getByPlaceId(placeId: Long): PlaceEvidenceEntity?

    @Delete
    suspend fun delete(evidence: PlaceEvidenceEntity)
}
