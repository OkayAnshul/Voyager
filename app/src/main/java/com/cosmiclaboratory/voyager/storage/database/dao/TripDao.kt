package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cosmiclaboratory.voyager.storage.database.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE deletedAt IS NULL ORDER BY startDayKey DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE tripId = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE deletedAt IS NULL ORDER BY startDayKey DESC")
    suspend fun getAll(): List<TripEntity>

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query("DELETE FROM trips")
    suspend fun deleteAll()

    /**
     * Replaces the whole table with a freshly detected set. Trips are pure derived
     * data with no user-authored fields, so [DetectTripsUseCase] rebuilds them
     * wholesale rather than diffing — the transaction keeps the swap atomic.
     */
    @Transaction
    suspend fun replaceAll(trips: List<TripEntity>) {
        deleteAll()
        for (trip in trips) insert(trip)
    }
}
