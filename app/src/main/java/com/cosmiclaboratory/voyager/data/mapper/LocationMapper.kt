package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.LocationEntity
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.UserActivity
import com.cosmiclaboratory.voyager.domain.model.SemanticContext

/**
 * Phase 1: Activity-First Implementation
 * Updated to handle activity context fields
 */

fun LocationEntity.toDomainModel(): Location {
    return Location(
        id = id,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        accuracy = accuracy,
        speed = speed,
        altitude = altitude,
        bearing = bearing,
        // Phase 1: Map activity fields
        userActivity = try {
            UserActivity.valueOf(userActivity)
        } catch (e: IllegalArgumentException) {
            UserActivity.UNKNOWN
        },
        activityConfidence = activityConfidence,
        semanticContext = semanticContext?.let {
            try {
                SemanticContext.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
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
        bearing = bearing,
        // Phase 1: Map activity fields
        userActivity = userActivity.name,
        activityConfidence = activityConfidence,
        semanticContext = semanticContext?.name
    )
}

fun List<LocationEntity>.toDomainModels(): List<Location> {
    return map { it.toDomainModel() }
}

fun List<Location>.toEntities(): List<LocationEntity> {
    return map { it.toEntity() }
}