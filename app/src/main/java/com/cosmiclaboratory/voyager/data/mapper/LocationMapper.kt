package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.LocationEntity
import com.cosmiclaboratory.voyager.domain.model.Location

fun LocationEntity.toDomainModel(): Location {
    return Location(
        id = id,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        accuracy = accuracy,
        speed = speed,
        altitude = altitude,
        bearing = bearing
    )
}

fun Location.toEntity(): LocationEntity {
    return LocationEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        accuracy = accuracy,
        speed = speed,
        altitude = altitude,
        bearing = bearing
    )
}

fun List<LocationEntity>.toDomainModels(): List<Location> {
    return map { it.toDomainModel() }
}

fun List<Location>.toEntities(): List<LocationEntity> {
    return map { it.toEntity() }
}