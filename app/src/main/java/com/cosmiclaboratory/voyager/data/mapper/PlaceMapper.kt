package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.UserActivity
import com.cosmiclaboratory.voyager.domain.model.SemanticContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Phase 2: Activity-First Implementation
 * Updated to handle OSM fields and activity distributions
 */

private val gson = Gson()

fun PlaceEntity.toDomainModel(): Place {
    return Place(
        id = id,
        name = name,
        category = category,
        customCategoryName = customCategoryName,  // ISSUE #3
        latitude = latitude,
        longitude = longitude,
        address = address,
        streetName = streetName,
        locality = locality,
        subLocality = subLocality,
        postalCode = postalCode,
        countryCode = countryCode,
        isUserRenamed = isUserRenamed,
        needsUserNaming = needsUserNaming,
        // OSM fields
        osmSuggestedName = osmSuggestedName,
        osmSuggestedCategory = osmSuggestedCategory?.let {
            try {
                PlaceCategory.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        },
        osmPlaceType = osmPlaceType,
        // Phase 2: Activity fields
        dominantActivity = dominantActivity?.let {
            try {
                UserActivity.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        },
        activityDistribution = activityDistributionJson?.let {
            try {
                val type = object : TypeToken<Map<String, Float>>() {}.type
                val stringMap: Map<String, Float> = gson.fromJson(it, type)
                stringMap.mapKeys { entry ->
                    UserActivity.valueOf(entry.key)
                }
            } catch (e: Exception) {
                emptyMap()
            }
        } ?: emptyMap(),
        dominantSemanticContext = dominantSemanticContext?.let {
            try {
                SemanticContext.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        },
        contextDistribution = contextDistributionJson?.let {
            try {
                val type = object : TypeToken<Map<String, Float>>() {}.type
                val stringMap: Map<String, Float> = gson.fromJson(it, type)
                stringMap.mapKeys { entry ->
                    SemanticContext.valueOf(entry.key)
                }
            } catch (e: Exception) {
                emptyMap()
            }
        } ?: emptyMap(),
        visitCount = visitCount,
        totalTimeSpent = totalTimeSpent,
        lastVisit = lastVisit,
        isCustom = isCustom,
        radius = radius,
        placeId = placeId,
        confidence = confidence
    )
}

fun Place.toEntity(): PlaceEntity {
    return PlaceEntity(
        id = id,
        name = name,
        category = category,
        customCategoryName = customCategoryName,  // ISSUE #3
        latitude = latitude,
        longitude = longitude,
        address = address,
        streetName = streetName,
        locality = locality,
        subLocality = subLocality,
        postalCode = postalCode,
        countryCode = countryCode,
        isUserRenamed = isUserRenamed,
        needsUserNaming = needsUserNaming,
        // OSM fields
        osmSuggestedName = osmSuggestedName,
        osmSuggestedCategory = osmSuggestedCategory?.name,
        osmPlaceType = osmPlaceType,
        // Phase 2: Activity fields
        dominantActivity = dominantActivity?.name,
        activityDistributionJson = if (activityDistribution.isNotEmpty()) {
            val stringMap = activityDistribution.mapKeys { it.key.name }
            gson.toJson(stringMap)
        } else null,
        dominantSemanticContext = dominantSemanticContext?.name,
        contextDistributionJson = if (contextDistribution.isNotEmpty()) {
            val stringMap = contextDistribution.mapKeys { it.key.name }
            gson.toJson(stringMap)
        } else null,
        visitCount = visitCount,
        totalTimeSpent = totalTimeSpent,
        lastVisit = lastVisit,
        isCustom = isCustom,
        radius = radius,
        placeId = placeId,
        confidence = confidence
    )
}

fun List<PlaceEntity>.toDomainModels(): List<Place> {
    return map { it.toDomainModel() }
}

fun List<Place>.toEntities(): List<PlaceEntity> {
    return map { it.toEntity() }
}