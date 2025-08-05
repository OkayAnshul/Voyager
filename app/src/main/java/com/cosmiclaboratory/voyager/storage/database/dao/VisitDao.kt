package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Insert
    suspend fun insert(visit: VisitEntity): Long

    @Update
    suspend fun update(visit: VisitEntity)

    @Query("SELECT * FROM visits WHERE visitId = :id")
    suspend fun getById(id: Long): VisitEntity?

    @Query("SELECT * FROM visits WHERE dayKey = :dayKey ORDER BY arrivalAt ASC")
    suspend fun getByDayKey(dayKey: String): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE dayKey = :dayKey ORDER BY arrivalAt ASC")
    fun observeByDayKey(dayKey: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE placeId = :placeId ORDER BY arrivalAt DESC")
    suspend fun getByPlaceId(placeId: Long): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE placeId = :placeId ORDER BY arrivalAt DESC")
    fun observeByPlaceId(placeId: Long): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE departureAt IS NULL ORDER BY arrivalAt DESC LIMIT 1")
    suspend fun getActiveVisit(): VisitEntity?

    @Query("SELECT * FROM visits WHERE departureAt IS NULL ORDER BY arrivalAt DESC LIMIT 1")
    fun observeActiveVisit(): Flow<VisitEntity?>

    @Query("SELECT * FROM visits WHERE departureAt IS NULL AND arrivalAt < :cutoffMs")
    suspend fun getStaleOpenVisits(cutoffMs: Long): List<VisitEntity>

    @Query("""
        SELECT * FROM visits
        WHERE dayKey = :dayKey
        AND arrivalAt < :departureAt
        AND (departureAt IS NULL OR departureAt > :arrivalAt)
    """)
    suspend fun getOverlapping(dayKey: String, arrivalAt: Long, departureAt: Long): List<VisitEntity>

    @Transaction
    suspend fun insertIfNotOverlapping(visit: VisitEntity): Long {
        val departureAt = visit.departureAt ?: Long.MAX_VALUE // ongoing visit spans all future time
        val overlapping = getOverlapping(visit.dayKey, visit.arrivalAt, departureAt)
        if (overlapping.isNotEmpty()) return -1L
        return insert(visit)
    }

    @Query("UPDATE visits SET departureAt = :departureAt, dwellMs = :dwellMs WHERE visitId = :visitId")
    suspend fun endVisit(visitId: Long, departureAt: Long, dwellMs: Long)

    @Query("SELECT COUNT(*) FROM visits WHERE placeId = :placeId")
    suspend fun countByPlaceId(placeId: Long): Int

    @Query("SELECT COUNT(*) FROM visits WHERE placeId = :placeId AND arrivalAt >= :sinceMs")
    suspend fun countByPlaceIdSince(placeId: Long, sinceMs: Long): Int

    @Delete
    suspend fun delete(visit: VisitEntity)

    /** Find the visit whose time range overlaps the given segment window. */
    @Query("""
        SELECT * FROM visits
        WHERE arrivalAt <= :endAt
        AND (departureAt IS NULL OR departureAt >= :startAt)
        ORDER BY arrivalAt DESC LIMIT 1
    """)
    suspend fun getVisitOverlappingWindow(startAt: Long, endAt: Long): VisitEntity?

    @Query("DELETE FROM visits WHERE arrivalAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
