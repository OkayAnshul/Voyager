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

    /** Persists the user-authored fields for one trip (the rest is derived). */
    @Query(
        "UPDATE trips SET userTitle = :userTitle, notes = :notes, " +
            "coverPhotoUri = :coverPhotoUri, dayCaptionsJson = :dayCaptionsJson, " +
            "lastModifiedAt = :modifiedAt WHERE tripId = :tripId"
    )
    suspend fun updateUserFields(
        tripId: Long,
        userTitle: String?,
        notes: String?,
        coverPhotoUri: String?,
        dayCaptionsJson: String?,
        modifiedAt: Long
    )

    /**
     * Replaces the whole table with a freshly detected set. The summary fields are
     * pure derived data, so detection rebuilds them wholesale; but user-authored
     * fields (title/notes/cover/captions) are carried over from the prior row with
     * the same [TripEntity.startDayKey] so an edited trip survives re-detection.
     * The transaction keeps the swap atomic.
     */
    @Transaction
    suspend fun replaceAll(trips: List<TripEntity>) {
        val priorByStartDay = getAll().associateBy { it.startDayKey }
        deleteAll()
        for (trip in trips) {
            val prior = priorByStartDay[trip.startDayKey]
            insert(
                if (prior == null) trip
                else trip.copy(
                    userTitle = prior.userTitle,
                    notes = prior.notes,
                    coverPhotoUri = prior.coverPhotoUri,
                    dayCaptionsJson = prior.dayCaptionsJson
                )
            )
        }
    }
}
