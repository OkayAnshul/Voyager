package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.Geofence
import kotlinx.coroutines.flow.Flow

interface GeofenceRepository {
    
    fun getAllGeofences(): Flow<List<Geofence>>
    
    fun getActiveGeofences(): Flow<List<Geofence>>
    
    suspend fun getGeofencesForPlace(placeId: Long): List<Geofence>
    
    suspend fun getGeofenceById(id: Long): Geofence?
    
    suspend fun insertGeofence(geofence: Geofence): Long
    
    suspend fun updateGeofence(geofence: Geofence)
    
    suspend fun deleteGeofence(geofence: Geofence)
    
    suspend fun updateGeofenceStatus(id: Long, isActive: Boolean)
    
    suspend fun deleteGeofencesForPlace(placeId: Long)
    
    suspend fun registerGeofence(geofence: Geofence): Boolean
    
    suspend fun unregisterGeofence(geofenceId: Long): Boolean
    
    suspend fun refreshAllGeofences()
}