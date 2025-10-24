package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.LocationDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.data.mapper.toEntities
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.domain.exception.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val logger: ProductionLogger,
    private val errorHandler: ErrorHandler
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
        return errorHandler.executeWithErrorHandling(
            operation = {
                val count = locationDao.getLocationCount()
                logger.d("LocationRepository", "Total location count = $count")
                count
            },
            context = ErrorContext(
                operation = "getLocationCount",
                component = "LocationRepository"
            )
        ).getOrElse { 0 }
    }
    
    override suspend fun getLastLocation(): Location? {
        return locationDao.getLastLocation()?.toDomainModel()
    }
    
    override suspend fun insertLocation(location: Location): Long {
        return errorHandler.executeWithErrorHandling(
            operation = {
                // Validate location before insertion
                if (location.latitude == 0.0 && location.longitude == 0.0) {
                    throw DataValidationException.LocationValidationException(
                        "Invalid location coordinates: 0,0",
                        recoveryAction = RecoveryAction.SKIP_INVALID_DATA
                    )
                }
                
                val result = locationDao.insertLocation(location.toEntity())
                logger.d("LocationRepository", "Location inserted - ID=$result, lat=${location.latitude}, lng=${location.longitude}")
                result
            },
            context = ErrorContext(
                operation = "insertLocation",
                component = "LocationRepository",
                metadata = mapOf(
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "accuracy" to location.accuracy.toString()
                )
            )
        ).getOrThrow()
    }
    
    override suspend fun insertLocations(locations: List<Location>) {
        locationDao.insertLocations(locations.toEntities())
    }
    
    override suspend fun deleteLocation(location: Location) {
        locationDao.deleteLocation(location.toEntity())
    }
    
    override suspend fun deleteLocationsBefore(beforeDate: LocalDateTime): Int {
        return errorHandler.executeWithErrorHandling(
            operation = {
                val count = locationDao.deleteLocationsBefore(beforeDate)
                logger.d("LocationRepository", "Deleted $count locations before $beforeDate")
                count
            },
            context = ErrorContext(
                operation = "deleteLocationsBefore",
                component = "LocationRepository",
                metadata = mapOf("beforeDate" to beforeDate.toString())
            )
        ).getOrElse { 0 }
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