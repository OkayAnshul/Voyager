package com.cosmiclaboratory.voyager.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.cosmiclaboratory.voyager.data.database.dao.GeofenceDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.data.receiver.GeofenceReceiver
import com.cosmiclaboratory.voyager.domain.model.Geofence
import com.cosmiclaboratory.voyager.domain.repository.GeofenceRepository
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceRepositoryImpl @Inject constructor(
    private val geofenceDao: GeofenceDao,
    private val geofencingClient: GeofencingClient,
    @ApplicationContext private val context: Context
) : GeofenceRepository {
    
    companion object {
        private const val TAG = "GeofenceRepository"
        private const val GEOFENCE_REQUEST_CODE = 1000
    }
    
    // Pending intent for geofence transitions
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
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
    
    @SuppressLint("MissingPermission")
    override suspend fun registerGeofence(geofence: Geofence): Boolean {
        return try {
            // Check for location permissions
            if (!hasLocationPermissions()) {
                Log.w(TAG, "Location permissions not granted, cannot register geofence")
                return false
            }
            
            // Create Android geofence
            val androidGeofence = com.google.android.gms.location.Geofence.Builder()
                .setRequestId("place_${geofence.placeId ?: geofence.id}")
                .setCircularRegion(geofence.latitude, geofence.longitude, geofence.radius.toFloat())
                .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER or 
                    com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .build()
            
            // Create geofencing request
            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(androidGeofence)
                .build()
            
            // Register with system
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            
            // Store in database
            insertGeofence(geofence)
            
            Log.d(TAG, "Successfully registered geofence for place ${geofence.placeId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register geofence", e)
            false
        }
    }
    
    override suspend fun unregisterGeofence(geofenceId: Long): Boolean {
        return try {
            val geofence = getGeofenceById(geofenceId)
            if (geofence != null) {
                val requestId = "place_${geofence.placeId ?: geofence.id}"
                
                // Remove from system
                geofencingClient.removeGeofences(listOf(requestId))
                
                // Update database
                updateGeofenceStatus(geofenceId, false)
                
                Log.d(TAG, "Successfully unregistered geofence $requestId")
                true
            } else {
                Log.w(TAG, "Geofence $geofenceId not found")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister geofence", e)
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    override suspend fun refreshAllGeofences() {
        try {
            if (!hasLocationPermissions()) {
                Log.w(TAG, "Location permissions not granted, cannot refresh geofences")
                return
            }
            
            // Remove all existing geofences
            geofencingClient.removeGeofences(geofencePendingIntent)
            
            // Re-register all active geofences
            val activeGeofenceEntities = geofenceDao.getAllGeofences().first().filter { it.isActive }
            val activeGeofences = activeGeofenceEntities.toDomainModels()
            activeGeofences.forEach { geofence ->
                registerGeofence(geofence)
            }
            
            Log.d(TAG, "Refreshed ${activeGeofences.size} geofences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh geofences", e)
        }
    }
    
    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}