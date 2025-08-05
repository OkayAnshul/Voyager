package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("SELECT * FROM places WHERE placeId = :id")
    suspend fun getById(id: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE placeId = :id")
    fun observeById(id: Long): Flow<PlaceEntity?>

    @Query("SELECT * FROM places WHERE lifecycleStatus != 'ARCHIVED' AND lifecycleStatus != 'MERGED' ORDER BY lastVisitedAt DESC")
    fun observeActivePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE lifecycleStatus = :status ORDER BY lastVisitedAt DESC")
    fun observeByStatus(status: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE geohash LIKE :prefix || '%' AND lifecycleStatus != 'ARCHIVED' AND lifecycleStatus != 'MERGED'")
    suspend fun getByGeohashPrefix(prefix: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE category = :category AND lifecycleStatus != 'ARCHIVED' AND lifecycleStatus != 'MERGED'")
    suspend fun getByCategory(category: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE lifecycleStatus != 'ARCHIVED' AND lifecycleStatus != 'MERGED' ORDER BY lastVisitedAt DESC LIMIT :limit")
    suspend fun getFrequentPlaces(limit: Int): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE category = 'HOME' AND lifecycleStatus = 'CONFIRMED' LIMIT 1")
    suspend fun getHomePlace(): PlaceEntity?

    @Query("UPDATE places SET userDisplayName = :name WHERE placeId = :placeId")
    suspend fun updateDisplayName(placeId: Long, name: String)

    @Query("UPDATE places SET category = :category, userCategory = :userCategory WHERE placeId = :placeId")
    suspend fun updateCategory(placeId: Long, category: String, userCategory: String?)

    @Query("UPDATE places SET lifecycleStatus = :status WHERE placeId = :placeId")
    suspend fun updateStatus(placeId: Long, status: String)

    @Query("UPDATE places SET mergedIntoPlaceId = :targetPlaceId, lifecycleStatus = 'MERGED' WHERE placeId = :placeId")
    suspend fun markMerged(placeId: Long, targetPlaceId: Long)

    @Query("UPDATE places SET lastVisitedAt = :lastVisitedAt WHERE placeId = :placeId")
    suspend fun updateLastVisitedAt(placeId: Long, lastVisitedAt: Long)

    @Query("UPDATE places SET emoji = :emoji WHERE placeId = :placeId")
    suspend fun updateEmoji(placeId: Long, emoji: String?)

    @Query("SELECT * FROM places WHERE lifecycleStatus != 'ARCHIVED' AND lifecycleStatus != 'MERGED'")
    suspend fun getAllActive(): List<PlaceEntity>

    @Query("SELECT COUNT(*) FROM places WHERE lifecycleStatus != 'ARCHIVED' AND lifecycleStatus != 'MERGED'")
    suspend fun countActive(): Int
}
