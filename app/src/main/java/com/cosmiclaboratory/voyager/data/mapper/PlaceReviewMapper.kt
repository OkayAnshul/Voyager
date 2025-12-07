package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.PlaceReviewEntity
import com.cosmiclaboratory.voyager.domain.model.PlaceReview
import org.json.JSONObject

fun PlaceReviewEntity.toDomainModel(): PlaceReview {
    return PlaceReview(
        id = id,
        placeId = placeId,
        detectedName = detectedName,
        detectedCategory = detectedCategory,
        confidence = confidence,
        latitude = latitude,
        longitude = longitude,
        detectionTime = detectionTime,
        status = status,
        priority = priority,
        reviewType = reviewType,
        osmSuggestedName = osmSuggestedName,
        osmSuggestedCategory = osmSuggestedCategory,
        osmPlaceType = osmPlaceType,
        userApprovedName = userApprovedName,
        userApprovedCategory = userApprovedCategory,
        reviewedAt = reviewedAt,
        locationCount = locationCount,
        visitCount = visitCount,
        notes = notes,
        confidenceBreakdown = parseConfidenceBreakdown(confidenceBreakdown)
    )
}

private fun parseConfidenceBreakdown(json: String?): Map<String, Float>? {
    if (json == null) return null
    return try {
        val jsonObject = JSONObject(json)
        val map = mutableMapOf<String, Float>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.getDouble(key).toFloat()
        }
        map
    } catch (e: Exception) {
        null
    }
}

fun PlaceReview.toEntity(): PlaceReviewEntity {
    return PlaceReviewEntity(
        id = id,
        placeId = placeId,
        detectedName = detectedName,
        detectedCategory = detectedCategory,
        confidence = confidence,
        latitude = latitude,
        longitude = longitude,
        detectionTime = detectionTime,
        status = status,
        priority = priority,
        reviewType = reviewType,
        osmSuggestedName = osmSuggestedName,
        osmSuggestedCategory = osmSuggestedCategory,
        osmPlaceType = osmPlaceType,
        userApprovedName = userApprovedName,
        userApprovedCategory = userApprovedCategory,
        reviewedAt = reviewedAt,
        locationCount = locationCount,
        visitCount = visitCount,
        notes = notes,
        confidenceBreakdown = serializeConfidenceBreakdown(confidenceBreakdown)
    )
}

private fun serializeConfidenceBreakdown(breakdown: Map<String, Float>?): String? {
    if (breakdown == null) return null
    return try {
        val jsonObject = JSONObject()
        breakdown.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        jsonObject.toString()
    } catch (e: Exception) {
        null
    }
}

fun List<PlaceReviewEntity>.toDomainModels(): List<PlaceReview> {
    return map { it.toDomainModel() }
}

fun List<PlaceReview>.toEntities(): List<PlaceReviewEntity> {
    return map { it.toEntity() }
}
