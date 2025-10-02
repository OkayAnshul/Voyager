package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.GeofenceEntity
import com.cosmiclaboratory.voyager.domain.model.Geofence

fun GeofenceEntity.toDomainModel(): Geofence {
    return Geofence(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        isActive = isActive,
        enterAlert = enterAlert,
        exitAlert = exitAlert,
        placeId = placeId
    )
}

fun Geofence.toEntity(): GeofenceEntity {
    return GeofenceEntity(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        isActive = isActive,
        enterAlert = enterAlert,
        exitAlert = exitAlert,
        placeId = placeId
    )
}

fun List<GeofenceEntity>.toDomainModels(): List<Geofence> {
    return map { it.toDomainModel() }
}

fun List<Geofence>.toEntities(): List<GeofenceEntity> {
    return map { it.toEntity() }
}