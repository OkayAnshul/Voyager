package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.GeofenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {
    
    @Query("SELECT * FROM geofences ORDER BY name")
    fun getAllGeofences(): Flow<List<GeofenceEntity>>
    
    @Query("SELECT * FROM geofences WHERE isActive = 1")
    fun getActiveGeofences(): Flow<List<GeofenceEntity>>
    
    @Query("SELECT * FROM geofences WHERE placeId = :placeId")
    suspend fun getGeofencesForPlace(placeId: Long): List<GeofenceEntity>
    
    @Query("SELECT * FROM geofences WHERE id = :id")
    suspend fun getGeofenceById(id: Long): GeofenceEntity?
    
    @Insert
    suspend fun insertGeofence(geofence: GeofenceEntity): Long
    
    @Update
    suspend fun updateGeofence(geofence: GeofenceEntity)
    
    @Delete
    suspend fun deleteGeofence(geofence: GeofenceEntity)
    
    @Query("UPDATE geofences SET isActive = :isActive WHERE id = :id")
    suspend fun updateGeofenceStatus(id: Long, isActive: Boolean)
    
    @Query("DELETE FROM geofences WHERE placeId = :placeId")
    suspend fun deleteGeofencesForPlace(placeId: Long)
}