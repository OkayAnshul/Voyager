package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementSegmentDao {
    @Insert
    suspend fun insert(segment: MovementSegmentEntity): Long

    @Update
    suspend fun update(segment: MovementSegmentEntity)

    @Query("SELECT * FROM movement_segments WHERE segmentId = :id")
    suspend fun getById(id: Long): MovementSegmentEntity?

    @Query("SELECT * FROM movement_segments WHERE dayKey = :dayKey ORDER BY startAt ASC")
    suspend fun getByDayKey(dayKey: String): List<MovementSegmentEntity>

    @Query("SELECT COUNT(*) FROM movement_segments WHERE dayKey = :dayKey")
    suspend fun countByDayKey(dayKey: String): Int

    @Query("SELECT * FROM movement_segments WHERE dayKey = :dayKey ORDER BY startAt ASC")
    fun observeByDayKey(dayKey: String): Flow<List<MovementSegmentEntity>>

    @Query("SELECT * FROM movement_segments WHERE placeId = :placeId ORDER BY startAt DESC")
    suspend fun getByPlaceId(placeId: Long): List<MovementSegmentEntity>

    /** Re-points every segment at [sourceId] to [targetId] — used when places merge. */
    @Query("UPDATE movement_segments SET placeId = :targetId WHERE placeId = :sourceId")
    suspend fun reassignPlace(sourceId: Long, targetId: Long)

    @Query("SELECT * FROM movement_segments WHERE routeId = :routeId")
    suspend fun getByRouteId(routeId: Long): MovementSegmentEntity?

    @Query("""
        SELECT * FROM movement_segments
        WHERE dayKey = :dayKey
        AND startAt < :endAt AND endAt > :startAt
    """)
    suspend fun getOverlapping(dayKey: String, startAt: Long, endAt: Long): List<MovementSegmentEntity>

    /** Segments of the given types within an inclusive dayKey range — drives the mileage log. */
    @Query("""
        SELECT * FROM movement_segments
        WHERE segmentType IN (:types)
        AND dayKey BETWEEN :startDay AND :endDay
        ORDER BY startAt DESC
    """)
    suspend fun getByTypesBetween(
        types: List<String>,
        startDay: String,
        endDay: String
    ): List<MovementSegmentEntity>

    @Query("SELECT * FROM movement_segments ORDER BY startAt DESC LIMIT 1")
    suspend fun getLatest(): MovementSegmentEntity?

    @Query("SELECT * FROM movement_segments ORDER BY startAt DESC LIMIT 1")
    fun observeLatest(): Flow<MovementSegmentEntity?>

    @Query("SELECT DISTINCT dayKey FROM movement_segments ORDER BY dayKey DESC")
    suspend fun getAllDayKeys(): List<String>

    @Query("SELECT DISTINCT dayKey FROM movement_segments ORDER BY dayKey DESC")
    fun observeAllDayKeys(): Flow<List<String>>

    @Query("DELETE FROM movement_segments WHERE startAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Delete
    suspend fun delete(segment: MovementSegmentEntity)
}
