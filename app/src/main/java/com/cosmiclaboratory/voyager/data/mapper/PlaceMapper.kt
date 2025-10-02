package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.domain.model.Place

fun PlaceEntity.toDomainModel(): Place {
    return Place(
        id = id,
        name = name,
        category = category,
        latitude = latitude,
        longitude = longitude,
        address = address,
        visitCount = visitCount,
        totalTimeSpent = totalTimeSpent,
        lastVisit = lastVisit,
        isCustom = isCustom,
        radius = radius,
        placeId = placeId
    )
}

fun Place.toEntity(): PlaceEntity {
    return PlaceEntity(
        id = id,
        name = name,
        category = category,
        latitude = latitude,
        longitude = longitude,
        address = address,
        visitCount = visitCount,
        totalTimeSpent = totalTimeSpent,
        lastVisit = lastVisit,
        isCustom = isCustom,
        radius = radius,
        placeId = placeId
    )
}

fun List<PlaceEntity>.toDomainModels(): List<Place> {
    return map { it.toDomainModel() }
}

fun List<Place>.toEntities(): List<PlaceEntity> {
    return map { it.toEntity() }
}