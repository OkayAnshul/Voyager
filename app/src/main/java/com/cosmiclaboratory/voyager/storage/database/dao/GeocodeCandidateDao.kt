package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.GeocodeCandidateEntity

@Dao
interface GeocodeCandidateDao {
    @Insert
    suspend fun insert(candidate: GeocodeCandidateEntity): Long

    @Insert
    suspend fun insertAll(candidates: List<GeocodeCandidateEntity>)

    @Query("SELECT * FROM geocode_candidates WHERE placeId = :placeId ORDER BY rank ASC")
    suspend fun getByPlaceId(placeId: Long): List<GeocodeCandidateEntity>

    @Query("SELECT * FROM geocode_candidates WHERE placeId = :placeId AND provider = :provider")
    suspend fun getByPlaceAndProvider(placeId: Long, provider: String): List<GeocodeCandidateEntity>

    @Query("DELETE FROM geocode_candidates WHERE placeId = :placeId")
    suspend fun deleteByPlaceId(placeId: Long)

    @Query("DELETE FROM geocode_candidates WHERE cachedUntil IS NOT NULL AND cachedUntil < :nowMs")
    suspend fun deleteExpired(nowMs: Long): Int
}
