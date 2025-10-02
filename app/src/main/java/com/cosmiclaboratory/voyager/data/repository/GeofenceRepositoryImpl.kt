package com.cosmiclaboratory.voyager.data.repository

import android.content.Context
import com.cosmiclaboratory.voyager.data.database.dao.GeofenceDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.Geofence
import com.cosmiclaboratory.voyager.domain.repository.GeofenceRepository
import com.google.android.gms.location.GeofencingClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceRepositoryImpl @Inject constructor(
    private val geofenceDao: GeofenceDao,
    private val geofencingClient: GeofencingClient,
    @ApplicationContext private val context: Context
) : GeofenceRepository {
    
    override fun getAllGeofences(): Flow<List<Geofence>> {
        return geofenceDao.getAllGeofences().map { it.toDomainModels() }
    }
    
    override fun getActiveGeofences(): Flow<List<Geofence>> {
        return geofenceDao.getActiveGeofences().map { it.toDomainModels() }
    }
    
    override suspend fun getGeofencesForPlace(placeId: Long): List<Geofence> {
        return geofenceDao.getGeofencesForPlace(placeId).toDomainModels()
    }
    
    override suspend fun getGeofenceById(id: Long): Geofence? {
        return geofenceDao.getGeofenceById(id)?.toDomainModel()
    }
    
    override suspend fun insertGeofence(geofence: Geofence): Long {
        return geofenceDao.insertGeofence(geofence.toEntity())
    }
    
    override suspend fun updateGeofence(geofence: Geofence) {
        geofenceDao.updateGeofence(geofence.toEntity())
    }
    
    override suspend fun deleteGeofence(geofence: Geofence) {
        geofenceDao.deleteGeofence(geofence.toEntity())
    }
    
    override suspend fun updateGeofenceStatus(id: Long, isActive: Boolean) {
        geofenceDao.updateGeofenceStatus(id, isActive)
    }
    
    override suspend fun deleteGeofencesForPlace(placeId: Long) {
        geofenceDao.deleteGeofencesForPlace(placeId)
    }
    
    override suspend fun registerGeofence(geofence: Geofence): Boolean {
        return try {
            // Implementation would register with GeofencingClient
            // For now, just return true
            insertGeofence(geofence)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun unregisterGeofence(geofenceId: Long): Boolean {
        return try {
            // Implementation would unregister with GeofencingClient
            // For now, just return true
            updateGeofenceStatus(geofenceId, false)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun refreshAllGeofences() {
        // Implementation would refresh all geofences with the system
        // For now, do nothing
    }
}