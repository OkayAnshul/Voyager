package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.data.database.dao.VisitDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.PlaceWithVisits
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.utils.LocationUtils
import com.cosmiclaboratory.voyager.utils.ErrorHandler
import com.cosmiclaboratory.voyager.utils.ErrorContext
import com.cosmiclaboratory.voyager.domain.exception.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val errorHandler: ErrorHandler
) : PlaceRepository {
    
    override fun getAllPlaces(): Flow<List<Place>> {
        return placeDao.getAllPlaces().map { it.toDomainModels() }
    }
    
    override fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>> {
        return placeDao.getPlacesByCategory(category).map { it.toDomainModels() }
    }
    
    override fun getMostVisitedPlaces(limit: Int): Flow<List<Place>> {
        return placeDao.getMostVisitedPlaces(limit).map { it.toDomainModels() }
    }
    
    override fun getPlacesWithMostTime(limit: Int): Flow<List<Place>> {
        return placeDao.getPlacesWithMostTime(limit).map { it.toDomainModels() }
    }
    
    override suspend fun getPlacesNearLocation(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<Place> {
        val bounds = LocationUtils.calculateBounds(latitude, longitude, radiusKm)
        return placeDao.getPlacesNearLocation(
            bounds.minLat, bounds.maxLat, bounds.minLng, bounds.maxLng
        ).toDomainModels()
    }
    
    override suspend fun getPlaceById(id: Long): Place? {
        return placeDao.getPlaceById(id)?.toDomainModel()
    }
    
    override suspend fun getPlaceByGoogleId(googlePlaceId: String): Place? {
        return placeDao.getPlaceByGoogleId(googlePlaceId)?.toDomainModel()
    }
    
    override suspend fun insertPlace(place: Place): Long {
        return errorHandler.executeWithErrorHandling(
            operation = {
                // Validate place before insertion
                if (place.name.isBlank()) {
                    throw DataValidationException.PlaceValidationException(
                        "Place name cannot be blank",
                        recoveryAction = RecoveryAction.SKIP_INVALID_DATA
                    )
                }
                
                if (place.latitude == 0.0 && place.longitude == 0.0) {
                    throw DataValidationException.PlaceValidationException(
                        "Invalid place coordinates: 0,0",
                        recoveryAction = RecoveryAction.SKIP_INVALID_DATA
                    )
                }
                
                placeDao.insertPlace(place.toEntity())
            },
            context = ErrorContext(
                operation = "insertPlace",
                component = "PlaceRepository",
                metadata = mapOf(
                    "placeName" to place.name,
                    "latitude" to place.latitude.toString(),
                    "longitude" to place.longitude.toString(),
                    "category" to place.category.toString()
                )
            )
        ).getOrThrow()
    }
    
    override suspend fun updatePlace(place: Place) {
        placeDao.updatePlace(place.toEntity())
    }
    
    override suspend fun deletePlace(place: Place) {
        placeDao.deletePlace(place.toEntity())
    }
    
    override suspend fun deletePlaceById(id: Long) {
        placeDao.deletePlaceById(id)
    }
    
    override suspend fun incrementVisitStats(id: Long, duration: Long) {
        errorHandler.executeWithErrorHandling(
            operation = {
                if (duration < 0) {
                    throw DataValidationException.PlaceValidationException(
                        "Visit duration cannot be negative: $duration",
                        recoveryAction = RecoveryAction.SKIP_INVALID_DATA
                    )
                }
                
                placeDao.incrementVisitStats(id, duration)
                Unit
            },
            context = ErrorContext(
                operation = "incrementVisitStats",
                component = "PlaceRepository",
                metadata = mapOf(
                    "placeId" to id.toString(),
                    "duration" to duration.toString()
                )
            )
        ).getOrElse {
            // Log error but don't fail - visit stats are not critical
        }
    }
    
    override suspend fun getPlaceCountByCategory(category: PlaceCategory): Int {
        return placeDao.getPlaceCountByCategory(category)
    }
    
    override suspend fun getPlaceWithVisits(placeId: Long): PlaceWithVisits? {
        val place = placeDao.getPlaceById(placeId)?.toDomainModel() ?: return null
        val visits = visitDao.getVisitsForPlace(placeId)
        // Note: This would need to be implemented differently as Flow -> List conversion
        // For now, return place with empty visits list
        return PlaceWithVisits(place, emptyList())
    }
    
    override suspend fun findOrCreatePlace(
        latitude: Double,
        longitude: Double,
        name: String,
        category: PlaceCategory
    ): Place {
        // Check if place already exists nearby (within 100m)
        val nearbyPlaces = getPlacesNearLocation(latitude, longitude, 0.1)
        val existingPlace = nearbyPlaces.find { 
            LocationUtils.calculateDistance(
                latitude, longitude, 
                it.latitude, it.longitude
            ) <= 100.0 // 100 meters
        }
        
        return existingPlace ?: run {
            val newPlace = Place(
                name = name,
                category = category,
                latitude = latitude,
                longitude = longitude
            )
            val id = insertPlace(newPlace)
            newPlace.copy(id = id)
        }
    }
}