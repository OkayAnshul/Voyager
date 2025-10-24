package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.domain.model.Visit

fun VisitEntity.toDomainModel(): Visit {
    // CRITICAL FIX: Use stored duration from database with comprehensive null safety
    return try {
        // ENHANCED NULL SAFETY: Check each field individually
        val safeId = try { id ?: 0L } catch (e: Exception) { 0L }
        val safePlaceId = try { placeId ?: 0L } catch (e: Exception) { 0L }
        val safeEntryTime = try { 
            entryTime ?: java.time.LocalDateTime.now() 
        } catch (e: Exception) { 
            java.time.LocalDateTime.now() 
        }
        val safeDuration = try { duration ?: 0L } catch (e: Exception) { 0L }
        val safeConfidence = try { confidence ?: 1.0f } catch (e: Exception) { 1.0f }
        
        Visit(
            id = safeId,
            placeId = safePlaceId,
            entryTime = safeEntryTime,
            exitTime = exitTime, // Can be null
            _duration = safeDuration,
            confidence = safeConfidence
        )
    } catch (e: Exception) {
        // CRITICAL FIX: Ultimate fallback for any reflection/serialization errors
        android.util.Log.w("VisitMapper", "CRITICAL: Error mapping VisitEntity to Visit, using safe defaults", e)
        Visit(
            id = 0L,
            placeId = 0L,
            entryTime = java.time.LocalDateTime.now(),
            exitTime = null,
            _duration = 0L,
            confidence = 1.0f
        )
    }
}

fun Visit.toEntity(): VisitEntity {
    return try {
        VisitEntity(
            id = id,
            placeId = placeId,
            entryTime = entryTime,
            exitTime = exitTime,
            duration = duration, // Use the calculated property
            confidence = confidence
        )
    } catch (e: Exception) {
        // CRITICAL FIX: Fallback for reflection/serialization errors
        android.util.Log.w("VisitMapper", "Error mapping Visit to VisitEntity, using safe defaults", e)
        VisitEntity(
            id = 0L,
            placeId = 0L,
            entryTime = java.time.LocalDateTime.now(),
            exitTime = null,
            duration = 0L,
            confidence = 1.0f
        )
    }
}

fun List<VisitEntity>.toDomainModels(): List<Visit> {
    return try {
        mapNotNull { entity ->
            try {
                entity.toDomainModel()
            } catch (e: Exception) {
                android.util.Log.w("VisitMapper", "Skipping invalid VisitEntity during batch conversion", e)
                null // Skip invalid entities
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VisitMapper", "Error during batch VisitEntity conversion, returning empty list", e)
        emptyList()
    }
}

fun List<Visit>.toEntities(): List<VisitEntity> {
    return try {
        mapNotNull { visit ->
            try {
                visit.toEntity()
            } catch (e: Exception) {
                android.util.Log.w("VisitMapper", "Skipping invalid Visit during batch conversion", e)
                null // Skip invalid visits
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VisitMapper", "Error during batch Visit conversion, returning empty list", e)
        emptyList()
    }
}