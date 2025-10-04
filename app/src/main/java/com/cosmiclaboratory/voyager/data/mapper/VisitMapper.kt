package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.domain.model.Visit

fun VisitEntity.toDomainModel(): Visit {
    return Visit.createWithDuration(
        id = id,
        placeId = placeId,
        entryTime = entryTime,
        exitTime = exitTime,
        confidence = confidence
    )
}

fun Visit.toEntity(): VisitEntity {
    return VisitEntity(
        id = id,
        placeId = placeId,
        entryTime = entryTime,
        exitTime = exitTime,
        duration = duration, // Use the calculated property
        confidence = confidence
    )
}

fun List<VisitEntity>.toDomainModels(): List<Visit> {
    return map { it.toDomainModel() }
}

fun List<Visit>.toEntities(): List<VisitEntity> {
    return map { it.toEntity() }
}