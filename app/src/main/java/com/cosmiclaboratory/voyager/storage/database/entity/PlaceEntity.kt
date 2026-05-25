package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "places",
    indices = [
        Index(value = ["geohash"]),
        Index(value = ["s2CellId"]),
        Index(value = ["lifecycleStatus"]),
        Index(value = ["category"])
    ]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val placeId: Long = 0,
    val centroidLat: Double,
    val centroidLng: Double,
    val radiusM: Float = 80f,
    val geohash: String,
    val s2CellId: Long? = null,
    val confidence: Float = 0.5f,
    val lifecycleStatus: String = "CANDIDATE", // CANDIDATE/CONFIRMED/ARCHIVED/MERGED
    val userDisplayName: String? = null,
    val bestProviderName: String? = null,
    val bestProviderSource: String? = null,
    val category: String = "UNKNOWN", // HOME/WORK/GYM/RESTAURANT/SHOPPING/TRANSIT_HUB/CUSTOM/UNKNOWN
    val categoryConfidence: Float = 0.0f,
    val userCategory: String? = null,
    val createdAt: Long,
    val lastVisitedAt: Long? = null,
    val mergedIntoPlaceId: Long? = null,
    val emoji: String? = null,
    // Multi-user scoping (v8) — inert until sync/multi-user ships; default = install id.
    val userId: String = "",
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)

/** Consistent display name with coordinate fallback for ungeocode places. */
fun PlaceEntity.displayName(): String =
    userDisplayName ?: bestProviderName ?: "%.4f, %.4f".format(centroidLat, centroidLng)
