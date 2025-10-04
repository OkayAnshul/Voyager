package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.LocationDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.data.mapper.toEntities
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao
) : LocationRepository {
    
    override fun getRecentLocations(limit: Int): Flow<List<Location>> {
        return locationDao.getRecentLocations(limit).map { it.toDomainModels() }
    }
    
    override fun getLocationsBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flow<List<Location>> {
        return locationDao.getLocationsBetween(startTime, endTime).map { it.toDomainModels() }
    }
    
    override suspend fun getLocationsSince(since: LocalDateTime): List<Location> {
        return locationDao.getLocationsSince(since).toDomainModels()
    }
    
    override suspend fun getLocationCount(): Int {
        val count = locationDao.getLocationCount()
        android.util.Log.d("LocationRepository", "CRITICAL DEBUG: Total location count = $count")
        return count
    }
    
    override suspend fun getLastLocation(): Location? {
        return locationDao.getLastLocation()?.toDomainModel()
    }
    
    override suspend fun insertLocation(location: Location): Long {
        val result = locationDao.insertLocation(location.toEntity())
        android.util.Log.d("LocationRepository", "CRITICAL DEBUG: Location inserted - ID=$result, lat=${location.latitude}, lng=${location.longitude}")
        return result
    }
    
    override suspend fun insertLocations(locations: List<Location>) {
        locationDao.insertLocations(locations.toEntities())
    }
    
    override suspend fun deleteLocation(location: Location) {
        locationDao.deleteLocation(location.toEntity())
    }
    
    override suspend fun deleteLocationsBefore(beforeDate: LocalDateTime): Int {
        return locationDao.deleteLocationsBefore(beforeDate)
    }
    
    override suspend fun deleteAllLocations() {
        locationDao.deleteAllLocations()
    }
    
    override suspend fun getLocationsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<Location> {
        return locationDao.getLocationsInBounds(minLat, maxLat, minLng, maxLng).toDomainModels()
    }
}